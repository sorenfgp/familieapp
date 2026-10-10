package dk.familieapp.ui

import android.Manifest
import android.content.ContentUris
import android.content.Intent
import android.provider.CalendarContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dk.familieapp.data.CalendarEvent
import dk.familieapp.data.CalendarMath
import dk.familieapp.data.Dates
import dk.familieapp.data.DeviceCalendar
import dk.familieapp.data.DeviceCalendarRepo
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@Composable
fun CalendarScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val repo = remember { DeviceCalendarRepo(context.applicationContext) }
    val meals by vm.meals.collectAsStateWithLifecycle()
    val zone = remember { ZoneId.systemDefault() }

    var granted by remember { mutableStateOf(repo.hasPermission()) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
    }

    val today = LocalDate.now()
    var weekOffset by rememberSaveable { mutableIntStateOf(0) }
    var selectedIso by rememberSaveable { mutableStateOf(today.toString()) }
    val selected = LocalDate.parse(selectedIso)
    val start = Dates.weekStart(today).plusWeeks(weekOffset.toLong())

    var events by remember { mutableStateOf(emptyList<CalendarEvent>()) }
    var calendars by remember { mutableStateOf(emptyList<DeviceCalendar>()) }
    var hidden by remember { mutableStateOf(repo.hiddenCalendars) }
    var choosing by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }

    // Hent igen, når man kommer tilbage fra kalenderappen.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        granted = repo.hasPermission()
        refresh += 1
    }
    LaunchedEffect(granted, start, hidden, refresh) {
        if (granted) {
            calendars = repo.calendars()
            events = repo.events(start, start.plusDays(6))
        }
    }

    fun moveWeek(weeks: Int) {
        weekOffset += weeks
        selectedIso = selected.plusWeeks(weeks.toLong()).toString()
    }

    fun open(intent: Intent) {
        runCatching { context.startActivity(intent) }
            .onFailure { context.toast("Der er ingen kalenderapp på telefonen") }
    }

    fun newEvent() {
        val time = if (selected == today) LocalTime.now().plusHours(1).withMinute(0) else LocalTime.of(12, 0)
        val begin = selected.atTime(time).atZone(zone).toInstant().toEpochMilli()
        open(
            Intent(Intent.ACTION_INSERT)
                .setData(CalendarContract.Events.CONTENT_URI)
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, begin + 60 * 60 * 1000)
        )
    }

    fun show(event: CalendarEvent) {
        open(
            Intent(Intent.ACTION_VIEW)
                .setData(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.eventId))
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.begin)
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.end)
        )
    }

    val monthName = Dates.months[selected.monthValue - 1].replaceFirstChar { it.uppercase() }
    val dayEvents = events.filter { it.isOn(selected) }
    val meal = meals.firstOrNull { it.date == selectedIso }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("$monthName ${selected.year} · uge ${Dates.weekNumber(start)}", "Kalender") {
                IconButton(onClick = { moveWeek(-1) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Forrige uge")
                }
                IconButton(onClick = { moveWeek(1) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Næste uge")
                }
            }

            // Ugestribe
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                for (index in 0..6) {
                    val day = start.plusDays(index.toLong())
                    val isSelected = day == selected
                    val hasEvents = events.any { it.isOn(day) }
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                when {
                                    isSelected -> Green
                                    day == today -> Mint
                                    else -> Color.Transparent
                                }
                            )
                            .clickable { selectedIso = day.toString() }
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            Dates.dayShort[index],
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isSelected) Color(0xFFCFE5D8) else Muted,
                        )
                        Text(
                            "${day.dayOfMonth}",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color.White else Ink,
                        )
                        Box(
                            Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        !hasEvents -> Color.Transparent
                                        isSelected -> Yellow
                                        else -> Green
                                    }
                                )
                        )
                    }
                }
            }

            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!granted) {
                    item(key = "permission") {
                        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = Color.White) {
                            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Vis familiens aftaler", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    "Appen viser de kalendere, telefonen allerede synkroniserer fra Google Kalender. " +
                                        "Den skal have lov til at læse kalenderen.",
                                    fontSize = 15.sp,
                                    lineHeight = 21.sp,
                                    color = Muted,
                                )
                                YellowButton(
                                    "Giv adgang til kalenderen",
                                    onClick = { permissionLauncher.launch(Manifest.permission.READ_CALENDAR) },
                                )
                                Text(
                                    "Sker der ikke noget, er adgangen afvist. Slå den til under telefonens indstillinger for appen.",
                                    fontSize = 13.sp,
                                    color = Muted,
                                )
                            }
                        }
                    }
                } else {
                    item(key = "status") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val shownCount = calendars.count { it.id !in hidden }
                            Text(
                                "Viser $shownCount af ${calendars.size} kalendere på telefonen",
                                Modifier.weight(1f),
                                fontSize = 13.sp,
                                color = Muted,
                            )
                            TextButton(onClick = { choosing = true }) {
                                Text("Vælg", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                item(key = "day") {
                    SectionLabel(
                        (if (selected == today) "I dag · " else "") + Dates.long(selected)
                    )
                }
                items(dayEvents, key = { "${it.eventId}-${it.begin}" }) { event ->
                    AgendaRow(
                        color = Color(event.color or 0xFF000000.toInt()),
                        title = event.title,
                        detail = listOf(CalendarMath.timeLabel(event, selected, zone), event.location)
                            .filter { it.isNotBlank() }
                            .joinToString(" · "),
                        onClick = { show(event) },
                    )
                }
                if (meal != null) {
                    item(key = "meal") {
                        AgendaRow(
                            color = Yellow,
                            title = "Aftensmad: ${meal.title}",
                            detail = if (meal.cook.isBlank()) "Fra madplanen" else "${meal.cook} laver mad · fra madplanen",
                            onClick = null,
                        )
                    }
                }
                if (granted && dayEvents.isEmpty() && meal == null) {
                    item(key = "empty") {
                        Text("Ingen aftaler denne dag.", Modifier.padding(vertical = 8.dp), color = Muted)
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { newEvent() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
            containerColor = Yellow,
            contentColor = Ink,
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Ny aftale")
        }
    }

    if (choosing) {
        CalendarChooser(
            calendars = calendars,
            initiallyHidden = hidden,
            onSave = { newHidden ->
                repo.hiddenCalendars = newHidden
                hidden = newHidden
                choosing = false
            },
            onDismiss = { choosing = false },
        )
    }
}

@Composable
private fun AgendaRow(color: Color, title: String, detail: String, onClick: (() -> Unit)?) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .height(IntrinsicSize.Min)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .width(4.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            if (detail.isNotBlank()) {
                Text(detail, fontSize = 14.sp, color = Muted)
            }
        }
    }
}

@Composable
private fun CalendarChooser(
    calendars: List<DeviceCalendar>,
    initiallyHidden: Set<Long>,
    onSave: (Set<Long>) -> Unit,
    onDismiss: () -> Unit,
) {
    var hidden by remember { mutableStateOf(initiallyHidden) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Vælg kalendere") },
        text = {
            if (calendars.isEmpty()) {
                Text("Telefonen har ingen synkroniserede kalendere. Tjek, at Google Kalender er sat op på telefonen.")
            } else {
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(calendars, key = { it.id }) { calendar ->
                        val shown = calendar.id !in hidden
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { hidden = if (shown) hidden + calendar.id else hidden - calendar.id }
                                .heightIn(min = 52.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Checkbox(checked = shown, onCheckedChange = null)
                            Column(Modifier.weight(1f)) {
                                Text(calendar.name, fontWeight = FontWeight.SemiBold)
                                if (calendar.account.isNotBlank() && calendar.account != calendar.name) {
                                    Text(calendar.account, fontSize = 13.sp, color = Muted)
                                }
                            }
                            Box(
                                Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(Color(calendar.color or 0xFF000000.toInt()))
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(hidden) }) { Text("Gem") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annullér") }
        },
    )
}
