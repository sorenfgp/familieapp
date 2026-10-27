package dk.familieapp

import dk.familieapp.data.MitTog
import dk.familieapp.data.WeatherService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class HomeInfoTest {
    @Test
    fun splitsDayAndFindsRain() {
        val text = """
            {"current":{"temperature_2m":9.6,"weather_code":3},
             "hourly":{"time":["2026-10-13T00:00","2026-10-13T01:00","2026-10-13T02:00","2026-10-13T03:00","2026-10-13T04:00","2026-10-13T05:00","2026-10-13T06:00","2026-10-13T07:00","2026-10-13T08:00","2026-10-13T09:00","2026-10-13T10:00","2026-10-13T11:00","2026-10-13T12:00","2026-10-13T13:00","2026-10-13T14:00","2026-10-13T15:00","2026-10-13T16:00","2026-10-13T17:00","2026-10-13T18:00","2026-10-13T19:00","2026-10-13T20:00","2026-10-13T21:00","2026-10-13T22:00","2026-10-13T23:00"],
                       "temperature_2m":[8.0,8.0,8.0,8.0,8.0,8.0,8.0,8.0,8.0,8.0,8.0,8.0,12.0,12.0,12.0,12.0,12.0,12.0,10.0,10.0,10.0,10.0,10.0,10.0],
                       "precipitation":[0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,1.0,1.5,0.5,0.0,0.0,0.0,0.4,0.0,0.0,0.0],
                       "weather_code":[3,3,3,3,3,3,3,3,3,3,3,3,3,3,61,63,61,3,3,3,61,3,3,3]}}
        """.trimIndent()
        val weather = WeatherService.parse(text, nowHour = 7)
        assertEquals(listOf("Morgen", "Middag", "Aften"), weather.parts.map { it.label })
        assertEquals(12.0, weather.parts[1].temperature, 0.01)
        assertEquals("Regn", WeatherService.describe(weather.parts[1].code))
        val rain = weather.rain!!
        assertEquals(14, rain.startHour)
        assertEquals(17, rain.endHour)
        assertEquals(3.0, rain.mm, 0.01)
        assertTrue(rain.moreLater)
        // Efter regnen er der ingen regn i første periode.
        assertEquals(20, WeatherService.parse(text, nowHour = 18).rain!!.startHour)
    }

    private fun train(
        line: String,
        planned: String,
        expected: String = planned,
        destination: String,
        stops: List<String>,
        extra: String = "",
    ): String {
        val stations = stops.joinToString(",") { "{\"StationId\":\"$it\"}" }
        return """
        {"TrainId":"1","Product":"STRAIN","LineName":"$line",
         "ScheduleTimeDeparture":"13-10-2026 $planned","EstimatedTimeDeparture":"13-10-2026 $expected",
         "TrackCurrent":"2","TrainDeparted":"01-01-0001 00:00:00",$extra
         "Routes":[{"DestinationStationId":"$destination",
           "Stations":[$stations]}]}
        """.trimIndent()
    }

    @Test
    fun readsLineAFromMittogBoard() {
        val text = """
            {"data":{"StationId":"FVT","Trains":[
              ${train("A", "07:41:00", "07:45:30", "KJ", listOf("LI", "KN", "KH"))},
              ${train("B", "07:43:00", destination = "HTÅ", stops = listOf("KN"))},
              ${train("A", "07:51:00", destination = "KJ", stops = listOf("LI", "KN"), extra = "\"IsCancelled\":true,")},
              ${train("A", "07:47:00", destination = "HI", stops = emptyList())},
              ${train("A", "07:20:00", destination = "KJ", stops = listOf("KN"))}
            ]},"pico":[{"header":"Sporarbejde mellem Holte og Lyngby","station_code":null},
                       {"header":"Gælder en anden station","station_code":"KH"}]}
        """.trimIndent()
        val board = MitTog.parseBoard(text, "FVT", now = LocalDateTime.of(2026, 10, 13, 7, 40))

        // Linje B og toget, der allerede er kørt kl. 7.20, er væk.
        assertEquals(3, board.departures.size)
        val first = board.departures[0]
        assertEquals(LocalDateTime.of(2026, 10, 13, 7, 41), first.planned)
        assertEquals(4, first.delayMinutes)
        assertEquals("Køge", first.direction)
        assertEquals("2", first.track)
        assertTrue("KN" in first.stops)
        assertTrue(board.departures.single { it.planned.minute == 51 }.cancelled)
        assertEquals(listOf("Sporarbejde mellem Holte og Lyngby"), board.notices)
        // Toget mod Hillerød regnes ikke med mod byen.
        assertEquals(listOf(41, 51), board.departures.filter { MitTog.HILLEROD !in it.stops }.map { it.planned.minute })
    }

    @Test
    fun skipsTrainsEndingHere() {
        val text = """{"data":{"Trains":[${train("A", "08:00:00", destination = "HI", stops = emptyList())}]}}"""
        assertTrue(MitTog.parseBoard(text, "HI", now = LocalDateTime.of(2026, 10, 13, 7, 0)).departures.isEmpty())
    }
}
