package dk.familieapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dk.familieapp.data.TrainDeparture
import dk.familieapp.data.WeatherService
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val Orange = Color(0xFFC8641B)
private val clock = DateTimeFormatter.ofPattern("HH.mm")

/** Dagens vejr i Hillerød fra Open-Meteo: morgen, middag og aften, og hvornår det regner. */
@Composable
fun WeatherCard(vm: AppViewModel, refresh: Int) {
    LaunchedEffect(refresh) { vm.loadWeather() }
    val result = vm.weather
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = Color.White) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val weather = result?.getOrNull()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Vejret i Hillerød", Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                if (weather != null) {
                    Text(
                        "${weather.temperature.roundToInt()}° nu",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Muted,
                    )
                }
            }
            when {
                weather != null -> {
                    Row(Modifier.fillMaxWidth()) {
                        weather.parts.forEach { part ->
                            Column(Modifier.weight(1f)) {
                                Text(part.label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Muted)
                                Text("${part.temperature.roundToInt()}°", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                Text(WeatherService.describe(part.code), fontSize = 13.sp, maxLines = 1)
                            }
                        }
                    }
                    val rain = weather.rain
                    Text(
                        if (rain == null) {
                            "Ingen regn resten af dagen"
                        } else {
                            val mm = "%.1f".format(rain.mm).replace('.', ',')
                            "Regn kl. ${"%02d".format(rain.startHour)}–${"%02d".format(rain.endHour % 24)}, ca. $mm mm" +
                                if (rain.moreLater) " · mere senere" else ""
                        },
                        fontSize = 14.sp,
                        fontWeight = if (rain != null) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (rain != null) Color(0xFF24598F) else Muted,
                        maxLines = 1,
                    )
                }
                result == null -> Text("Henter vejret …", fontSize = 15.sp, color = Muted)
                else -> Text("Vejret kunne ikke hentes. Tjek internetforbindelsen.", fontSize = 15.sp, color = Muted)
            }
        }
    }
}

/** Næste afgange på S-togets linje A mellem Favrholm og Nørreport, med forsinkelser. */
@Composable
fun TrainCard(vm: AppViewModel, refresh: Int) {
    LaunchedEffect(refresh) { vm.loadTrain() }
    val result = vm.train

    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = Color.White) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val status = result?.getOrNull()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("S-tog linje A", Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                if (status != null) {
                    val all = status.towardsCity + status.towardsHillerod
                    val worst = all.maxOfOrNull { it.delayMinutes } ?: 0
                    when {
                        all.any { it.cancelled } -> Pill("Aflysninger", Color(0xFFF6D3C0))
                        worst >= 3 -> Pill("Op til $worst min forsinket", Color(0xFFF6D3C0))
                        else -> Pill("Kører til tiden")
                    }
                }
            }
            when {
                status != null -> {
                    DepartureLine("Favrholm → København", status.towardsCity)
                    DepartureLine("Nørreport → Hillerød", status.towardsHillerod)
                    status.notices.take(2).forEach { notice ->
                        Text(notice, fontSize = 14.sp, lineHeight = 19.sp, color = Orange)
                    }
                    TextButton(onClick = { vm.loadTrain(force = true) }, contentPadding = PaddingValues(0.dp)) {
                        Text("Opdatér", fontWeight = FontWeight.Bold)
                    }
                }
                result == null -> Text("Henter afgange …", fontSize = 15.sp, color = Muted)
                else -> {
                    Text(
                        "Afgangene kunne ikke hentes fra mittog.dk. Tjek internetforbindelsen.",
                        fontSize = 14.sp,
                        color = Muted,
                    )
                    TextButton(onClick = { vm.loadTrain(force = true) }, contentPadding = PaddingValues(0.dp)) {
                        Text("Prøv igen", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun DepartureLine(label: String, departures: List<TrainDeparture>) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Muted)
        if (departures.isEmpty()) {
            Text("Ingen afgange de næste to timer", fontSize = 15.sp)
        }
        departures.forEach { d ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    clock.format(d.planned),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = if (d.cancelled) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                )
                when {
                    d.cancelled -> Text("Aflyst", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Orange)
                    d.delayMinutes > 0 -> Text(
                        "+${d.delayMinutes} min (${clock.format(d.expected)})",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Orange,
                    )
                    else -> Text("Til tiden", fontSize = 14.sp, color = Green)
                }
                if (d.track.isNotBlank()) {
                    Text("spor ${d.track}", fontSize = 13.sp, color = Muted)
                }
            }
        }
    }
}

/** Herrelandsholdets kommende landskampe. */
@Composable
fun FootballCard(vm: AppViewModel, refresh: Int) {
    LaunchedEffect(refresh) { vm.loadMatches() }
    val result = vm.matches
    val matchClock = DateTimeFormatter.ofPattern("HH.mm")
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = Color.White) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Herrelandsholdet", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            val matches = result?.getOrNull()
            when {
                matches != null && matches.isEmpty() ->
                    Text("Ingen kampe er sat på programmet endnu.", fontSize = 15.sp, color = Muted)
                matches != null -> matches.take(4).forEach { m ->
                    val date = m.kickoff.toLocalDate()
                    Column {
                        Text(
                            if (m.home) "Danmark – ${m.opponent}" else "${m.opponent} – Danmark",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            listOf(
                                "${dk.familieapp.data.Dates.dayAbbr(date)} ${dk.familieapp.data.Dates.short(date)} kl. ${matchClock.format(m.kickoff)}",
                                if (m.home) "hjemme" else "ude",
                                m.competition,
                            ).filter { it.isNotBlank() }.joinToString(" · "),
                            fontSize = 13.sp,
                            color = Muted,
                        )
                    }
                }
                result == null -> Text("Henter kampprogrammet …", fontSize = 15.sp, color = Muted)
                else -> Text("Kampprogrammet kunne ikke hentes.", fontSize = 15.sp, color = Muted)
            }
        }
    }
}
