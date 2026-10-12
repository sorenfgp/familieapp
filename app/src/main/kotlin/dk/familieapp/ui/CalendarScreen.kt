@file:OptIn(ExperimentalLayoutApi::class)

package dk.familieapp.ui

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dk.familieapp.data.CalendarEvent
import dk.familieapp.data.CalendarMath
import dk.familieapp.data.CalendarMirror
import dk.familieapp.data.Dates
import dk.familieapp.data.DeviceCalendar
import dk.familieapp.data.DeviceCalendarRepo
import dk.familieapp.data.EventWho
import dk.familieapp.data.Profile
import dk.familieapp.data.ROLE_PARENT
import dk.familieapp.data.Repeat
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

private val calendarPermissions = arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

private fun pickDate(context: Context, initial: LocalDate, onPick: (LocalDate) -> Unit) {
    DatePickerDialog(
        context,
        { _, year, month, day -> onPick(LocalDate.of(year, month + 1, day)) },
        initial.year,
        initial.monthValue - 1,
        initial.dayOfMonth,
    ).show()
}

@Composable
fun CalendarScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { DeviceCalendarRepo(context.applicationContext) }
    val meals by vm.meals.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val me by vm.me.collectAsStateWithLifecycle()
    val mirror by vm.calendarMirror.collectAsStateWithLifecycle()
    val syncInfo by vm.calendarSync.collectAsStateWithLifecycle()
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val isParent = me?.role == ROLE_PARENT
    val familyKey = settings?.calendarKey.orEmpty()
    val familyName = settings?.calendarName.orEmpty()
    val zone = remember { ZoneId.systemDefault() }

    var granted by remember { mutableStateOf(repo.hasReadPermission()) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = repo.hasReadPermission()
    }

    val today = LocalDate.now()
    // Ugevisning er standard, hver gang fanen åbnes.
    var monthView by rememberSaveable { mutableStateOf(false) }
    var selectedIso by rememberSaveable { mutableStateOf(today.toString()) }
    val selected = LocalDate.parse(selectedIso)
    val weekStart = Dates.weekStart(selected)
    val monthStart = selected.withDayOfMonth(1)
    val gridStart = Dates.weekStart(monthStart)
    val gridEnd = Dates.weekStart(monthStart.plusMonths(1).minusDays(1)).plusDays(6)
    val rangeStart = if (monthView) gridStart else weekStart
    val rangeEnd = if (monthView) gridEnd else weekStart.plusDays(6)

    var allCalendars by remember { mutableStateOf(emptyList<DeviceCalendar>()) }
    var familyCalendar by remember { mutableStateOf<DeviceCalendar?>(null) }
    var localEvents by remember { mutableStateOf(emptyList<CalendarEvent>()) }
    var loaded by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var choosing by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var openEvent by remember { mutableStateOf<CalendarEvent?>(null) }

    // Hent igen, når man kommer tilbage fra kalenderappen.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        granted = repo.hasReadPermission()
        refresh += 1
    }
    LaunchedEffect(granted, rangeStart, rangeEnd, familyKey, refresh) {
        if (granted) {
            allCalendars = repo.calendars()
            val found = repo.findCalendar(familyKey, allCalendars)
            familyCalendar = found
            localEvents = found?.let { repo.events(rangeStart, rangeEnd, it.id) }.orEmpty()
            // Telefonen har kalenderen: opdatér kopien, som børnenes enheder læser.
            if (found != null) vm.syncCalendar()
        } else {
            familyCalendar = null
        }
        loaded = true
    }

    // Har telefonen ikke selv kalenderen, vises kopien fra forældrenes telefon.
    val local = familyCalendar
    val events = if (local != null) localEvents else CalendarMirror.eventsBetween(mirror, rangeStart, rangeEnd, zone)
    val readsCopy = local == null && familyKey.isNotBlank()

    fun move(step: Long) {
        selectedIso = (if (monthView) selected.plusMonths(step) else selected.plusWeeks(step)).toString()
    }

    fun show(event: CalendarEvent) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setData(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.eventId))
                    .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.begin)
                    .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.end)
            )
        }.onFailure { context.toast("Der er ingen kalenderapp på telefonen") }
    }

    fun startCreating() {
        val calendar = familyCalendar ?: return
        when {
            !calendar.canWrite -> context.toast("Telefonens Google-konto må kun læse familiekalenderen")
            !repo.hasWritePermission() -> permissionLauncher.launch(calendarPermissions)
            else -> creating = true
        }
    }

    /** Ændrer i familiekalenderen og opdaterer visningen og kopien bagefter. */
    fun change(success: String, failure: String, action: suspend () -> Boolean) {
        if (!repo.hasWritePermission()) {
            permissionLauncher.launch(calendarPermissions)
            context.toast("Giv appen adgang til kalenderen, og prøv igen")
            return
        }
        scope.launch {
            val ok = action()
            context.toast(if (ok) success else failure)
            refresh += 1
            vm.syncCalendar(force = true)
        }
    }

    val monthName = Dates.months[selected.monthValue - 1].replaceFirstChar { it.uppercase() }
    val dayEvents = events.filter { it.isOn(selected) }
    val meal = meals.firstOrNull { it.date == selectedIso }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(
                if (monthView) "$monthName ${selected.year}"
                else "$monthName ${selected.year} · uge ${Dates.weekNumber(weekStart)}",
                "Kalender",
            ) {
                IconButton(onClick = { move(-1) }) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = if (monthView) "Forrige måned" else "Forrige uge",
                    )
                }
                IconButton(onClick = { move(1) }) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = if (monthView) "Næste måned" else "Næste uge",
                    )
                }
            }
            Segmented(
                options = listOf("Uge", "Måned"),
                selected = if (monthView) 1 else 0,
                onSelect = { monthView = it == 1 },
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
            )

            if (!monthView) {
                WeekStrip(weekStart, selected, today, events) { selectedIso = it.toString() }
            }

            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (monthView) {
                    item(key = "month") {
                        MonthGrid(monthStart, gridStart, gridEnd, selected, today, events) {
                            selectedIso = it.toString()
                        }
                    }
                }
                item(key = "status") {
                    when {
                        familyKey.isBlank() && isParent && !granted -> InfoCard(
                            title = "Vis familiens aftaler",
                            text = "Appen bruger en Google-kalender, som telefonen allerede synkroniserer. " +
                                "Den skal have lov til at læse og skrive i kalenderen.",
                            button = "Giv adgang til kalenderen",
                            onClick = { permissionLauncher.launch(calendarPermissions) },
                            note = "Sker der ikke noget, er adgangen afvist. Slå den til under telefonens indstillinger for appen.",
                        )
                        familyKey.isBlank() -> InfoCard(
                            title = "Ingen familiekalender endnu",
                            text = if (isParent) {
                                "Vælg den Google-kalender, hele familien skal læse fra og gemme i."
                            } else {
                                "En forælder skal vælge familiens kalender."
                            },
                            button = if (isParent) "Vælg familiekalender" else null,
                            onClick = { choosing = true },
                        )
                        local != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Familiekalender: $familyName",
                                Modifier.weight(1f),
                                fontSize = 13.sp,
                                color = Muted,
                            )
                            if (isParent) {
                                TextButton(onClick = { choosing = true }) {
                                    Text("Skift", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "Familiekalender: $familyName · ${syncLabel(syncInfo?.syncedAt)}",
                                fontSize = 13.sp,
                                color = Muted,
                            )
                            if (syncInfo == null) {
                                Text(
                                    "Kalenderen er ikke hentet endnu. Den hentes, når en forælder med adgang " +
                                        "til kalenderen åbner appen.",
                                    fontSize = 14.sp,
                                )
                            }
                            if (isParent && !granted) {
                                InfoCard(
                                    title = "Opret aftaler fra denne telefon",
                                    text = "Giv appen adgang til telefonens kalender for at se aftalerne direkte og oprette nye.",
                                    button = "Giv adgang til kalenderen",
                                    onClick = { permissionLauncher.launch(calendarPermissions) },
                                )
                            } else if (isParent && loaded) {
                                InfoCard(
                                    title = "Familiekalenderen mangler her",
                                    text = "Telefonen viser en kopi. Del \"$familyName\" med telefonens Google-konto " +
                                        "for at oprette aftaler herfra.",
                                    button = "Vælg en anden kalender",
                                    onClick = { choosing = true },
                                )
                            }
                        }
                    }
                }

                item(key = "day") {
                    SectionLabel((if (selected == today) "I dag · " else "") + Dates.long(selected))
                }
                items(dayEvents, key = { "${it.eventId}-${it.begin}" }) { event ->
                    AgendaRow(
                        color = Color(event.color or 0xFF000000.toInt()),
                        title = event.title,
                        detail = listOf(
                            CalendarMath.timeLabel(event, selected, zone),
                            event.location,
                            if (event.recurring) "fast" else "",
                        ).filter { it.isNotBlank() }.joinToString(" · "),
                        onClick = { openEvent = event },
                        who = EventWho.parse(event.description),
                        profiles = profiles.orEmpty(),
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
                if ((local != null || readsCopy) && dayEvents.isEmpty() && meal == null) {
                    item(key = "empty") {
                        Text("Ingen aftaler denne dag.", Modifier.padding(vertical = 8.dp), color = Muted)
                    }
                }
            }
        }

        if (local != null) {
            FloatingActionButton(
                onClick = { startCreating() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp),
                containerColor = Yellow,
                contentColor = Ink,
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Ny aftale")
            }
        }
    }

    if (choosing) {
        CalendarChooser(
            calendars = allCalendars,
            currentKey = familyKey,
            onPick = { calendar ->
                vm.setFamilyCalendar(calendar.key, calendar.name)
                choosing = false
            },
            onDismiss = { choosing = false },
        )
    }

    if (creating) {
        val calendar = familyCalendar
        NewEventDialog(
            initialDate = selected,
            profiles = profiles.orEmpty(),
            onSave = { title, location, date, startTime, endTime, who, repeat ->
                creating = false
                if (calendar != null) {
                    change(
                        success = if (repeat != null) "Den faste aftale er gemt i $familyName" else "Aftalen er gemt i $familyName",
                        failure = "Aftalen kunne ikke gemmes",
                    ) {
                        repo.insertEvent(
                            calendarId = calendar.id,
                            title = title,
                            location = location,
                            date = date,
                            start = startTime,
                            end = endTime,
                            description = EventWho.apply("", who),
                            repeat = repeat,
                        )
                    }
                }
            },
            onDismiss = { creating = false },
        )
    }

    openEvent?.let { event ->
        EventDialog(
            event = event,
            day = selected,
            zone = zone,
            profiles = profiles.orEmpty(),
            canEdit = local?.canWrite == true,
            canOpen = local != null,
            onSaveWho = { who ->
                openEvent = null
                change("Gemt i $familyName", "Aftalen kunne ikke ændres") {
                    repo.updateDescription(event.eventId, EventWho.apply(event.description, who))
                }
            },
            onMove = { newDate ->
                openEvent = null
                change("Flyttet til ${Dates.long(newDate)}", "Aftalen kunne ikke flyttes") {
                    repo.moveEvent(event, newDate)
                }
            },
            onCancelOnce = {
                openEvent = null
                change("Aflyst ${Dates.long(event.firstDay)}", "Aftalen kunne ikke aflyses") {
                    repo.cancelOccurrence(event.eventId, event.begin)
                }
            },
            onCancelRange = { from, to ->
                openEvent = null
                change("Aflyst fra ${Dates.short(from)} til ${Dates.short(to)}", "Ingen gange at aflyse i perioden") {
                    repo.cancelOccurrences(event.eventId, from, to) > 0
                }
            },
            onDelete = {
                openEvent = null
                change("Aftalen er slettet", "Aftalen kunne ikke slettes") {
                    repo.deleteEvent(event.eventId)
                }
            },
            onOpen = {
                openEvent = null
                show(event)
            },
            onDismiss = { openEvent = null },
        )
    }
}

/** "opdateret i dag kl. 14.05" */
private fun syncLabel(syncedAt: Long?): String {
    if (syncedAt == null || syncedAt == 0L) return "ikke hentet endnu"
    val moment = java.time.Instant.ofEpochMilli(syncedAt).atZone(ZoneId.systemDefault())
    val day = if (moment.toLocalDate() == LocalDate.now()) "i dag" else Dates.short(moment.toLocalDate())
    return "opdateret $day kl. ${CalendarMath.clock(moment.toLocalTime())}"
}

@Composable
private fun WeekStrip(
    start: LocalDate,
    selected: LocalDate,
    today: LocalDate,
    events: List<CalendarEvent>,
    onSelect: (LocalDate) -> Unit,
) {
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
                    .clickable { onSelect(day) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    Dates.dayAbbr(day),
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
}

/** Hele måneden i et gitter. Prikkerne viser, hvor mange aftaler dagen har (højst tre). */
@Composable
private fun MonthGrid(
    month: LocalDate,
    gridStart: LocalDate,
    gridEnd: LocalDate,
    selected: LocalDate,
    today: LocalDate,
    events: List<CalendarEvent>,
    onSelect: (LocalDate) -> Unit,
) {
    val weeks = ((ChronoUnit.DAYS.between(gridStart, gridEnd) + 1) / 7).toInt()
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .padding(8.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            Dates.dayShort.forEach { label ->
                Text(
                    label,
                    Modifier
                        .weight(1f)
                        .padding(vertical = 4.dp),
                    textAlign = TextAlign.Center,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Muted,
                )
            }
        }
        for (week in 0 until weeks) {
            Row(Modifier.fillMaxWidth()) {
                for (index in 0..6) {
                    val day = gridStart.plusDays(week * 7L + index)
                    val inMonth = day.month == month.month
                    val isSelected = day == selected
                    val count = events.count { it.isOn(day) }
                    Column(
                        Modifier
                            .weight(1f)
                            .height(50.dp)
                            .padding(2.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                when {
                                    isSelected -> Green
                                    day == today -> Mint
                                    else -> Color.Transparent
                                }
                            )
                            .clickable { onSelect(day) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            "${day.dayOfMonth}",
                            fontSize = 15.sp,
                            fontWeight = if (day == today || isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = when {
                                isSelected -> Color.White
                                inMonth -> Ink
                                else -> Muted.copy(alpha = 0.5f)
                            },
                        )
                        Row(
                            Modifier.height(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            repeat(minOf(count, 3)) {
                                Box(
                                    Modifier
                                        .size(5.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) Yellow else Green)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, text: String, button: String?, onClick: () -> Unit, note: String? = null) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = Color.White) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(text, fontSize = 15.sp, lineHeight = 21.sp, color = Muted)
            if (button != null) YellowButton(button, onClick = onClick)
            if (note != null) Text(note, fontSize = 13.sp, color = Muted)
        }
    }
}

@Composable
private fun AgendaRow(
    color: Color,
    title: String,
    detail: String,
    onClick: (() -> Unit)?,
    who: List<String>? = null,
    profiles: List<Profile> = emptyList(),
) {
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
        WhoBadges(who, profiles, Modifier.align(Alignment.CenterVertically))
    }
}

@Composable
private fun CalendarChooser(
    calendars: List<DeviceCalendar>,
    currentKey: String,
    onPick: (DeviceCalendar) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Familiekalender") },
        text = {
            if (calendars.isEmpty()) {
                Text("Telefonen har ingen synkroniserede kalendere. Tjek, at Google Kalender er sat op på telefonen.")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Kalenderen bruges af hele familien. Forældrene skal have adgang til den i Google Kalender.",
                        fontSize = 14.sp,
                        color = Muted,
                    )
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(calendars.distinctBy { it.key }, key = { it.key }) { calendar ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onPick(calendar) }
                                    .heightIn(min = 52.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                RadioButton(selected = calendar.key == currentKey, onClick = null)
                                Column(Modifier.weight(1f)) {
                                    Text(calendar.name, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        if (calendar.canWrite) calendar.account else "${calendar.account} · kun læseadgang",
                                        fontSize = 13.sp,
                                        color = Muted,
                                    )
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
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Luk") }
        },
    )
}

@Composable
private fun NewEventDialog(
    initialDate: LocalDate,
    profiles: List<Profile>,
    onSave: (
        title: String,
        location: String,
        date: LocalDate,
        start: LocalTime?,
        end: LocalTime?,
        who: List<String>,
        repeat: Repeat?,
    ) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(initialDate) }
    var allDay by remember { mutableStateOf(false) }
    var start by remember { mutableStateOf(LocalTime.of(17, 0)) }
    var end by remember { mutableStateOf(LocalTime.of(18, 0)) }
    var who by remember { mutableStateOf(listOf(EventWho.ALL)) }
    // 0 = ingen gentagelse, 1 = hver uge, 2 = hver 2. uge
    var repeatEvery by remember { mutableIntStateOf(0) }
    var weekdays by remember { mutableStateOf(setOf(initialDate.dayOfWeek)) }
    var until by remember { mutableStateOf<LocalDate?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ny aftale") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Titel") },
                    keyboardOptions = SentenceCaps,
                    singleLine = true,
                )
                OutlinedButton(
                    onClick = {
                        pickDate(context, date) {
                            date = it
                            if (repeatEvery == 0) weekdays = setOf(it.dayOfWeek)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text((if (repeatEvery > 0) "Fra " else "") + Dates.long(date))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Hele dagen", Modifier.weight(1f))
                    Switch(checked = allDay, onCheckedChange = { allDay = it })
                }
                if (!allDay) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                TimePickerDialog(
                                    context,
                                    { _, hour, minute ->
                                        val duration = java.time.Duration.between(start, end)
                                        start = LocalTime.of(hour, minute)
                                        end = if (duration.isNegative || duration.isZero) start.plusHours(1) else start.plus(duration)
                                    },
                                    start.hour,
                                    start.minute,
                                    true,
                                ).show()
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("Fra ${CalendarMath.clock(start)}") }
                        OutlinedButton(
                            onClick = {
                                TimePickerDialog(
                                    context,
                                    { _, hour, minute -> end = LocalTime.of(hour, minute) },
                                    end.hour,
                                    end.minute,
                                    true,
                                ).show()
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("Til ${CalendarMath.clock(end)}") }
                    }
                }

                Text("Gentag", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Muted)
                Segmented(
                    options = listOf("Nej", "Hver uge", "Hver 2. uge"),
                    selected = repeatEvery,
                    onSelect = { repeatEvery = it },
                )
                if (repeatEvery > 0) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DayOfWeek.values().forEach { day ->
                            val on = day in weekdays
                            FilterChip(
                                selected = on,
                                onClick = {
                                    // Mindst én dag skal være valgt.
                                    weekdays = if (on && weekdays.size > 1) weekdays - day else weekdays + day
                                },
                                label = { Text(Dates.dayShort[day.value - 1]) },
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { pickDate(context, until ?: date.plusMonths(3)) { until = it } }) {
                            Text(until?.let { "Til og med ${Dates.short(it)}" } ?: "Ingen slutdato")
                        }
                        if (until != null) {
                            TextButton(onClick = { until = null }) { Text("Fjern") }
                        }
                    }
                }

                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Sted (valgfrit)") },
                    keyboardOptions = SentenceCaps,
                    singleLine = true,
                )
                Text("Hvem", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Muted)
                WhoPicker(profiles, who) { who = it }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        title.trim(),
                        location.trim(),
                        date,
                        if (allDay) null else start,
                        if (allDay) null else end,
                        who,
                        if (repeatEvery > 0) Repeat(weekdays, repeatEvery, until) else null,
                    )
                },
                enabled = title.isNotBlank(),
            ) { Text("Gem") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annullér") }
        },
    )
}

/** Vælg hvem en aftale gælder for: Alle eller enkelte familiemedlemmer. */
@Composable
private fun WhoPicker(profiles: List<Profile>, selected: List<String>, onChange: (List<String>) -> Unit) {
    val isAll = EventWho.isAll(selected)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(
            selected = isAll,
            onClick = { onChange(if (isAll) emptyList() else listOf(EventWho.ALL)) },
            label = { Text(EventWho.ALL) },
        )
        profiles.sortedBy { it.name.lowercase() }.forEach { profile ->
            val chosen = !isAll && selected.any { it.equals(profile.name, ignoreCase = true) }
            FilterChip(
                selected = chosen,
                onClick = {
                    val people = selected.filterNot { EventWho.isAll(listOf(it)) }
                    onChange(
                        if (chosen) people.filterNot { it.equals(profile.name, ignoreCase = true) }
                        else people + profile.name
                    )
                },
                label = { Text(profile.name) },
            )
        }
    }
}

/** "Alle" eller små avatarer for dem, aftalen gælder for. */
@Composable
private fun WhoBadges(who: List<String>?, profiles: List<Profile>, modifier: Modifier = Modifier) {
    if (who.isNullOrEmpty()) return
    if (EventWho.isAll(who)) {
        Box(modifier) { Pill(EventWho.ALL) }
        return
    }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        who.take(4).forEach { name ->
            val profile = profiles.firstOrNull { it.name.equals(name, ignoreCase = true) }
            if (profile != null) Avatar(profile, 26.dp) else Pill(name)
        }
    }
}

@Composable
private fun EventDialog(
    event: CalendarEvent,
    day: LocalDate,
    zone: ZoneId,
    profiles: List<Profile>,
    canEdit: Boolean,
    canOpen: Boolean,
    onSaveWho: (List<String>) -> Unit,
    onMove: (LocalDate) -> Unit,
    onCancelOnce: () -> Unit,
    onCancelRange: (LocalDate, LocalDate) -> Unit,
    onDelete: () -> Unit,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val initial = remember(event) { EventWho.parse(event.description).orEmpty() }
    var who by remember(event) { mutableStateOf(initial) }
    // "delete", "cancel" eller "series", mens der bedes om bekræftelse.
    var confirm by remember(event) { mutableStateOf<String?>(null) }
    var choosingRange by remember(event) { mutableStateOf(false) }
    val notes = EventWho.strip(event.description)
    val dangerColors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(event.title) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    listOf(
                        Dates.long(day).replaceFirstChar { it.uppercase() },
                        CalendarMath.timeLabel(event, day, zone),
                        event.location,
                    ).filter { it.isNotBlank() }.joinToString(" · "),
                    fontSize = 14.sp,
                    color = Muted,
                )
                if (event.recurring) {
                    Text("Fast aftale", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Green)
                }
                if (notes.isNotBlank()) Text(notes, fontSize = 15.sp)
                Text("Hvem", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Muted)
                if (canEdit) {
                    WhoPicker(profiles, who) { who = it }
                    if (event.recurring) {
                        Text("For en fast aftale gælder \"hvem\" alle gange.", fontSize = 12.sp, color = Muted)
                    }
                } else if (initial.isEmpty()) {
                    Text("Ikke angivet", fontSize = 15.sp, color = Muted)
                } else {
                    WhoBadges(initial, profiles)
                }
                if (canEdit) {
                    Text("Ændr", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Muted)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        OutlinedButton(onClick = { pickDate(context, day) { onMove(it) } }) {
                            Text(if (event.recurring) "Flyt denne gang" else "Flyt til anden dato")
                        }
                        if (event.recurring) {
                            OutlinedButton(onClick = { confirm = "cancel" }) { Text("Aflys denne gang") }
                            OutlinedButton(onClick = { choosingRange = true }) { Text("Aflys i en periode") }
                            OutlinedButton(onClick = { confirm = "series" }, colors = dangerColors) {
                                Text("Slet hele serien")
                            }
                        } else {
                            OutlinedButton(onClick = { confirm = "delete" }, colors = dangerColors) { Text("Slet") }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (canEdit && who != initial) {
                TextButton(onClick = { onSaveWho(who) }) { Text("Gem") }
            } else {
                TextButton(onClick = onDismiss) { Text("Luk") }
            }
        },
        dismissButton = {
            if (canOpen) {
                TextButton(onClick = onOpen) { Text("Åbn i Google Kalender") }
            }
        },
    )

    confirm?.let { kind ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = {
                Text(
                    when (kind) {
                        "cancel" -> "Aflys ${Dates.long(day)}?"
                        "series" -> "Slet hele serien?"
                        else -> "Slet aftalen?"
                    }
                )
            },
            text = {
                Text(
                    when (kind) {
                        "cancel" -> "Kun denne gang aflyses. De andre gange står stadig i kalenderen."
                        "series" -> "Alle gange af \"${event.title}\" slettes fra familiekalenderen."
                        else -> "\"${event.title}\" slettes fra familiekalenderen."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    if (kind == "cancel") onCancelOnce() else onDelete()
                }) { Text(if (kind == "cancel") "Aflys" else "Slet") }
            },
            dismissButton = {
                TextButton(onClick = { confirm = null }) { Text("Annullér") }
            },
        )
    }

    if (choosingRange) {
        CancelRangeDialog(
            initialFrom = day,
            onConfirm = { from, to ->
                choosingRange = false
                onCancelRange(from, to)
            },
            onDismiss = { choosingRange = false },
        )
    }
}

/** Aflys alle gange af en fast aftale i en periode, fx en ferie. */
@Composable
private fun CancelRangeDialog(
    initialFrom: LocalDate,
    onConfirm: (LocalDate, LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var from by remember { mutableStateOf(initialFrom) }
    var to by remember { mutableStateOf(initialFrom.plusDays(6)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Aflys i en periode") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Alle gange i perioden aflyses. Resten af serien står stadig i kalenderen.", fontSize = 14.sp, color = Muted)
                OutlinedButton(onClick = { pickDate(context, from) { from = it } }, modifier = Modifier.fillMaxWidth()) {
                    Text("Fra ${Dates.long(from)}")
                }
                OutlinedButton(onClick = { pickDate(context, to) { to = it } }, modifier = Modifier.fillMaxWidth()) {
                    Text("Til og med ${Dates.long(to)}")
                }
                if (to.isBefore(from)) {
                    Text("Slutdatoen ligger før startdatoen.", color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(from, to) }, enabled = !to.isBefore(from)) { Text("Aflys") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annullér") }
        },
    )
}
