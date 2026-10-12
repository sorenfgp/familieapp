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

    /** Opdaterer kopien i baggrunden hver 3. time, så den er frisk, selv om appen ikke åbnes. */
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<CalendarSyncWorker>(3, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
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
