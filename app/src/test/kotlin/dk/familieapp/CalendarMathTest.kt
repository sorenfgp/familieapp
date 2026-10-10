package dk.familieapp

import dk.familieapp.data.CalendarEvent
import dk.familieapp.data.CalendarMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

class CalendarMathTest {
    private val copenhagen = ZoneId.of("Europe/Copenhagen")

    private fun millis(year: Int, month: Int, day: Int, hour: Int, minute: Int, zone: ZoneId): Long =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

    private fun event(begin: Long, end: Long, allDay: Boolean): CalendarEvent {
        val (first, last) = CalendarMath.days(begin, end, allDay, copenhagen)
        return CalendarEvent(1, "Test", begin, end, allDay, "", 1, 0, first, last)
    }

    @Test
    fun allDayEventStaysOnItsOwnDay() {
        // Heldagsaftale 10. oktober: midnat til midnat i UTC.
        val e = event(
            millis(2026, 10, 10, 0, 0, ZoneOffset.UTC),
            millis(2026, 10, 11, 0, 0, ZoneOffset.UTC),
            allDay = true,
        )
        assertEquals(LocalDate.of(2026, 10, 10), e.firstDay)
        assertEquals(LocalDate.of(2026, 10, 10), e.lastDay)
        assertFalse(e.isOn(LocalDate.of(2026, 10, 9)))
        assertTrue(e.isOn(LocalDate.of(2026, 10, 10)))
        assertFalse(e.isOn(LocalDate.of(2026, 10, 11)))
        assertEquals("Hele dagen", CalendarMath.timeLabel(e, LocalDate.of(2026, 10, 10), copenhagen))
    }

    @Test
    fun timedEventUsesLocalTime() {
        val e = event(
            millis(2026, 10, 10, 10, 0, copenhagen),
            millis(2026, 10, 10, 11, 30, copenhagen),
            allDay = false,
        )
        assertEquals(LocalDate.of(2026, 10, 10), e.firstDay)
        assertEquals(LocalDate.of(2026, 10, 10), e.lastDay)
        assertEquals("10.00–11.30", CalendarMath.timeLabel(e, LocalDate.of(2026, 10, 10), copenhagen))
    }

    @Test
    fun eventPastMidnightCoversBothDays() {
        val e = event(
            millis(2026, 10, 10, 23, 0, copenhagen),
            millis(2026, 10, 11, 1, 0, copenhagen),
            allDay = false,
        )
        assertTrue(e.isOn(LocalDate.of(2026, 10, 10)))
        assertTrue(e.isOn(LocalDate.of(2026, 10, 11)))
        assertEquals("Fra 23.00", CalendarMath.timeLabel(e, LocalDate.of(2026, 10, 10), copenhagen))
        assertEquals("Til 01.00", CalendarMath.timeLabel(e, LocalDate.of(2026, 10, 11), copenhagen))
    }

    @Test
    fun eventEndingAtMidnightDoesNotSpillOver() {
        val e = event(
            millis(2026, 10, 10, 20, 0, copenhagen),
            millis(2026, 10, 11, 0, 0, copenhagen),
            allDay = false,
        )
        assertEquals(LocalDate.of(2026, 10, 10), e.lastDay)
    }
}
