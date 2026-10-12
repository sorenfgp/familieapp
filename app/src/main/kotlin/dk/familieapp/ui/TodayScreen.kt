package dk.familieapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dk.familieapp.data.CHORE_APPROVED
import dk.familieapp.data.CHORE_DONE
import dk.familieapp.data.CalendarEvent
import dk.familieapp.data.CalendarMath
import dk.familieapp.data.ChorePlan
import dk.familieapp.data.Dates
import dk.familieapp.data.DeviceCalendarRepo
import dk.familieapp.data.ROLE_PARENT
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@Composable
fun TodayScreen(vm: AppViewModel, nav: NavController) {
    val meals by vm.meals.collectAsStateWithLifecycle()
    val shopping by vm.shopping.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val me by vm.me.collectAsStateWithLifecycle()
    val templates by vm.choreTemplates.collectAsStateWithLifecycle()
    val chores by vm.chores.collectAsStateWithLifecycle()
    val isParent = me?.role == ROLE_PARENT

    val today = LocalDate.now()
    val context = LocalContext.current
    val calendarRepo = remember { DeviceCalendarRepo(context.applicationContext) }
    val zone = remember { ZoneId.systemDefault() }
    val familyKey = settings?.calendarKey.orEmpty()

    // Null betyder: ingen adgang eller ingen familiekalender endnu.
    var todayEvents by remember { mutableStateOf<List<CalendarEvent>?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh += 1 }
    LaunchedEffect(refresh, familyKey) {
        todayEvents = if (calendarRepo.hasReadPermission() && familyKey.isNotBlank()) {
            calendarRepo.findCalendar(familyKey, calendarRepo.calendars())
                ?.let { calendarRepo.events(today, today, it.id) }
        } else {
            null
        }
    }

    val meal = meals.firstOrNull { it.date == today.toString() }
    val cook = meal?.cook.orEmpty()
    val open = shopping.filter { !it.checked }.sortedBy { it.createdAt }
    val week = ChorePlan.week(Dates.weekStart(today), templates, chores)
    val myChoresToday = week.filter { it.date == today.toString() && it.childId == me?.id }
    val pending = week.filter { it.status == CHORE_DONE }
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
        ScreenHeader(
            Dates.long(today).replaceFirstChar { it.uppercase() },
            "$greeting${me?.name?.let { ", $it" }.orEmpty()}",
        ) {
            ProfileButton(vm, nav)
        }
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
                    Text(meal?.title ?: "Ikke planlagt", fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold)
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

            // Pligter
            if (isParent && pending.isNotEmpty() || !isParent && myChoresToday.isNotEmpty()) {
                Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = Color.White) {
                    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 8.dp)) {
                        if (isParent) {
                            Text("Pligter", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "${pending.size} ${if (pending.size == 1) "pligt venter" else "pligter venter"} på din bekræftelse.",
                                fontSize = 15.sp,
                                color = Muted,
                            )
                        } else {
                            Text("Dine pligter i dag", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(6.dp))
                            myChoresToday.forEach { entry ->
                                val state = when (entry.status) {
                                    CHORE_APPROVED -> "bekræftet"
                                    CHORE_DONE -> "venter på bekræftelse"
                                    else -> "ikke udført"
                                }
                                Text("${entry.title} · $state", fontSize = 15.sp)
                            }
                        }
                        TextButton(
                            onClick = {
                                vm.choreTab = 0
                                nav.openTab("chores")
                            },
                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 12.dp),
                        ) {
                            Text("Åbn pligter", fontWeight = FontWeight.Bold)
                        }
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
                Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 8.dp)) {
                    Text("I dag i kalenderen", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    val shownEvents = todayEvents
                    when {
                        shownEvents == null -> Text(
                            "Familiekalenderen sættes op under fanen Kalender.",
                            fontSize = 15.sp,
                            color = Muted,
                        )
                        shownEvents.isEmpty() -> Text("Ingen aftaler i dag.", fontSize = 15.sp, color = Muted)
                        else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            shownEvents.take(4).forEach { event ->
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(
                                        CalendarMath.timeLabel(event, today, zone),
                                        Modifier.width(104.dp),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(event.title, Modifier.weight(1f), fontSize = 15.sp, maxLines = 1)
                                }
                            }
                            if (shownEvents.size > 4) {
                                Text("og ${shownEvents.size - 4} mere", fontSize = 14.sp, color = Muted)
                            }
                        }
                    }
                    TextButton(
                        onClick = { nav.openTab("calendar") },
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 12.dp),
                    ) {
                        Text("Åbn kalenderen", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
