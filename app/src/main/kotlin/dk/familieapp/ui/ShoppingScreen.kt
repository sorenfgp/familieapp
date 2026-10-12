package dk.familieapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dk.familieapp.data.Categories
import dk.familieapp.data.Profile
import dk.familieapp.data.ROLE_PARENT
import dk.familieapp.data.RecipeImporter
import dk.familieapp.data.ShoppingItem
import dk.familieapp.data.ShoppingMerge
import dk.familieapp.data.Staple

private val stapleGroups = listOf("Morgenmad", "Madpakker", "Andet")

@Composable
fun ShoppingScreen(vm: AppViewModel) {
    val items by vm.shopping.collectAsStateWithLifecycle()
    val staples by vm.staples.collectAsStateWithLifecycle()
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val me by vm.me.collectAsStateWithLifecycle()
    val canEdit = me?.role == ROLE_PARENT
    val open = items.filter { !it.checked }.sortedBy { it.createdAt }
    val done = items.filter { it.checked }.sortedBy { it.createdAt }

    Column(Modifier.fillMaxSize()) {
        if (vm.shoppingTab == 0) {
            ScreenHeader("${open.size} mangler · ${done.size} i kurven", "Indkøbsliste")
        } else {
            ScreenHeader("Købes hver uge · ${staples.size} varer", "Faste varer")
        }
        Segmented(
            options = listOf("Denne uge", "Faste varer"),
            selected = vm.shoppingTab,
            onSelect = { vm.shoppingTab = it },
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        if (!canEdit) {
            Text(
                "Kun forældre kan rette indkøbslisten.",
                Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp),
                fontSize = 13.sp,
                color = Muted,
            )
        }
        if (vm.shoppingTab == 0) {
            WeekList(vm, open, done, profiles.orEmpty(), canEdit, Modifier.weight(1f))
        } else {
            StapleList(vm, staples, canEdit, Modifier.weight(1f))
        }
    }
}

// ---------- Ugens indkøbsliste ----------

@Composable
private fun WeekList(
    vm: AppViewModel,
    open: List<ShoppingItem>,
    done: List<ShoppingItem>,
    profiles: List<Profile>,
    canEdit: Boolean,
    modifier: Modifier,
) {
    var text by rememberSaveable { mutableStateOf("") }

    fun add() {
        if (text.isBlank()) return
        // "2 l mælk" bliver til vare "Mælk" og mængde "2 l".
        val parsed = RecipeImporter.splitIngredient(text)
        vm.addShoppingItem(parsed.name, parsed.amount)
        text = ""
    }

    val groups = Categories.order.mapNotNull { category ->
        val inCategory = open.filter { item ->
            val known = if (item.category in Categories.order) item.category else "Andet"
            known == category
        }
        if (inCategory.isEmpty()) null else category to inCategory
    }

    LazyColumn(
        modifier,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (canEdit) {
            item(key = "add") {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Tilføj vare, fx 2 l mælk") },
                    singleLine = true,
                    shape = RoundedCornerShape(26.dp),
                    trailingIcon = {
                        IconButton(onClick = { add() }) {
                            Icon(Icons.Filled.Add, contentDescription = "Tilføj vare")
                        }
                    },
                    keyboardOptions = SentenceCaps.copy(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { add() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                    ),
                )
            }
        }
        if (open.isEmpty() && done.isEmpty()) {
            item(key = "empty") {
                Text(
                    "Listen er tom.",
                    Modifier.padding(vertical = 12.dp),
                    color = Muted,
                )
            }
        }
        groups.forEach { (category, list) ->
            item(key = "label-$category") { SectionLabel(category) }
            item(key = "card-$category") {
                WhiteCard {
                    list.forEach { entry ->
                        ItemRow(entry, profiles, canEdit, onToggle = { vm.toggleShoppingItem(entry) })
                    }
                }
            }
        }
        if (done.isNotEmpty()) {
            item(key = "done-label") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("I kurven", Modifier.weight(1f))
                    if (canEdit) {
                        TextButton(onClick = { vm.clearCheckedItems() }) {
                            Text("Ryd købte", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            item(key = "done") {
                Column {
                    done.forEach { entry ->
                        ItemRow(entry, profiles, canEdit, onToggle = { vm.toggleShoppingItem(entry) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemRow(item: ShoppingItem, profiles: List<Profile>, canEdit: Boolean, onToggle: () -> Unit) {
    val sources = ShoppingMerge.shortSources(item.allSources())
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = canEdit, onClick = onToggle)
            .heightIn(min = 48.dp)
            .padding(end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Checkbox(
            checked = item.checked,
            onCheckedChange = null,
            enabled = canEdit,
            modifier = Modifier.padding(start = 14.dp, end = 6.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                item.name,
                fontSize = 16.sp,
                color = if (item.checked) Muted else Ink,
                textDecoration = if (item.checked) TextDecoration.LineThrough else null,
            )
            if (sources.isNotBlank() && !item.checked) {
                Text(sources, fontSize = 12.sp, color = Muted, maxLines = 1)
            }
        }
        if (item.amount.isNotBlank()) {
            Text(item.amount, fontSize = 14.sp, color = Muted, maxLines = 1)
        }
        if (item.checked && item.checkedBy.isNotBlank()) {
            Text(profiles.nameOf(item.checkedBy), fontSize = 13.sp, color = Muted)
        }
    }
}

// ---------- Faste varer ----------

@Composable
private fun StapleList(vm: AppViewModel, staples: List<Staple>, canEdit: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    var editing by remember { mutableStateOf<Staple?>(null) }

    val groupNames = (stapleGroups + staples.map { it.group }).distinct()

    LazyColumn(
        modifier,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (staples.isEmpty()) {
            item(key = "empty") {
                Text(
                    "Her samler I det, der købes hver uge, fx morgenmad og ting til madpakker.",
                    Modifier.padding(vertical = 12.dp),
                    color = Muted,
                )
            }
        }
        groupNames.forEach { group ->
            val inGroup = staples.filter { it.group == group }.sortedBy { it.name.lowercase() }
            if (inGroup.isNotEmpty()) {
                item(key = "label-$group") { SectionLabel(group) }
                item(key = "card-$group") {
                    WhiteCard {
                        inGroup.forEach { staple ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = canEdit) { editing = staple }
                                    .heightIn(min = 48.dp)
                                    .padding(start = 16.dp, end = if (canEdit) 0.dp else 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(staple.name, Modifier.weight(1f), fontSize = 16.sp)
                                Text(staple.amount, fontSize = 14.sp, color = Muted)
                                if (canEdit) {
                                    IconButton(onClick = { vm.deleteStaple(staple.id) }) {
                                        Icon(
                                            Icons.Filled.Close,
                                            contentDescription = "Fjern ${staple.name} fra faste varer",
                                            tint = Muted,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (canEdit) {
            item(key = "actions") {
                Column(
                    Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { editing = Staple() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp),
                    ) {
                        Text("Tilføj fast vare", fontWeight = FontWeight.Bold)
                    }
                    YellowButton(
                        text = "Læg alle ${staples.size} på ugens liste",
                        onClick = {
                            val count = vm.addStaplesToShopping()
                            context.toast(
                                if (count == 0) "De faste varer står allerede på listen"
                                else "$count varer lagt på ugens liste"
                            )
                        },
                        enabled = staples.isNotEmpty(),
                    )
                }
            }
        }
    }

    editing?.let { staple ->
        StapleDialog(
            staple = staple,
            onSave = { updated ->
                vm.saveStaple(updated)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun StapleDialog(staple: Staple, onSave: (Staple) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(staple.name) }
    var amount by remember { mutableStateOf(staple.amount) }
    var group by remember { mutableStateOf(if (staple.group in stapleGroups) staple.group else "Andet") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (staple.id.isBlank()) "Ny fast vare" else "Ret fast vare") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Vare") },
                    keyboardOptions = SentenceCaps,
                    singleLine = true,
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Mængde (valgfrit)") },
                    singleLine = true,
                )
                Segmented(
                    options = stapleGroups,
                    selected = stapleGroups.indexOf(group),
                    onSelect = { group = stapleGroups[it] },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(staple.copy(name = name.trim(), amount = amount.trim(), group = group)) },
                enabled = name.isNotBlank(),
            ) {
                Text("Gem")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annullér") }
        },
    )
}
