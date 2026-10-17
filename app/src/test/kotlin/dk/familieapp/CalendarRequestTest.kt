package dk.familieapp

import dk.familieapp.data.CalendarMirror
import dk.familieapp.data.CalendarRequest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class CalendarRequestTest {
    @Test
    fun pendingCreatesShowOnTheirDays() {
        val trip = CalendarRequest(id = "a", op = "create", title = "Tur", date = "2026-10-16", endDate = "2026-10-18")
        val football = CalendarRequest(id = "b", op = "create", title = "Fodbold", date = "2026-10-13", repeatWeekdays = listOf(2))
        val delete = CalendarRequest(id = "c", op = "delete", eventId = 7)
        val all = listOf(trip, football, delete)
        assertEquals(listOf("a"), CalendarMirror.pendingOn(all, LocalDate.of(2026, 10, 17)).map { it.id })
        assertEquals(listOf("b"), CalendarMirror.pendingOn(all, LocalDate.of(2026, 10, 20)).map { it.id })
        assertEquals(emptyList<String>(), CalendarMirror.pendingOn(all, LocalDate.of(2026, 10, 19)).map { it.id })
    }
}
