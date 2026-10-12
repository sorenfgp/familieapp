package dk.familieapp

import dk.familieapp.data.RRules
import dk.familieapp.data.Repeat
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

class RRulesTest {
    private val copenhagen = ZoneId.of("Europe/Copenhagen")

    @Test
    fun weeklyOnTuesday() {
        assertEquals(
            "FREQ=WEEKLY;WKST=MO;BYDAY=TU",
            RRules.weekly(Repeat(setOf(DayOfWeek.TUESDAY)), allDay = false, zone = copenhagen),
        )
    }

    @Test
    fun everySecondWeekUntilDateInLocalTime() {
        val repeat = Repeat(setOf(DayOfWeek.THURSDAY, DayOfWeek.TUESDAY), 2, LocalDate.of(2026, 12, 20))
        // 20. december kl. 23.59.59 dansk vintertid = 22.59.59 UTC.
        assertEquals(
            "FREQ=WEEKLY;WKST=MO;INTERVAL=2;BYDAY=TU,TH;UNTIL=20261220T225959Z",
            RRules.weekly(repeat, allDay = false, zone = copenhagen),
        )
        assertEquals(
            "FREQ=WEEKLY;WKST=MO;INTERVAL=2;BYDAY=TU,TH;UNTIL=20261220",
            RRules.weekly(repeat, allDay = true, zone = copenhagen),
        )
    }

    @Test
    fun firstDateIsNextMatchingWeekday() {
        val monday = LocalDate.of(2026, 10, 12)
        assertEquals(LocalDate.of(2026, 10, 13), RRules.firstDate(monday, setOf(DayOfWeek.TUESDAY)))
        assertEquals(monday, RRules.firstDate(monday, setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)))
    }

    @Test
    fun durations() {
        assertEquals("PT120M", RRules.duration(120, allDay = false))
        assertEquals("P1D", RRules.duration(1440, allDay = true))
        assertEquals("P3D", RRules.duration(3 * 1440, allDay = true))
    }
}
