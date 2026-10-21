package dk.familieapp

import dk.familieapp.data.Football
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class FootballTest {
    @Test
    fun readsUpcomingMatches() {
        val text = """
            {"events":[
              {"date":"2026-10-04T18:45Z","league":{"name":"UEFA Nations League"},
               "competitions":[{"competitors":[
                 {"homeAway":"home","team":{"displayName":"Wales"}},
                 {"homeAway":"away","team":{"displayName":"Denmark"}}]}]},
              {"date":"2026-11-17T19:45Z","league":{"name":"UEFA Nations League"},
               "competitions":[{"venue":{"address":{"city":"Copenhagen"}},"competitors":[
                 {"homeAway":"home","team":{"displayName":"Denmark"}},
                 {"homeAway":"away","team":{"displayName":"Norway"}}]}]},
              {"date":"2026-11-14T19:45Z","league":{"name":"UEFA Nations League"},
               "competitions":[{"competitors":[
                 {"homeAway":"home","team":{"displayName":"Portugal"}},
                 {"homeAway":"away","team":{"displayName":"Denmark"}}]}]}
            ]}
        """.trimIndent()
        val matches = Football.parse(text, Instant.parse("2026-10-21T18:00:00Z"))
        assertEquals(listOf("Portugal", "Norge"), matches.map { it.opponent })
        assertFalse(matches[0].home)
        assertTrue(matches[1].home)
        // 20.45 dansk vintertid
        assertEquals(20, matches[0].kickoff.hour)
    }
}
