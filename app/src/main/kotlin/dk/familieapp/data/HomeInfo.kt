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

    /** Profilens rækkefølge, med eventuelle nye kort til sidst. */
    fun order(cardOrder: List<String>): List<String> {
        val known = all.map { it.first }
        return (cardOrder.filter { it in known } + known).distinct()
    }
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

/** Morgen, middag eller aften: gennemsnitstemperatur, det værste vejr og samlet regn. */
data class DayPart(val label: String, val temperature: Double, val code: Int, val rainMm: Double)

/** Første sammenhængende regnperiode fra nu af. [endHour] er timen, hvor regnen holder op. */
data class RainSpell(val startHour: Int, val endHour: Int, val mm: Double, val moreLater: Boolean)

data class Weather(
    val temperature: Double,
    val code: Int,
    val parts: List<DayPart>,
    val rain: RainSpell?,
)

object WeatherService {
    private const val HILLEROD =
        "https://api.open-meteo.com/v1/forecast?latitude=55.9267&longitude=12.3109" +
            "&current=temperature_2m,weather_code" +
            "&hourly=temperature_2m,precipitation,weather_code" +
            "&timezone=Europe%2FCopenhagen&forecast_days=1"

    /** Timer der regnes med i hver del af dagen. */
    private val dayParts = listOf("Morgen" to 6..11, "Middag" to 12..17, "Aften" to 18..23)

    /** Mindste regnmængde i en time, der tæller som regn (mm). */
    private const val RAIN_THRESHOLD = 0.2

    suspend fun hillerod(): Weather = withContext(Dispatchers.IO) { parse(httpGet(HILLEROD)) }

    fun parse(text: String, nowHour: Int = LocalTime.now().hour): Weather {
        val root = json.parseToJsonElement(text).jsonObject
        val current = root.getValue("current").jsonObject
        val hourly = root.getValue("hourly").jsonObject
        fun series(key: String): List<Double> =
            hourly[key]?.jsonArray?.map { it.jsonPrimitive.doubleOrNull ?: 0.0 }.orEmpty()
        val hours = hourly["time"]?.jsonArray?.map { it.jsonPrimitive.content.substringAfter('T').take(2).toIntOrNull() ?: 0 }.orEmpty()
        val temps = series("temperature_2m")
        val rain = series("precipitation")
        val codes = series("weather_code").map { it.toInt() }

        val parts = dayParts.map { (label, range) ->
            val idx = hours.indices.filter { hours[it] in range }
            DayPart(
                label = label,
                temperature = idx.map { temps.getOrElse(it) { 0.0 } }.average().takeIf { !it.isNaN() } ?: 0.0,
                // Højere WMO-kode betyder groft sagt dårligere vejr; vis det værste i perioden.
                code = idx.maxOfOrNull { codes.getOrElse(it) { -1 } } ?: -1,
                rainMm = idx.sumOf { rain.getOrElse(it) { 0.0 } },
            )
        }

        // Regn fra nu og resten af dagen: første sammenhængende periode.
        val wet = hours.indices.filter { hours[it] >= nowHour && rain.getOrElse(it) { 0.0 } >= RAIN_THRESHOLD }
        val spell = wet.firstOrNull()?.let { first ->
            var last = first
            while (last + 1 in wet) last++
            RainSpell(
                startHour = hours[first],
                endHour = hours[last] + 1,
                mm = (first..last).sumOf { rain.getOrElse(it) { 0.0 } },
                moreLater = wet.any { it > last },
            )
        }

        return Weather(
            temperature = current["temperature_2m"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
            code = current["weather_code"]?.jsonPrimitive?.intOrNull ?: -1,
            parts = parts,
            rain = spell,
        )
    }

    /** Kort vejrbeskrivelse til de små kolonner. */
    fun describe(code: Int): String = when (code) {
        0 -> "Klart"
        1 -> "Let skyet"
        2 -> "Skyet"
        3 -> "Overskyet"
        45, 48 -> "Tåge"
        in 51..57 -> "Støvregn"
        in 61..67 -> "Regn"
        in 71..77 -> "Sne"
        in 80..82 -> "Byger"
        85, 86 -> "Snebyger"
        in 95..99 -> "Torden"
        else -> "–"
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
