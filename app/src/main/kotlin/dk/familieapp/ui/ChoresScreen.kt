@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package dk.familieapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dk.familieapp.data.CHORE_APPROVED
import dk.familieapp.data.CHORE_DONE
import dk.familieapp.data.CHORE_OPEN
import dk.familieapp.data.ChoreEntry
import dk.familieapp.data.ChorePlan
import dk.familieapp.data.ChoreTemplate
import dk.familieapp.data.Dates
import dk.familieapp.data.Profile
import dk.familieapp.data.ROLE_CHILD
import dk.familieapp.data.ROLE_PARENT
import java.time.LocalDate

@Composable
fun ChoresScreen(vm: AppViewModel, nav: NavController) {
    val me by vm.me.collectAsStateWithLifecycle()
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val templates by vm.choreTemplates.collectAsStateWithLifecycle()
    val entries by vm.chores.collectAsStateWithLifecycle()
    val isParent = me?.role == ROLE_PARENT

    var weekOffset by rememberSaveable { mutableIntStateOf(0) }
    val today = LocalDate.now()
    val start = Dates.weekStart(today).plusWeeks(weekOffset.toLong())
    val week = ChorePlan.week(start, templates, entries)
    val showWeek = !isParent || vm.choreTab == 0

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            if (showWeek) "Uge ${Dates.weekNumber(start)} · ${Dates.weekRange(start)}" else "Gentages hver uge",
            if (isParent) "Pligter" else "Mine pligter",
        ) {
            if (showWeek) {
                IconButton(onClick = { weekOffset -= 1 }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Forrige uge")
                }
                IconButton(onClick = { weekOffset += 1 }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Næste uge")
                }
            }
            ProfileButton(vm, nav)
        }
        if (isParent) {
            Segmented(
                options = listOf("Ugen", "Faste pligter"),
                selected = vm.choreTab,
                onSelect = { vm.choreTab = it },
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            if (vm.choreTab == 0) {
                ParentWeek(vm, week, profiles.orEmpty(), start, today, Modifier.weight(1f))
            } else {
                TemplateList(vm, templates, profiles.orEmpty(), Modifier.weight(1f))
            }
        } else {
            ChildWeek(vm, week.filter { it.childId == me?.id }, profiles.orEmpty(), me, start, today, Modifier.weight(1f))
        }
    }
}

private fun dayLabel(date: LocalDate, today: LocalDate): String {
    val label = Dates.long(date).replaceFirstChar { it.uppercase() }
    return if (date == today) "I dag · $label" else label
}

// ---------- Forældrenes uge ----------

@Composable
private fun ParentWeek(
    vm: AppViewModel,
    week: List<ChoreEntry>,
    profiles: List<Profile>,
    start: LocalDate,
    today: LocalDate,
    modifier: Modifier,
) {
    val me by vm.me.collectAsStateWithLifecycle()
    val children = profiles.filter { it.role == ROLE_CHILD }.sortedBy { it.name.lowercase() }
    val pending = week.filter { it.status == CHORE_DONE }
    var addingAdHoc by remember { mutableStateOf(false) }

    LazyColumn(
        modifier,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (children.isEmpty()) {
            item(key = "no-children") {
                Text(
                    "Opret en profil til hvert barn: tryk på din avatar øverst og vælg Ny profil.",
                    Modifier.padding(vertical = 8.dp),
                    color = Muted,
                )
            }
        }
        if (pending.isNotEmpty()) {
            item(key = "pending-label") { SectionLabel("Venter på bekræftelse (${pending.size})") }
            items(pending, key = { "pending-" + it.id }) { entry ->
                ChoreRow(vm, entry, profiles, me, showDate = true)
            }
        }
        for (offset in 0L..6L) {
            val day = start.plusDays(offset)
            val dayEntries = week.filter { it.date == day.toString() && it.status != CHORE_DONE }
            if (dayEntries.isNotEmpty()) {
                item(key = "day-$day") { SectionLabel(dayLabel(day, today)) }
                items(dayEntries, key = { it.id }) { entry ->
                    ChoreRow(vm, entry, profiles, me, showDate = false)
                }
            }
        }
        if (week.isEmpty() && children.isNotEmpty()) {
            item(key = "empty") {
                Text(
                    "Ingen pligter i denne uge. Opret faste pligter under Faste pligter eller en ad hoc-pligt herunder.",
                    Modifier.padding(vertical = 8.dp),
                    color = Muted,
                )
            }
        }
        if (children.isNotEmpty()) {
            item(key = "add") {
                OutlinedButton(
                    onClick = { addingAdHoc = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .heightIn(min = 52.dp),
                ) {
                    Text("+ Ad hoc-pligt", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (addingAdHoc) {
        AdHocDialog(
            children = children,
            days = (0L..6L).map { start.plusDays(it) },
            initialDay = if (today in start..start.plusDays(6)) today else start,
            onSave = { title, childId, date, reward ->
                vm.addAdHocChore(title, childId, date, reward)
                addingAdHoc = false
            },
            onDismiss = { addingAdHoc = false },
        )
    }
}

// ---------- Barnets uge ----------

@Composable
private fun ChildWeek(
    vm: AppViewModel,
    mine: List<ChoreEntry>,
    profiles: List<Profile>,
    me: Profile?,
    start: LocalDate,
    today: LocalDate,
    modifier: Modifier,
) {
    val earned = mine.filter { it.status == CHORE_APPROVED && it.reward.isNotBlank() }

    LazyColumn(
        modifier,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (earned.isNotEmpty()) {
            item(key = "earned") {
                Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = Green, contentColor = Color.White) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Optjent i denne uge", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFCFE5D8))
                        earned.forEach { Text(it.reward, fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }
                    }
                }
            }
        }
        if (mine.isEmpty()) {
            item(key = "empty") {
                Text("Ingen pligter i denne uge.", Modifier.padding(vertical = 8.dp), color = Muted)
            }
        }
        for (offset in 0L..6L) {
            val day = start.plusDays(offset)
            val dayEntries = mine.filter { it.date == day.toString() }
            if (dayEntries.isNotEmpty()) {
                item(key = "day-$day") { SectionLabel(dayLabel(day, today)) }
                items(dayEntries, key = { it.id }) { entry ->
                    ChoreRow(vm, entry, profiles, me, showDate = false)
                }
            }
        }
    }
}

// ---------- Én pligt ----------

@Composable
private fun ChoreRow(vm: AppViewModel, entry: ChoreEntry, profiles: List<Profile>, me: Profile?, showDate: Boolean) {
    val isParent = me?.role == ROLE_PARENT
    val child = profiles.firstOrNull { it.id == entry.childId }
    val childName = child?.name ?: "?"
    val status = when (entry.status) {
        CHORE_DONE -> "Udført af ${profiles.nameOf(entry.doneBy)} · venter på bekræftelse"
        CHORE_APPROVED -> if (entry.doneBy.isNotBlank()) {
            "Udført af ${profiles.nameOf(entry.doneBy)} · bekræftet af ${profiles.nameOf(entry.approvedBy)}"
        } else {
            "Bekræftet af ${profiles.nameOf(entry.approvedBy)}"
        }
        else -> if (isParent) "$childName · ikke udført" else "Ikke udført endnu"
    }
    val datePrefix = if (showDate) Dates.dayAbbr(LocalDate.parse(entry.date)) + " · " else ""

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Avatar(child, 36.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(datePrefix + entry.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            if (entry.reward.isNotBlank()) {
                Text("Belønning: ${entry.reward}", fontSize = 14.sp, color = Green, fontWeight = FontWeight.SemiBold)
            }
            Text(status, fontSize = 13.sp, color = Muted)
        }
        if (isParent) {
            when (entry.status) {
                CHORE_DONE -> Button(onClick = { vm.approveChore(entry) }) { Text("Bekræft") }
                CHORE_APPROVED -> TextButton(onClick = { vm.unapproveChore(entry) }) { Text("Fortryd") }
                else -> {
                    TextButton(onClick = { vm.approveChore(entry) }) { Text("Bekræft") }
                    if (entry.templateId.isBlank()) {
                        IconButton(onClick = { vm.deleteChore(entry.id) }) {
                            Icon(Icons.Filled.Close, contentDescription = "Slet ${entry.title}", tint = Muted)
                        }
                    }
                }
            }
        } else if (entry.childId == me?.id) {
            when (entry.status) {
                CHORE_OPEN -> Button(
                    onClick = { vm.markChoreDone(entry) },
                    colors = ButtonDefaults.buttonColors(containerColor = Yellow, contentColor = Ink),
                ) { Text("Udført", fontWeight = FontWeight.Bold) }
                CHORE_DONE -> TextButton(onClick = { vm.undoChoreDone(entry) }) { Text("Fortryd") }
                else -> Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = "Bekræftet",
                    tint = Green,
                    modifier = Modifier.padding(10.dp),
                )
            }
        }
    }
}

// ---------- Faste pligter ----------

@Composable
private fun TemplateList(vm: AppViewModel, templates: List<ChoreTemplate>, profiles: List<Profile>, modifier: Modifier) {
    val children = profiles.filter { it.role == ROLE_CHILD }.sortedBy { it.name.lowercase() }
    var editing by remember { mutableStateOf<ChoreTemplate?>(null) }
    val groups = children.map { it.id to it.name } + listOf("" to "Uden barn")

    LazyColumn(
        modifier,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (templates.isEmpty()) {
            item(key = "empty") {
                Text(
                    "Faste pligter gentages hver uge på de valgte dage, fx \"Tøm opvaskemaskinen\" mandag, onsdag og fredag.",
                    Modifier.padding(vertical = 8.dp),
                    color = Muted,
                )
            }
        }
        groups.forEach { (childId, name) ->
            val list = templates.filter { t ->
                if (childId.isBlank()) children.none { it.id == t.childId } else t.childId == childId
            }.sortedBy { it.title.lowercase() }
            if (list.isNotEmpty()) {
                item(key = "label-$childId") { SectionLabel(name) }
                item(key = "card-$childId") {
                    WhiteCard {
                        list.forEach { template ->
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { editing = template }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                            ) {
                                Text(template.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    template.weekdays.sorted().joinToString(", ") { Dates.dayShort[it - 1] }
                                        .ifBlank { "Ingen dage valgt" },
                                    fontSize = 14.sp,
                                    color = Muted,
                                )
                                if (template.reward.isNotBlank()) {
                                    Text("Belønning: ${template.reward}", fontSize = 14.sp, color = Green)
                                }
                            }
                        }
                    }
                }
            }
        }
        item(key = "add") {
            OutlinedButton(
                onClick = { editing = ChoreTemplate(childId = children.firstOrNull()?.id.orEmpty()) },
                enabled = children.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .heightIn(min = 52.dp),
            ) {
                Text("+ Fast pligt", fontWeight = FontWeight.Bold)
            }
        }
    }

    editing?.let { template ->
        TemplateDialog(
            template = template,
            children = children,
            onSave = {
                vm.saveChoreTemplate(it)
                editing = null
            },
            onDelete = {
                vm.deleteChoreTemplate(template.id)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun ChildPicker(children: List<Profile>, selected: String, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        children.forEach { child ->
            FilterChip(
                selected = child.id == selected,
                onClick = { onSelect(child.id) },
                label = { Text(child.name) },
            )
        }
    }
}

@Composable
private fun TemplateDialog(
    template: ChoreTemplate,
    children: List<Profile>,
    onSave: (ChoreTemplate) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf(template.title) }
    var childId by remember { mutableStateOf(template.childId) }
    var weekdays by remember { mutableStateOf(template.weekdays.toSet()) }
    var reward by remember { mutableStateOf(template.reward) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (template.id.isBlank()) "Ny fast pligt" else "Ret fast pligt") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Pligt") },
                    keyboardOptions = SentenceCaps,
                    singleLine = true,
                )
                Text("Hvem", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Muted)
                ChildPicker(children, childId) { childId = it }
                Text("Hvilke dage", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Muted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Dates.dayShort.forEachIndexed { index, label ->
                        val day = index + 1
                        FilterChip(
                            selected = day in weekdays,
                            onClick = { weekdays = if (day in weekdays) weekdays - day else weekdays + day },
                            label = { Text(label) },
                        )
                    }
                }
                OutlinedTextField(
                    value = reward,
                    onValueChange = { reward = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Belønning (valgfri)") },
                    placeholder = { Text("Fx 30 min skærmtid") },
                    keyboardOptions = SentenceCaps,
                    singleLine = true,
                )
                if (template.id.isNotBlank()) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text("Slet fast pligt") }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        template.copy(
                            title = title.trim(),
                            childId = childId,
                            weekdays = weekdays.sorted(),
                            reward = reward.trim(),
                        )
                    )
                },
                enabled = title.isNotBlank() && childId.isNotBlank() && weekdays.isNotEmpty(),
            ) { Text("Gem") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annullér") }
        },
    )
}

@Composable
private fun AdHocDialog(
    children: List<Profile>,
    days: List<LocalDate>,
    initialDay: LocalDate,
    onSave: (title: String, childId: String, date: LocalDate, reward: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var childId by remember { mutableStateOf(children.firstOrNull()?.id.orEmpty()) }
    var date by remember { mutableStateOf(initialDay) }
    var reward by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ad hoc-pligt") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Pligt") },
                    keyboardOptions = SentenceCaps,
                    singleLine = true,
                )
                Text("Hvem", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Muted)
                ChildPicker(children, childId) { childId = it }
                Text("Hvornår", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Muted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    days.forEach { day ->
                        FilterChip(
                            selected = day == date,
                            onClick = { date = day },
                            label = { Text("${Dates.dayAbbr(day)} ${day.dayOfMonth}.") },
                        )
                    }
                }
                OutlinedTextField(
                    value = reward,
                    onValueChange = { reward = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Belønning (valgfri)") },
                    placeholder = { Text("Fx 20 kr.") },
                    keyboardOptions = SentenceCaps,
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(title, childId, date, reward) },
                enabled = title.isNotBlank() && childId.isNotBlank(),
            ) { Text("Gem") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annullér") }
        },
    )
}
