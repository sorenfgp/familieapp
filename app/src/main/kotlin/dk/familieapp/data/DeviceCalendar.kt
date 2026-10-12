package dk.familieapp.data

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
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
import java.time.DayOfWeek
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
    /** Del af en fast (gentaget) aftale. */
    val recurring: Boolean = false,
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
                            Instances.RRULE,
                            Instances.STATUS,
                        ),
                        null,
                        null,
                        null,
                    )?.use { cursor ->
                        while (cursor.moveToNext()) {
                            if (cursor.getLong(6) != calendarId) continue
                            if (cursor.getInt(10) == Events.STATUS_CANCELED) continue
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
                                    recurring = !cursor.getString(9).isNullOrBlank(),
                                )
                            }
                        }
                    }
                }
            }
            result.sortedWith(compareByDescending<CalendarEvent> { it.allDay }.thenBy { it.begin })
        }

    /**
     * Opretter en aftale i [calendarId]. [start] = null betyder heldagsaftale.
     * [endDate] gør aftalen flerdags (fx en weekendtur fredag til søndag).
     * Med [repeat] bliver det en fast aftale, der starter på første valgte ugedag fra [date].
     */
    suspend fun insertEvent(
        calendarId: Long,
        title: String,
        location: String,
        date: LocalDate,
        start: LocalTime?,
        end: LocalTime?,
        description: String = "",
        repeat: Repeat? = null,
        endDate: LocalDate? = null,
    ): Boolean = withContext(Dispatchers.IO) {
        if (!hasWritePermission()) return@withContext false
        val zone = ZoneId.systemDefault()
        val recurring = repeat != null && repeat.weekdays.isNotEmpty()
        val firstDate = if (recurring) RRules.firstDate(date, repeat!!.weekdays) else date
        // Antal dage, aftalen strækker sig over ud over den første.
        val extraDays = maxOf(0L, java.time.temporal.ChronoUnit.DAYS.between(date, endDate ?: date))

        val begin: Long
        val minutes: Long
        if (start == null) {
            begin = firstDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            minutes = (extraDays + 1) * 24 * 60
        } else {
            begin = firstDate.atTime(start).atZone(zone).toInstant().toEpochMilli()
            val finish = firstDate.plusDays(extraDays).atTime(end ?: start.plusHours(1))
                .atZone(zone).toInstant().toEpochMilli()
            val length = (finish - begin) / 60_000
            minutes = if (length > 0) length else 60L
        }

        val values = ContentValues().apply {
            put(Events.CALENDAR_ID, calendarId)
            put(Events.TITLE, title)
            put(Events.EVENT_LOCATION, location)
            put(Events.DESCRIPTION, description)
            put(Events.DTSTART, begin)
            if (start == null) {
                put(Events.ALL_DAY, 1)
                put(Events.EVENT_TIMEZONE, "UTC")
            } else {
                put(Events.ALL_DAY, 0)
                put(Events.EVENT_TIMEZONE, zone.id)
            }
            if (recurring) {
                // Faste aftaler har en varighed i stedet for et sluttidspunkt.
                put(Events.DURATION, RRules.duration(minutes, start == null))
                put(Events.RRULE, RRules.weekly(repeat!!, start == null, zone))
            } else {
                put(Events.DTEND, begin + minutes * 60_000)
            }
        }
        runCatching { context.contentResolver.insert(Events.CONTENT_URI, values) != null }.getOrDefault(false)
    }

    /** Sletter en aftale. For en fast aftale slettes hele serien. */
    suspend fun deleteEvent(eventId: Long): Boolean = withContext(Dispatchers.IO) {
        if (!hasWritePermission()) return@withContext false
        runCatching {
            context.contentResolver.delete(ContentUris.withAppendedId(Events.CONTENT_URI, eventId), null, null) > 0
        }.getOrDefault(false)
    }

    /** Aflyser én gang af en fast aftale. [instanceBegin] er starttidspunktet for netop den gang. */
    suspend fun cancelOccurrence(eventId: Long, instanceBegin: Long): Boolean = withContext(Dispatchers.IO) {
        if (!hasWritePermission()) return@withContext false
        val values = ContentValues().apply {
            put(Events.ORIGINAL_INSTANCE_TIME, instanceBegin)
            put(Events.STATUS, Events.STATUS_CANCELED)
        }
        runCatching {
            context.contentResolver.insert(
                Uri.withAppendedPath(Events.CONTENT_EXCEPTION_URI, eventId.toString()),
                values,
            ) != null
        }.getOrDefault(false)
    }

    /** Aflyser alle gange af en fast aftale fra [from] til og med [to], fx i en ferie. Returnerer antallet. */
    suspend fun cancelOccurrences(eventId: Long, from: LocalDate, to: LocalDate): Int {
        val calendarEvents = instancesOf(eventId, from, to)
        var cancelled = 0
        calendarEvents.forEach { if (cancelOccurrence(eventId, it)) cancelled++ }
        return cancelled
    }

    private suspend fun instancesOf(eventId: Long, from: LocalDate, to: LocalDate): List<Long> =
        withContext(Dispatchers.IO) {
            val result = mutableListOf<Long>()
            val zone = ZoneId.systemDefault()
            val uri = Instances.CONTENT_URI.buildUpon().also {
                ContentUris.appendId(it, from.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
                ContentUris.appendId(it, to.plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli())
            }.build()
            runCatching {
                context.contentResolver.query(
                    uri,
                    arrayOf(Instances.EVENT_ID, Instances.BEGIN, Instances.END, Instances.ALL_DAY, Instances.STATUS),
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        if (cursor.getLong(0) != eventId || cursor.getInt(4) == Events.STATUS_CANCELED) continue
                        val begin = cursor.getLong(1)
                        val (first, _) = CalendarMath.days(begin, cursor.getLong(2), cursor.getInt(3) == 1, zone)
                        if (!first.isBefore(from) && !first.isAfter(to)) result += begin
                    }
                }
            }
            result
        }

    /**
     * Flytter en aftale til [newDate] og beholder klokkeslættet.
     * For en fast aftale flyttes kun denne ene gang.
     */
    suspend fun moveEvent(event: CalendarEvent, newDate: LocalDate): Boolean = withContext(Dispatchers.IO) {
        if (!hasWritePermission()) return@withContext false
        val days = java.time.temporal.ChronoUnit.DAYS.between(event.firstDay, newDate)
        if (days == 0L) return@withContext true
        val zone: ZoneId = if (event.allDay) ZoneOffset.UTC else ZoneId.systemDefault()
        val newBegin = Instant.ofEpochMilli(event.begin).atZone(zone).plusDays(days).toInstant().toEpochMilli()
        val newEnd = Instant.ofEpochMilli(event.end).atZone(zone).plusDays(days).toInstant().toEpochMilli()
        val values = ContentValues().apply {
            put(Events.DTSTART, newBegin)
            put(Events.DTEND, newEnd)
        }
        runCatching {
            if (event.recurring) {
                values.put(Events.ORIGINAL_INSTANCE_TIME, event.begin)
                values.put(Events.STATUS, Events.STATUS_CONFIRMED)
                context.contentResolver.insert(
                    Uri.withAppendedPath(Events.CONTENT_EXCEPTION_URI, event.eventId.toString()),
                    values,
                ) != null
            } else {
                context.contentResolver.update(
                    ContentUris.withAppendedId(Events.CONTENT_URI, event.eventId),
                    values,
                    null,
                    null,
                ) > 0
            }
        }.getOrDefault(false)
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

/** En fast aftale: hver [intervalWeeks]. uge på [weekdays], evt. til og med [until]. */
data class Repeat(
    val weekdays: Set<DayOfWeek>,
    val intervalWeeks: Int = 1,
    val until: LocalDate? = null,
)

/** Gentagelsesregler i det format, Google Kalender bruger (RFC 5545). */
object RRules {
    private val codes = mapOf(
        DayOfWeek.MONDAY to "MO",
        DayOfWeek.TUESDAY to "TU",
        DayOfWeek.WEDNESDAY to "WE",
        DayOfWeek.THURSDAY to "TH",
        DayOfWeek.FRIDAY to "FR",
        DayOfWeek.SATURDAY to "SA",
        DayOfWeek.SUNDAY to "SU",
    )
    private val utcStamp = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")

    fun weekly(repeat: Repeat, allDay: Boolean, zone: ZoneId): String {
        val parts = mutableListOf("FREQ=WEEKLY", "WKST=MO")
        if (repeat.intervalWeeks > 1) parts += "INTERVAL=${repeat.intervalWeeks}"
        parts += "BYDAY=" + repeat.weekdays.sortedBy { it.value }.joinToString(",") { codes.getValue(it) }
        repeat.until?.let { until ->
            parts += if (allDay) {
                "UNTIL=" + until.format(DateTimeFormatter.BASIC_ISO_DATE)
            } else {
                val lastMoment = until.plusDays(1).atStartOfDay(zone).minusSeconds(1).withZoneSameInstant(ZoneOffset.UTC)
                "UNTIL=" + lastMoment.format(utcStamp)
            }
        }
        return parts.joinToString(";")
    }

    /** Første dag fra og med [from], der er en af [weekdays]. */
    fun firstDate(from: LocalDate, weekdays: Set<DayOfWeek>): LocalDate =
        (0L..6L).map { from.plusDays(it) }.firstOrNull { it.dayOfWeek in weekdays } ?: from

    fun duration(minutes: Long, allDay: Boolean): String =
        if (allDay) "P${maxOf(1L, minutes / (24 * 60))}D" else "PT${minutes}M"
}
