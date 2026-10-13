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

/** Dagens vejr i Hillerød fra Open-Meteo. */
@Composable
fun WeatherCard(vm: AppViewModel, refresh: Int) {
    LaunchedEffect(refresh) { vm.loadWeather() }
    val result = vm.weather
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = Color.White) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Vejret i Hillerød", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            val weather = result?.getOrNull()
            when {
                weather != null -> {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("${weather.temperature.roundToInt()}°", fontSize = 36.sp, fontWeight = FontWeight.Bold)
                        Text(
                            WeatherService.describe(weather.code),
                            Modifier.padding(bottom = 6.dp),
                            fontSize = 16.sp,
                        )
                    }
                    Text(
                        "I dag ${weather.min.roundToInt()}° til ${weather.max.roundToInt()}° · " +
                            "regn ${weather.rainChance} % (${"%.1f".format(weather.rainMm).replace('.', ',')} mm) · " +
                            "vind ${weather.wind.roundToInt()} m/s",
                        fontSize = 14.sp,
                        color = Muted,
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
fun TrainCard(vm: AppViewModel, refresh: Int, onSetup: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val hasKey = settings?.rejseplanenKey?.isNotBlank() == true
    LaunchedEffect(refresh, hasKey) { vm.loadTrain() }
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
                !hasKey -> {
                    Text(
                        "Kræver en gratis nøgle fra Rejseplanen. En forælder indtaster den under Familien.",
                        fontSize = 14.sp,
                        color = Muted,
                    )
                    TextButton(onClick = onSetup, contentPadding = PaddingValues(0.dp)) {
                        Text("Gå til Familien", fontWeight = FontWeight.Bold)
                    }
                }
                status != null -> {
                    DepartureLine("Favrholm → København", status.towardsCity)
                    DepartureLine("Nørreport → Hillerød", status.towardsHillerod)
                    TextButton(onClick = { vm.loadTrain(force = true) }, contentPadding = PaddingValues(0.dp)) {
                        Text("Opdatér", fontWeight = FontWeight.Bold)
                    }
                }
                result == null -> Text("Henter afgange …", fontSize = 15.sp, color = Muted)
                else -> {
                    Text(
                        "Afgangene kunne ikke hentes. Tjek nøglen og internetforbindelsen.",
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
