package dk.familieapp.data

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Instances
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * En kalender, som telefonen synkroniserer (typisk fra Google Kalender).
 * [key] er Google-kalenderens id og er ens på alle telefoner, der har adgang til den.
 */
data class DeviceCalendar(
    val id: Long,
    val key: String,
    val name: String,
    val account: String,
    val color: Int,
    val canWrite: Boolean,
)

data class CalendarEvent(
    val eventId: Long,
    val title: String,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    val location: String,
    val calendarId: Long,
    val color: Int,
    val firstDay: LocalDate,
    val lastDay: LocalDate,
    val description: String = "",
) {
    fun isOn(day: LocalDate): Boolean = !day.isBefore(firstDay) && !day.isAfter(lastDay)
}

object CalendarMath {
    private val clock = DateTimeFormatter.ofPattern("HH.mm")

    /**
     * Første og sidste dag, en aftale ligger på. Heldagsaftaler gemmes som
     * midnat til midnat i UTC, så de skal læses i UTC og ikke i lokal tid.
     */
    fun days(begin: Long, end: Long, allDay: Boolean, zone: ZoneId): Pair<LocalDate, LocalDate> {
        val z = if (allDay) ZoneOffset.UTC else zone
        val first = Instant.ofEpochMilli(begin).atZone(z).toLocalDate()
        val lastMoment = if (end > begin) end - 1 else begin
        val last = Instant.ofEpochMilli(lastMoment).atZone(z).toLocalDate()
        return first to last
    }

    /** "10.00–11.30", "Hele dagen" eller "Fortsat" for dag to af en lang aftale. */
    fun timeLabel(event: CalendarEvent, day: LocalDate, zone: ZoneId): String {
        if (event.allDay) return "Hele dagen"
        val start = Instant.ofEpochMilli(event.begin).atZone(zone)
        val end = Instant.ofEpochMilli(event.end).atZone(zone)
        return when {
            event.firstDay == event.lastDay -> "${clock.format(start)}–${clock.format(end)}"
            day == event.firstDay -> "Fra ${clock.format(start)}"
            day == event.lastDay -> "Til ${clock.format(end)}"
            else -> "Fortsat"
        }
    }

    fun clock(time: LocalTime): String = clock.format(time)
}

/**
 * Læser og skriver i telefonens kalenderlager. Google Kalender synkroniserer
 * selv mellem lageret og Google, så der skal hverken login eller Google Cloud til.
 */
class DeviceCalendarRepo(private val context: Context) {

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun hasReadPermission(): Boolean = granted(Manifest.permission.READ_CALENDAR)
    fun hasWritePermission(): Boolean = granted(Manifest.permission.WRITE_CALENDAR)

    suspend fun calendars(): List<DeviceCalendar> = withContext(Dispatchers.IO) {
        val result = mutableListOf<DeviceCalendar>()
        if (hasReadPermission()) {
            runCatching {
                context.contentResolver.query(
                    Calendars.CONTENT_URI,
                    arrayOf(
                        Calendars._ID,
                        Calendars.NAME,
                        Calendars.OWNER_ACCOUNT,
                        Calendars.CALENDAR_DISPLAY_NAME,
                        Calendars.ACCOUNT_NAME,
                        Calendars.CALENDAR_COLOR,
                        Calendars.CALENDAR_ACCESS_LEVEL,
                        Calendars.VISIBLE,
                    ),
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(1) ?: ""
                        val owner = cursor.getString(2) ?: ""
                        val key = name.ifBlank { owner }
                        if (key.isNotBlank() && cursor.getInt(7) == 1) {
                            result += DeviceCalendar(
                                id = cursor.getLong(0),
                                key = key,
                                name = cursor.getString(3) ?: key,
                                account = cursor.getString(4) ?: "",
                                color = cursor.getInt(5),
                                canWrite = cursor.getInt(6) >= Calendars.CAL_ACCESS_CONTRIBUTOR,
                            )
                        }
                    }
                }
            }
        }
        result.sortedBy { it.name.lowercase() }
    }

    /** Familiekalenderen på denne telefon. Har telefonen den via flere konti, foretrækkes en med skriveadgang. */
    fun findCalendar(key: String, calendars: List<DeviceCalendar>): DeviceCalendar? {
        if (key.isBlank()) return null
        val matches = calendars.filter { it.key == key }
        return matches.firstOrNull { it.canWrite } ?: matches.firstOrNull()
    }

    /** Aftaler i kalenderen [calendarId], der ligger på mindst én dag fra [from] til og med [to]. */
    suspend fun events(from: LocalDate, to: LocalDate, calendarId: Long): List<CalendarEvent> =
        withContext(Dispatchers.IO) {
            val result = mutableListOf<CalendarEvent>()
            if (hasReadPermission()) {
                val zone = ZoneId.systemDefault()
                // En dag ekstra i hver ende, så heldagsaftaler (UTC) ikke falder ud.
                val startMillis = from.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                val endMillis = to.plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli()
                val uri = Instances.CONTENT_URI.buildUpon().also {
                    ContentUris.appendId(it, startMillis)
                    ContentUris.appendId(it, endMillis)
                }.build()
                runCatching {
                    context.contentResolver.query(
                        uri,
                        arrayOf(
                            Instances.EVENT_ID,
                            Instances.TITLE,
                            Instances.BEGIN,
                            Instances.END,
                            Instances.ALL_DAY,
                            Instances.EVENT_LOCATION,
                            Instances.CALENDAR_ID,
                            Instances.DISPLAY_COLOR,
                            Instances.DESCRIPTION,
                        ),
                        null,
                        null,
                        null,
                    )?.use { cursor ->
                        while (cursor.moveToNext()) {
                            if (cursor.getLong(6) != calendarId) continue
                            val begin = cursor.getLong(2)
                            val end = cursor.getLong(3)
                            val allDay = cursor.getInt(4) == 1
                            val (first, last) = CalendarMath.days(begin, end, allDay, zone)
                            if (!last.isBefore(from) && !first.isAfter(to)) {
                                result += CalendarEvent(
                                    eventId = cursor.getLong(0),
                                    title = cursor.getString(1)?.takeIf { it.isNotBlank() } ?: "(Uden titel)",
                                    begin = begin,
                                    end = end,
                                    allDay = allDay,
                                    location = cursor.getString(5) ?: "",
                                    calendarId = calendarId,
                                    color = cursor.getInt(7),
                                    firstDay = first,
                                    lastDay = last,
                                    description = cursor.getString(8) ?: "",
                                )
                            }
                        }
                    }
                }
            }
            result.sortedWith(compareByDescending<CalendarEvent> { it.allDay }.thenBy { it.begin })
        }

    /** Opretter en aftale i [calendarId]. [start] = null betyder heldagsaftale. */
    suspend fun insertEvent(
        calendarId: Long,
        title: String,
        location: String,
        date: LocalDate,
        start: LocalTime?,
        end: LocalTime?,
        description: String = "",
    ): Boolean = withContext(Dispatchers.IO) {
        if (!hasWritePermission()) return@withContext false
        val values = ContentValues().apply {
            put(Events.CALENDAR_ID, calendarId)
            put(Events.TITLE, title)
            put(Events.EVENT_LOCATION, location)
            put(Events.DESCRIPTION, description)
            if (start == null) {
                val begin = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                put(Events.DTSTART, begin)
                put(Events.DTEND, begin + 24 * 60 * 60 * 1000L)
                put(Events.ALL_DAY, 1)
                put(Events.EVENT_TIMEZONE, "UTC")
            } else {
                val zone = ZoneId.systemDefault()
                val begin = date.atTime(start).atZone(zone).toInstant().toEpochMilli()
                var finish = date.atTime(end ?: start.plusHours(1)).atZone(zone).toInstant().toEpochMilli()
                if (finish <= begin) finish = begin + 60 * 60 * 1000L
                put(Events.DTSTART, begin)
                put(Events.DTEND, finish)
                put(Events.ALL_DAY, 0)
                put(Events.EVENT_TIMEZONE, zone.id)
            }
        }
        runCatching { context.contentResolver.insert(Events.CONTENT_URI, values) != null }.getOrDefault(false)
    }

    /** Retter beskrivelsen (fx "Hvem: Ida"). For en gentagen aftale gælder det hele serien. */
    suspend fun updateDescription(eventId: Long, description: String): Boolean = withContext(Dispatchers.IO) {
        if (!hasWritePermission()) return@withContext false
        val values = ContentValues().apply { put(Events.DESCRIPTION, description) }
        runCatching {
            context.contentResolver.update(ContentUris.withAppendedId(Events.CONTENT_URI, eventId), values, null, null) > 0
        }.getOrDefault(false)
    }
}
