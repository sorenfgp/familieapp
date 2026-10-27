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
    const val FOOTBALL = "football"

    /** I den rækkefølge, de vises på forsiden. */
    val all = listOf(
        WEATHER to "Vejret i Hillerød",
        TRAIN to "S-tog linje A (Favrholm–Nørreport)",
        DINNER to "Aftensmad i dag",
        CHORES to "Pligter",
        SHOPPING to "Indkøbsliste",
        CALENDAR to "I dag i kalenderen",
        FOOTBALL to "Herrelandsholdets kampe",
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

// ---------- S-tog (mittog.dk, kræver ingen nøgle) ----------

data class TrainDeparture(
    val planned: LocalDateTime,
    val expected: LocalDateTime?,
    val direction: String,
    val cancelled: Boolean,
    val track: String,
    /** Stationskoder, toget kører til herfra, endestationen medregnet. */
    val stops: List<String> = emptyList(),
) {
    val delayMinutes: Int
        get() = expected?.let { Duration.between(planned, it).toMinutes().toInt() }?.coerceAtLeast(0) ?: 0
}

/** Næste afgange på linje A: fra Favrholm mod København og fra Nørreport mod Hillerød. */
data class TrainStatus(
    val towardsCity: List<TrainDeparture>,
    val towardsHillerod: List<TrainDeparture>,
    /** Driftsmeddelelser fra DSB for de to stationer. */
    val notices: List<String> = emptyList(),
)

data class TrainBoard(val departures: List<TrainDeparture>, val notices: List<String>)

/**
 * Live afgangstavler fra mittog.dk, som viser DSB's og Banedanmarks egne afgangsdata.
 * Der er ingen nøgle og intet officielt API: hjemmesiden åbner en WebSocket pr. station,
 * og serveren sender hele tavlen som JSON. Appen tager den første tavle og lukker igen.
 * Kan holde op med at virke, hvis mittog.dk ændrer sit format.
 */
object MitTog {
    private const val WS = "wss://api.mittog.dk/api/ws/stog/departure/"
    const val FAVRHOLM = "FVT"
    const val NORREPORT = "KN"
    const val HILLEROD = "HI"

    private val names = mapOf(
        "HI" to "Hillerød", "FVT" to "Favrholm", "LI" to "Allerød", "BI" to "Birkerød", "HOT" to "Holte",
        "LY" to "Lyngby", "HL" to "Hellerup", "SAM" to "Svanemøllen", "KK" to "Østerport", "KN" to "Nørreport",
        "VPT" to "Vesterport", "KH" to "København H", "DBT" to "Dybbølsbro", "SYV" to "Sydhavn",
        "NEL" to "København Syd", "UND" to "Hundige", "GRE" to "Greve", "KLU" to "Karlslunde",
        "SOL" to "Solrød Strand", "JSI" to "Jersie", "KJN" to "Køge Nord", "KJ" to "Køge",
    )

    fun name(code: String): String = names[code] ?: code

    private val hiddenStopTypes = setOf("TechnicalStop", "CommercielDisembarcationOnly", "FinalDestination", "PassThrough")
    private val timeFormat = java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss")

    private val client by lazy {
        okhttp3.OkHttpClient.Builder()
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    /** Den første tavle, serveren sender for [station]. */
    suspend fun boardText(station: String): String = kotlinx.coroutines.withTimeout(20_000) {
        kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            val request = okhttp3.Request.Builder()
                .url(WS + URLEncoder.encode(station, "UTF-8") + "/")
                .header("Origin", "https://mittog.dk")
                .header("User-Agent", "Familien-app")
                .build()
            val socket = client.newWebSocket(request, object : okhttp3.WebSocketListener() {
                override fun onMessage(webSocket: okhttp3.WebSocket, text: String) {
                    if (cont.isActive) cont.resumeWith(Result.success(text))
                    webSocket.close(1000, null)
                }

                override fun onFailure(webSocket: okhttp3.WebSocket, t: Throwable, response: okhttp3.Response?) {
                    if (cont.isActive) cont.resumeWith(Result.failure(t))
                }

                override fun onClosed(webSocket: okhttp3.WebSocket, code: Int, reason: String) {
                    if (cont.isActive) cont.resumeWith(Result.failure(java.io.IOException("Forbindelsen blev lukket")))
                }
            })
            cont.invokeOnCancellation { socket.cancel() }
        }
    }

    suspend fun status(): TrainStatus = withContext(Dispatchers.IO) {
        val favrholm = parseBoard(boardText(FAVRHOLM), FAVRHOLM)
        val norreport = parseBoard(boardText(NORREPORT), NORREPORT)
        TrainStatus(
            // Favrholm ligger lige før endestationen, så alt der ikke kører mod Hillerød, kører mod byen.
            towardsCity = favrholm.departures.filter { HILLEROD !in it.stops }.take(4),
            towardsHillerod = norreport.departures.filter { FAVRHOLM in it.stops || HILLEROD in it.stops }.take(4),
            notices = (favrholm.notices + norreport.notices).distinct(),
        )
    }

    private fun parseTime(value: String?): LocalDateTime? {
        if (value.isNullOrBlank() || value.startsWith("01-01-0001")) return null
        return runCatching { LocalDateTime.parse(value, timeFormat) }.getOrNull()
            ?: runCatching { java.time.OffsetDateTime.parse(value).atZoneSameInstant(java.time.ZoneId.of("Europe/Copenhagen")).toLocalDateTime() }.getOrNull()
            ?: runCatching { LocalDateTime.parse(value) }.getOrNull()
    }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

    private fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull

    private fun JsonObject.objects(key: String): List<JsonObject> =
        (this[key] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()

    /**
     * Afgange på [line] fra en tavle. Tog, der er kørt, ender her eller ikke tager passagerer,
     * springes over, ligesom på mittog.dk.
     */
    fun parseBoard(
        text: String,
        station: String,
        line: String = "A",
        now: LocalDateTime = LocalDateTime.now(java.time.ZoneId.of("Europe/Copenhagen")),
    ): TrainBoard {
        val root = json.parseToJsonElement(text).jsonObject
        val data = root["data"] as? JsonObject
        val departures = data?.objects("Trains").orEmpty().mapNotNull { train ->
            if (train.str("Product") != "STRAIN") return@mapNotNull null
            if (!train.str("LineName").equals(line, ignoreCase = true)) return@mapNotNull null
            if (train.str("TrainType").let { it != null && it != "Passenger" }) return@mapNotNull null
            if (train.str("StopType") in hiddenStopTypes) return@mapNotNull null
            if (train.bool("MayDepartEarly") == true || train.bool("IsLastStop") == true) return@mapNotNull null
            if (train.str("InformationType").let { it != null && it != "NORMAL" }) return@mapNotNull null
            if (parseTime(train.str("TrainDeparted")) != null) return@mapNotNull null

            val planned = parseTime(train.str("ScheduleTimeDeparture")) ?: parseTime(train.str("ScheduleTime"))
                ?: return@mapNotNull null
            val expected = parseTime(train.str("EstimatedTimeDeparture")) ?: parseTime(train.str("DelayTime"))
            if ((expected ?: planned).isBefore(now.minusSeconds(30))) return@mapNotNull null

            val routes = train.objects("Routes")
            val destinations = routes.mapNotNull { it.str("DestinationStationId") }.ifEmpty {
                (train["TargetStation"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }.orEmpty()
            }.distinct()
            if (destinations.isNotEmpty() && destinations.all { it == station }) return@mapNotNull null
            val stops = (routes.flatMap { r -> r.objects("Stations").mapNotNull { it.str("StationId") } } + destinations)
                .filter { it != station }
                .distinct()

            TrainDeparture(
                planned = planned,
                expected = expected,
                direction = destinations.joinToString(" / ") { name(it) },
                cancelled = train.bool("IsCancelledDeparture") ?: (train.bool("IsCancelled") == true),
                track = train.str("TrackCurrent").orEmpty(),
                stops = stops,
            )
        }.sortedBy { it.expected ?: it.planned }

        val notices = (root["pico"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            .filter { it.str("station_code").let { code -> code == null || code == station } }
            .mapNotNull { it.str("header") }
        return TrainBoard(departures, notices)
    }
}

// ---------- Herrelandsholdet i fodbold (ESPN, kræver ingen nøgle) ----------

data class Match(
    val kickoff: java.time.ZonedDateTime,
    val opponent: String,
    val home: Boolean,
    val competition: String,
    val city: String,
)

/**
 * Kommende landskampe for Danmarks herrelandshold fra ESPN's åbne (men udokumenterede) API.
 * Danmark har id 479 hos ESPN.
 */
object Football {
    private const val URL =
        "https://site.api.espn.com/apis/site/v2/sports/soccer/all/teams/479/schedule?fixture=true"

    private val danishNames = mapOf(
        "Norway" to "Norge", "Sweden" to "Sverige", "Finland" to "Finland", "Iceland" to "Island",
        "Germany" to "Tyskland", "Netherlands" to "Holland", "Belgium" to "Belgien", "France" to "Frankrig",
        "Spain" to "Spanien", "Portugal" to "Portugal", "Italy" to "Italien", "England" to "England",
        "Scotland" to "Skotland", "Wales" to "Wales", "Northern Ireland" to "Nordirland",
        "Republic of Ireland" to "Irland", "Ireland" to "Irland", "Switzerland" to "Schweiz",
        "Austria" to "Østrig", "Poland" to "Polen", "Czechia" to "Tjekkiet", "Czech Republic" to "Tjekkiet",
        "Croatia" to "Kroatien", "Serbia" to "Serbien", "Slovenia" to "Slovenien", "Slovakia" to "Slovakiet",
        "Hungary" to "Ungarn", "Romania" to "Rumænien", "Greece" to "Grækenland", "Turkey" to "Tyrkiet",
        "Türkiye" to "Tyrkiet", "Ukraine" to "Ukraine", "Belarus" to "Hviderusland", "Faroe Islands" to "Færøerne",
        "Bosnia-Herzegovina" to "Bosnien-Hercegovina", "North Macedonia" to "Nordmakedonien",
        "Kazakhstan" to "Kasakhstan", "Lithuania" to "Litauen", "Latvia" to "Letland", "Estonia" to "Estland",
        "Georgia" to "Georgien", "Albania" to "Albanien", "Montenegro" to "Montenegro", "Kosovo" to "Kosovo",
        "Cyprus" to "Cypern", "Israel" to "Israel", "Luxembourg" to "Luxembourg", "Malta" to "Malta",
        "United States" to "USA", "USA" to "USA", "Mexico" to "Mexico", "Brazil" to "Brasilien",
        "Argentina" to "Argentina", "Morocco" to "Marokko", "Japan" to "Japan", "South Korea" to "Sydkorea",
    )

    fun danish(name: String): String = danishNames[name] ?: name

    suspend fun upcoming(): List<Match> = withContext(Dispatchers.IO) { parse(httpGet(URL), java.time.Instant.now()) }

    /** Kampe fra [now] og frem (en igangværende kamp tæller med de første tre timer). */
    fun parse(text: String, now: java.time.Instant): List<Match> {
        val zone = java.time.ZoneId.of("Europe/Copenhagen")
        val events = (json.parseToJsonElement(text).jsonObject["events"] as? JsonArray).orEmpty()
        return events.mapNotNull { e ->
            val event = e as? JsonObject ?: return@mapNotNull null
            val date = (event["date"] as? JsonPrimitive)?.content ?: return@mapNotNull null
            // ESPN skriver fx "2026-11-14T19:45Z" uden sekunder.
            val instant = runCatching { java.time.OffsetDateTime.parse(date).toInstant() }.getOrNull()
                ?: return@mapNotNull null
            if (instant.isBefore(now.minus(Duration.ofHours(3)))) return@mapNotNull null
            val competition = (event["competitions"] as? JsonArray)?.firstOrNull() as? JsonObject
            val competitors = (competition?.get("competitors") as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
            fun teamName(c: JsonObject): String =
                ((c["team"] as? JsonObject)?.get("displayName") as? JsonPrimitive)?.content.orEmpty()
            val denmark = competitors.firstOrNull { teamName(it) == "Denmark" }
            val other = competitors.firstOrNull { it !== denmark } ?: return@mapNotNull null
            val home = (denmark?.get("homeAway") as? JsonPrimitive)?.content == "home"
            val league = ((event["league"] as? JsonObject)?.get("name") as? JsonPrimitive)?.content
                ?: ((event["season"] as? JsonObject)?.get("displayName") as? JsonPrimitive)?.content
                ?: ""
            val city = ((competition?.get("venue") as? JsonObject)?.get("address") as? JsonObject)
                ?.get("city")?.let { (it as? JsonPrimitive)?.content }.orEmpty()
            Match(
                kickoff = instant.atZone(zone),
                opponent = danish(teamName(other)),
                home = home,
                competition = league.replace("International Friendly", "Venskabskamp").replace("Friendly", "Venskabskamp"),
                city = city,
            )
        }.sortedBy { it.kickoff }
    }
}
