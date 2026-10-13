package dk.familieapp.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Kortene på forsiden. Hver profil vælger selv, hvilke der vises. */
object HomeCards {
    const val WEATHER = "weather"
    const val TRAIN = "train"
    const val DINNER = "dinner"
    const val CHORES = "chores"
    const val SHOPPING = "shopping"
    const val CALENDAR = "calendar"

    /** I den rækkefølge, de vises på forsiden. */
    val all = listOf(
        WEATHER to "Vejret i Hillerød",
        TRAIN to "S-tog linje A (Favrholm–Nørreport)",
        DINNER to "Aftensmad i dag",
        CHORES to "Pligter",
        SHOPPING to "Indkøbsliste",
        CALENDAR to "I dag i kalenderen",
    )

    /** Toget er fra, indtil profilen selv slår det til. */
    val default = listOf(WEATHER, DINNER, CHORES, SHOPPING, CALENDAR)
}

internal fun httpGet(url: String): String {
    val connection = URL(url).openConnection() as HttpURLConnection
    return try {
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        connection.setRequestProperty("User-Agent", "Familien-app")
        if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
        connection.inputStream.bufferedReader().use { it.readText() }
    } finally {
        connection.disconnect()
    }
}

private val json = Json { ignoreUnknownKeys = true; isLenient = true }

// ---------- Vejr (Open-Meteo, kræver ingen nøgle) ----------

data class Weather(
    val temperature: Double,
    val code: Int,
    val max: Double,
    val min: Double,
    val rainChance: Int,
    val rainMm: Double,
    val wind: Double,
)

object WeatherService {
    private const val HILLEROD =
        "https://api.open-meteo.com/v1/forecast?latitude=55.9267&longitude=12.3109" +
            "&current=temperature_2m,weather_code,wind_speed_10m" +
            "&daily=temperature_2m_max,temperature_2m_min,precipitation_sum,precipitation_probability_max" +
            "&wind_speed_unit=ms&timezone=Europe%2FCopenhagen&forecast_days=1"

    suspend fun hillerod(): Weather = withContext(Dispatchers.IO) { parse(httpGet(HILLEROD)) }

    fun parse(text: String): Weather {
        val root = json.parseToJsonElement(text).jsonObject
        val current = root.getValue("current").jsonObject
        val daily = root.getValue("daily").jsonObject
        fun day(key: String): Double = daily[key]?.jsonArray?.firstOrNull()?.jsonPrimitive?.doubleOrNull ?: 0.0
        fun now(key: String): Double = current[key]?.jsonPrimitive?.doubleOrNull ?: 0.0
        return Weather(
            temperature = now("temperature_2m"),
            code = current["weather_code"]?.jsonPrimitive?.intOrNull ?: -1,
            max = day("temperature_2m_max"),
            min = day("temperature_2m_min"),
            rainChance = day("precipitation_probability_max").toInt(),
            rainMm = day("precipitation_sum"),
            wind = now("wind_speed_10m"),
        )
    }

    /** WMO-vejrkoder på dansk. */
    fun describe(code: Int): String = when (code) {
        0 -> "Klart vejr"
        1 -> "Overvejende klart"
        2 -> "Delvist skyet"
        3 -> "Overskyet"
        45, 48 -> "Tåge"
        in 51..57 -> "Støvregn"
        in 61..67 -> "Regn"
        in 71..77 -> "Sne"
        in 80..82 -> "Regnbyger"
        85, 86 -> "Snebyger"
        in 95..99 -> "Torden"
        else -> "Ukendt vejr"
    }
}

// ---------- S-tog (Rejseplanen, kræver en gratis nøgle) ----------

data class TrainDeparture(
    val planned: LocalDateTime,
    val expected: LocalDateTime?,
    val direction: String,
    val cancelled: Boolean,
    val track: String,
) {
    val delayMinutes: Int
        get() = expected?.let { Duration.between(planned, it).toMinutes().toInt() }?.coerceAtLeast(0) ?: 0
}

/** Næste afgange på linje A: fra Favrholm mod København og fra Nørreport mod Hillerød. */
data class TrainStatus(
    val towardsCity: List<TrainDeparture>,
    val towardsHillerod: List<TrainDeparture>,
)

object Rejseplanen {
    private const val BASE = "https://www.rejseplanen.dk/api/"
    const val FAVRHOLM = "Favrholm St."
    const val NORREPORT = "Nørreport St."

    private fun encode(text: String) = URLEncoder.encode(text, "UTF-8")

    /** Stoppestedets id, slået op på navn. */
    suspend fun stopId(key: String, name: String): String = withContext(Dispatchers.IO) {
        val text = httpGet("${BASE}location.name?accessId=${encode(key)}&input=${encode(name)}&type=S&format=json")
        parseStopId(text) ?: error("Stationen $name blev ikke fundet")
    }

    suspend fun departures(key: String, stopId: String): List<TrainDeparture> = withContext(Dispatchers.IO) {
        val text = httpGet(
            "${BASE}departureBoard?accessId=${encode(key)}&id=${encode(stopId)}&format=json" +
                "&useBus=0&useMetro=0&maxJourneys=40&duration=120"
        )
        parseDepartures(text, line = "A")
    }

    suspend fun status(key: String, favrholmId: String, norreportId: String): TrainStatus = TrainStatus(
        towardsCity = departures(key, favrholmId).filterNot { it.direction.contains("Hillerød", ignoreCase = true) }.take(4),
        towardsHillerod = departures(key, norreportId).filter { it.direction.contains("Hillerød", ignoreCase = true) }.take(4),
    )

    /** Første StopLocation i svaret. Tåler både liste og enkelt objekt. */
    fun parseStopId(text: String): String? {
        fun find(element: JsonElement): String? = when (element) {
            is JsonObject -> {
                val stop = element["StopLocation"]
                if (stop is JsonObject) {
                    (stop["extId"] ?: stop["id"])?.jsonPrimitive?.content
                } else {
                    element.values.firstNotNullOfOrNull { find(it) }
                }
            }
            is JsonArray -> element.firstNotNullOfOrNull { find(it) }
            else -> null
        }
        return find(json.parseToJsonElement(text))
    }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString || it.content.isNotBlank() }?.content

    private fun lineOf(departure: JsonObject): String {
        val products = listOfNotNull(departure["ProductAtStop"], departure["Product"]).flatMap {
            when (it) {
                is JsonArray -> it.mapNotNull { p -> p as? JsonObject }
                is JsonObject -> listOf(it)
                else -> emptyList()
            }
        }
        val fromProduct = products.firstNotNullOfOrNull { p -> p.str("displayNumber") ?: p.str("line") }
        return (fromProduct ?: departure.str("name") ?: "").trim().removePrefix("Linje ").trim()
    }

    /** Afgange på [line] fra et departureBoard-svar. */
    fun parseDepartures(text: String, line: String): List<TrainDeparture> {
        val root = json.parseToJsonElement(text).jsonObject
        val list = when (val departures = root["Departure"]) {
            is JsonArray -> departures.mapNotNull { it as? JsonObject }
            is JsonObject -> listOf(departures)
            else -> emptyList()
        }
        return list.filter { lineOf(it).equals(line, ignoreCase = true) || lineOf(it).endsWith(" $line") }
            .mapNotNull { d ->
                val date = d.str("date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return@mapNotNull null
                val time = d.str("time")?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: return@mapNotNull null
                val rtTime = d.str("rtTime")?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
                val rtDate = d.str("rtDate")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: date
                TrainDeparture(
                    planned = LocalDateTime.of(date, time),
                    expected = rtTime?.let { LocalDateTime.of(rtDate, it) },
                    direction = d.str("direction").orEmpty(),
                    cancelled = (d["cancelled"] as? JsonPrimitive)?.booleanOrNull == true,
                    track = d.str("rtTrack") ?: d.str("track") ?: "",
                )
            }
            .sortedBy { it.planned }
    }
}
