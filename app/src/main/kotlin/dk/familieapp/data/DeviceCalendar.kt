package dk.familieapp.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Instances
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** En kalender, som telefonen synkroniserer (typisk fra Google Kalender). */
data class DeviceCalendar(val id: Long, val name: String, val account: String, val color: Int)

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
}

/**
 * Læser kalenderne på telefonen via Androids kalenderlager. Google Kalender
 * synkroniserer selv hertil, så der skal hverken login eller Google Cloud til.
 */
class DeviceCalendarRepo(private val context: Context) {
    private val prefs = context.getSharedPreferences("calendar", Context.MODE_PRIVATE)

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    /** Kalendere, som er valgt fra på denne telefon. */
    var hiddenCalendars: Set<Long>
        get() = prefs.getStringSet("hidden", emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet()
        set(value) {
            prefs.edit().putStringSet("hidden", value.map { it.toString() }.toSet()).apply()
        }

    suspend fun calendars(): List<DeviceCalendar> = withContext(Dispatchers.IO) {
        val result = mutableListOf<DeviceCalendar>()
        if (hasPermission()) {
            runCatching {
                context.contentResolver.query(
                    Calendars.CONTENT_URI,
                    arrayOf(
                        Calendars._ID,
                        Calendars.CALENDAR_DISPLAY_NAME,
                        Calendars.ACCOUNT_NAME,
                        Calendars.CALENDAR_COLOR,
                        Calendars.VISIBLE,
                    ),
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        if (cursor.getInt(4) == 1) {
                            result += DeviceCalendar(
                                id = cursor.getLong(0),
                                name = cursor.getString(1) ?: "",
                                account = cursor.getString(2) ?: "",
                                color = cursor.getInt(3),
                            )
                        }
                    }
                }
            }
        }
        result.sortedBy { it.name.lowercase() }
    }

    /** Aftaler, der ligger på mindst én dag fra [from] til og med [to]. */
    suspend fun events(from: LocalDate, to: LocalDate): List<CalendarEvent> = withContext(Dispatchers.IO) {
        val result = mutableListOf<CalendarEvent>()
        if (hasPermission()) {
            val zone = ZoneId.systemDefault()
            val shown = calendars().map { it.id }.toSet() - hiddenCalendars
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
                    ),
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val begin = cursor.getLong(2)
                        val end = cursor.getLong(3)
                        val allDay = cursor.getInt(4) == 1
                        val calendarId = cursor.getLong(6)
                        val (first, last) = CalendarMath.days(begin, end, allDay, zone)
                        if (calendarId in shown && !last.isBefore(from) && !first.isAfter(to)) {
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
                            )
                        }
                    }
                }
            }
        }
        result.sortedWith(compareByDescending<CalendarEvent> { it.allDay }.thenBy { it.begin })
    }
}
