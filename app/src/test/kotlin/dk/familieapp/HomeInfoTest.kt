package dk.familieapp

import dk.familieapp.data.Rejseplanen
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

    @Test
    fun readsLineADeparturesWithDelayAndCancellation() {
        val text = """
            {"Departure":[
              {"name":"A","type":"S","time":"07:41:00","date":"2026-10-13","rtTime":"07:45:00","rtDate":"2026-10-13",
               "direction":"Hundige St.","track":"1","ProductAtStop":{"displayNumber":"A","catOut":"S"}},
              {"name":"B","type":"S","time":"07:43:00","date":"2026-10-13","direction":"Høje Taastrup St.",
               "ProductAtStop":{"displayNumber":"B"}},
              {"name":"A","type":"S","time":"07:51:00","date":"2026-10-13","direction":"Hundige St.",
               "cancelled":true,"ProductAtStop":{"displayNumber":"A"}}
            ]}
        """.trimIndent()
        val departures = Rejseplanen.parseDepartures(text, line = "A")
        assertEquals(2, departures.size)
        assertEquals(LocalDateTime.of(2026, 10, 13, 7, 41), departures[0].planned)
        assertEquals(4, departures[0].delayMinutes)
        assertTrue(departures[1].cancelled)
    }

    @Test
    fun findsStopIdInListOrSingleObject() {
        assertEquals(
            "8600719",
            Rejseplanen.parseStopId("""{"stopLocationOrCoordLocation":[{"StopLocation":{"name":"Favrholm St.","extId":"8600719"}}]}"""),
        )
        assertEquals("123", Rejseplanen.parseStopId("""{"StopLocation":{"id":"123"}}"""))
    }
}
