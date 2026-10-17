package dk.familieapp.data

import android.app.Application
import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Kopierer familiekalenderen fra en telefon, der har den i Google Kalender, til
 * familiens fælles data. Børnenes enheder læser kopien og behøver ingen Google-konto.
 */
object CalendarMirror {
    private const val PAST_DAYS = 7L
    private const val FUTURE_DAYS = 90L
    private const val WORK_NAME = "familiekalender-kopi"

    /** Returnerer true, hvis denne telefon har familiekalenderen og har opdateret kopien. */
    suspend fun sync(context: Context, store: FamilyStore, key: String): Boolean {
        if (key.isBlank()) return false
        val repo = DeviceCalendarRepo(context)
        if (!repo.hasReadPermission()) return false
        val calendar = repo.findCalendar(key, repo.calendars()) ?: return false
        val today = LocalDate.now()
        if (calendar.canWrite && repo.hasWritePermission()) applyRequests(store, repo, calendar.id)
        val events = repo.events(today.minusDays(PAST_DAYS), today.plusDays(FUTURE_DAYS), calendar.id)
        val copies = events
            .map {
                MirroredEvent(
                    id = "${it.eventId}_${it.begin}",
                    eventId = it.eventId,
                    title = it.title,
                    begin = it.begin,
                    end = it.end,
                    allDay = it.allDay,
                    location = it.location,
                    color = it.color,
                    description = it.description,
                    recurring = it.recurring,
                )
            }
            .distinctBy { it.id }
        store.replaceAll(Cols.calendarEvents, copies)
        store.save(
            Cols.calendarSync,
            listOf(
                CalendarSyncInfo(
                    id = "main",
                    calendarKey = key,
                    calendarName = calendar.name,
                    syncedAt = System.currentTimeMillis(),
                )
            ),
        )
        return true
    }

    /** Opdaterer kopien og udfører børnenes ændringer i baggrunden hvert kvarter. */
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<CalendarSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Udfører ventende ændringer fra enheder uden adgang til kalenderen. Mislykkes en, prøves den op til 3 gange. */
    private suspend fun applyRequests(store: FamilyStore, repo: DeviceCalendarRepo, calendarId: Long) {
        val requests = store.observe(Cols.calendarRequests).first().sortedBy { it.createdAt }
        for (r in requests) {
            val ok = runCatching { apply(r, repo, calendarId) }.getOrDefault(false)
            if (ok || r.attempts >= 2) {
                store.delete(Cols.calendarRequests, listOf(r.id))
            } else {
                store.save(Cols.calendarRequests, listOf(r.copy(attempts = r.attempts + 1)))
            }
        }
    }

    private fun minutes(m: Int): LocalTime? = if (m < 0) null else LocalTime.of(m / 60, m % 60)

    private suspend fun apply(r: CalendarRequest, repo: DeviceCalendarRepo, calendarId: Long): Boolean {
        val zone = ZoneId.systemDefault()
        val event = requestEvent(r, zone)
        return when (r.op) {
            "create" -> repo.insertEvent(
                calendarId = calendarId,
                title = r.title,
                location = r.location,
                date = LocalDate.parse(r.date),
                start = minutes(r.startMinutes),
                end = minutes(r.endMinutes),
                description = EventWho.apply("", r.who),
                repeat = r.repeatWeekdays.takeIf { it.isNotEmpty() }?.let { days ->
                    Repeat(
                        weekdays = days.map { java.time.DayOfWeek.of(it) }.toSet(),
                        intervalWeeks = r.repeatInterval,
                        until = r.repeatUntil.takeIf { it.isNotBlank() }?.let { LocalDate.parse(it) },
                    )
                },
                endDate = r.endDate.takeIf { it.isNotBlank() }?.let { LocalDate.parse(it) },
            )
            "who" -> repo.updateDescription(r.eventId, EventWho.apply(r.description, r.who))
            "move" -> repo.moveEvent(event, LocalDate.parse(r.newDate))
            "cancel" -> repo.cancelOccurrence(r.eventId, r.begin)
            "cancelRange" -> repo.cancelOccurrences(r.eventId, LocalDate.parse(r.from), LocalDate.parse(r.to)) > 0
            "delete" -> repo.deleteEvent(r.eventId)
            else -> true
        }
    }

    private fun requestEvent(r: CalendarRequest, zone: ZoneId): CalendarEvent {
        val (first, last) = CalendarMath.days(r.begin, r.end, r.allDay, zone)
        return CalendarEvent(
            eventId = r.eventId, title = r.title, begin = r.begin, end = r.end, allDay = r.allDay,
            location = "", calendarId = 0, color = 0, firstDay = first, lastDay = last,
            description = r.description, recurring = r.recurring,
        )
    }

    /** Ventende nye aftaler, der skal vises på [day], indtil de er gemt i kalenderen. */
    fun pendingOn(requests: List<CalendarRequest>, day: LocalDate): List<CalendarRequest> =
        requests.filter { r ->
            if (r.op != "create" || r.date.isBlank()) return@filter false
            val start = LocalDate.parse(r.date)
            if (day.isBefore(start)) return@filter false
            if (r.repeatWeekdays.isNotEmpty()) {
                val until = r.repeatUntil.takeIf { it.isNotBlank() }?.let { LocalDate.parse(it) }
                day.dayOfWeek.value in r.repeatWeekdays && (until == null || !day.isAfter(until))
            } else {
                val end = r.endDate.takeIf { it.isNotBlank() }?.let { LocalDate.parse(it) } ?: start
                !day.isAfter(end)
            }
        }

    fun toEvent(copy: MirroredEvent, zone: ZoneId): CalendarEvent {
        val (first, last) = CalendarMath.days(copy.begin, copy.end, copy.allDay, zone)
        return CalendarEvent(
            eventId = copy.eventId,
            title = copy.title,
            begin = copy.begin,
            end = copy.end,
            allDay = copy.allDay,
            location = copy.location,
            calendarId = 0,
            color = copy.color,
            firstDay = first,
            lastDay = last,
            description = copy.description,
            recurring = copy.recurring,
        )
    }

    /** Aftaler fra kopien, der ligger på mindst én dag fra [from] til og med [to]. */
    fun eventsBetween(copies: List<MirroredEvent>, from: LocalDate, to: LocalDate, zone: ZoneId): List<CalendarEvent> =
        copies.map { toEvent(it, zone) }
            .filter { !it.lastDay.isBefore(from) && !it.firstDay.isAfter(to) }
            .sortedWith(compareByDescending<CalendarEvent> { it.allDay }.thenBy { it.begin })
}

/** Baggrundsjob på forældrenes telefoner, der holder kopien af familiekalenderen opdateret. */
class CalendarSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as Application
        val session = Session(app)
        val household = session.householdId
        if (!session.firebaseEnabled || household == null) return Result.success()
        return runCatching {
            val store = FirestoreStore(household)
            val key = store.observe(Cols.settings).first()
                .firstOrNull { it.id == "main" }?.calendarKey.orEmpty()
            CalendarMirror.sync(app, store, key)
            Result.success()
        }.getOrElse { Result.retry() }
    }
}
