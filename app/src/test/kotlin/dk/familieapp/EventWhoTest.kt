package dk.familieapp

import dk.familieapp.data.EventWho
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EventWhoTest {
    @Test
    fun readsWhoLine() {
        assertEquals(listOf("Ida", "Noah"), EventWho.parse("Husk sko\nHvem: Ida, Noah"))
        assertTrue(EventWho.isAll(EventWho.parse("hvem: alle")))
        assertNull(EventWho.parse("Husk sko"))
        assertNull(EventWho.parse(""))
    }

    @Test
    fun replacesWhoLineAndKeepsNotes() {
        assertEquals("Husk sko\nHvem: Alle", EventWho.apply("Husk sko\nHvem: Ida", listOf("Alle")))
        assertEquals("Hvem: Ida", EventWho.apply("", listOf("Ida")))
        assertEquals("Husk sko", EventWho.apply("Husk sko\nHvem: Ida", emptyList()))
        assertEquals("Husk sko", EventWho.strip("Husk sko\nHvem: Ida"))
    }
}
