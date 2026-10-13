package dk.familieapp

import dk.familieapp.data.Rejseplanen
import dk.familieapp.data.WeatherService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class HomeInfoTest {
    @Test
    fun readsOpenMeteoResponse() {
        val text = """
            {"current":{"time":"2026-10-13T07:30","temperature_2m":9.6,"weather_code":61,"wind_speed_10m":4.2},
             "daily":{"time":["2026-10-13"],"temperature_2m_max":[13.1],"temperature_2m_min":[7.4],
                      "precipitation_sum":[2.3],"precipitation_probability_max":[80]}}
        """.trimIndent()
        val weather = WeatherService.parse(text)
        assertEquals(9.6, weather.temperature, 0.01)
        assertEquals(61, weather.code)
        assertEquals(80, weather.rainChance)
        assertEquals("Regn", WeatherService.describe(weather.code))
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
