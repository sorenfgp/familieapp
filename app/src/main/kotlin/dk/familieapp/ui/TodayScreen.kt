package dk.familieapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dk.familieapp.data.Dates
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun TodayScreen(vm: AppViewModel, nav: NavController) {
    val meals by vm.meals.collectAsStateWithLifecycle()
    val shopping by vm.shopping.collectAsStateWithLifecycle()

    val today = LocalDate.now()
    val meal = meals.firstOrNull { it.date == today.toString() }
    val cook = meal?.cook.orEmpty()
    val open = shopping.filter { !it.checked }.sortedBy { it.createdAt }
    val hour = LocalTime.now().hour
    val greeting = when {
        hour < 10 -> "God morgen"
        hour < 12 -> "God formiddag"
        hour < 18 -> "God eftermiddag"
        else -> "God aften"
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader(Dates.long(today).replaceFirstChar { it.uppercase() }, greeting)
        Column(
            Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Aftensmad
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = Green,
                contentColor = Color.White,
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Aftensmad i dag", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFCFE5D8))
                    Text(
                        meal?.title ?: "Ikke planlagt",
                        fontSize = 26.sp,
                        lineHeight = 30.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    if (cook.isNotBlank()) {
                        Text("$cook laver mad", fontSize = 15.sp, color = Color(0xFFE6F1EA))
                    }
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = {
                            vm.mealTab = 0
                            nav.openTab("mealplan")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Yellow, contentColor = Ink),
                    ) {
                        Text(if (meal == null) "Planlæg aftensmad" else "Se ugens madplan", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Indkøbsliste
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = Color.White) {
                Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Indkøbsliste", Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Pill("${open.size} mangler")
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        when {
                            open.isEmpty() -> "Der mangler ikke noget lige nu."
                            open.size <= 3 -> open.joinToString(", ") { it.name }
                            else -> open.take(3).joinToString(", ") { it.name } + " og ${open.size - 3} andre varer"
                        },
                        fontSize = 15.sp,
                        color = Muted,
                    )
                    TextButton(
                        onClick = {
                            vm.shoppingTab = 0
                            nav.openTab("shopping")
                        },
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 12.dp),
                    ) {
                        Text("Åbn listen", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Kalender
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = Color.White) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("I dag i kalenderen", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Google Kalender bliver koblet på i næste version.",
                        fontSize = 15.sp,
                        color = Muted,
                    )
                }
            }
        }
    }
}
