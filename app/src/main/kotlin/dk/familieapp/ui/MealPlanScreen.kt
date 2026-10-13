@file:OptIn(ExperimentalMaterial3Api::class)

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dk.familieapp.data.Dates
import dk.familieapp.data.PlannedMeal
import dk.familieapp.data.Recipe
import dk.familieapp.data.onDate
import dk.familieapp.data.KIND_DESSERT
import dk.familieapp.data.KIND_DINNER
import dk.familieapp.data.ROLE_PARENT
import java.time.LocalDate

@Composable
fun MealPlanScreen(vm: AppViewModel, nav: NavController) {
    val meals by vm.meals.collectAsStateWithLifecycle()
    val recipes by vm.recipes.collectAsStateWithLifecycle()
    var weekOffset by rememberSaveable { mutableIntStateOf(0) }

    val today = LocalDate.now()
    val start = Dates.planWeekStart(today).plusWeeks(weekOffset.toLong())

    Column(Modifier.fillMaxSize()) {
        if (vm.mealTab == 0) {
            ScreenHeader("Uge ${Dates.weekNumber(start.plusDays(1))} · ${Dates.weekRange(start)}", "Madplan") {
                IconButton(onClick = { weekOffset -= 1 }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Forrige uge")
                }
                IconButton(onClick = { weekOffset += 1 }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Næste uge")
                }
            }
        } else if (vm.mealTab == 2) {
            ScreenHeader("Ugens tilbud i Bilka, Rema 1000, Netto og Meny", "Forslag") {
                IconButton(onClick = { vm.loadOfferSuggestions(force = true) }, enabled = !vm.offersLoading) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Hent tilbud igen")
                }
            }
        } else {
            ScreenHeader("Familiens favoritter · ${recipes.size} retter", "Opskrifter") {
                FilledIconButton(
                    onClick = { nav.navigate("import") },
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Yellow, contentColor = Ink),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Ny opskrift")
                }
            }
        }
        Segmented(
            options = listOf("Ugeplan", "Opskrifter", "Forslag"),
            selected = vm.mealTab,
            onSelect = { vm.mealTab = it },
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        when (vm.mealTab) {
            0 -> WeekPlan(vm, meals, recipes, start, today, Modifier.weight(1f))
            2 -> OfferSuggestions(vm, nav, Modifier.weight(1f))
            else -> RecipeList(vm, meals, recipes, nav, Modifier.weight(1f))
        }
    }
}

@Composable
private fun WeekPlan(
    vm: AppViewModel,
    meals: List<PlannedMeal>,
    recipes: List<Recipe>,
    start: LocalDate,
    today: LocalDate,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val me by vm.me.collectAsStateWithLifecycle()
    val editable = me?.role == ROLE_PARENT
    // Retten, der rettes, eller en ny ret (uden id) på en dag.
    var editing by remember { mutableStateOf<PlannedMeal?>(null) }

    LazyColumn(
        modifier,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(7) { index ->
            val date = start.plusDays(index.toLong())
            DayCard(
                date = date,
                meals = meals.onDate(date.toString()),
                isToday = date == today,
                editable = editable,
                onEdit = { editing = it },
                onAdd = { editing = PlannedMeal(date = date.toString()) },
            )
        }
        if (editable) item {
            YellowButton(
                text = "Læg ugens ingredienser på indkøbslisten",
                onClick = {
                    val count = vm.addWeekIngredients(start)
                    context.toast(
                        if (count == 0) "Ingen opskrifter med ingredienser på ugens plan"
                        else "$count varer lagt på indkøbslisten"
                    )
                },
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }

    editing?.let { meal ->
        PlanMealDialog(
            date = LocalDate.parse(meal.date),
            existing = meal.takeIf { it.id.isNotBlank() },
            recipes = recipes,
            onSave = { title, recipeId, cook ->
                vm.saveMeal(meal.copy(title = title, recipeId = recipeId, cook = cook))
                editing = null
            },
            onClear = {
                vm.removeMeal(meal)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

/** Én dag på madplanen med alle dagens retter. Tryk på en ret for at rette den. */
@Composable
private fun DayCard(
    date: LocalDate,
    meals: List<PlannedMeal>,
    isToday: Boolean,
    editable: Boolean,
    onEdit: (PlannedMeal) -> Unit,
    onAdd: () -> Unit,
) {
    val background = if (isToday) Green else Color.White
    val textColor = if (isToday) Color.White else Ink
    val subColor = if (isToday) Color(0xFFE6F1EA) else Muted
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(background)
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = if (editable) 0.dp else 8.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(
                Modifier
                    .width(52.dp)
                    .padding(top = 6.dp),
            ) {
                Text(Dates.dayAbbr(date), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = subColor)
                Text("${date.dayOfMonth}.", fontSize = 12.sp, color = subColor)
            }
            Column(Modifier.weight(1f)) {
                if (meals.isEmpty()) {
                    Text("Ikke planlagt", Modifier.padding(vertical = 6.dp), fontSize = 15.sp, color = subColor)
                }
                meals.forEach { meal ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(enabled = editable) { onEdit(meal) }
                            .padding(vertical = 6.dp),
                    ) {
                        Text(meal.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = textColor)
                        if (meal.cook.isNotBlank()) {
                            Text("${meal.cook} laver mad", fontSize = 13.sp, color = subColor)
                        }
                    }
                }
            }
            if (isToday) {
                Box(Modifier.padding(top = 4.dp)) { Pill("I dag", Yellow) }
            }
        }
        if (editable) {
            TextButton(
                onClick = onAdd,
                modifier = Modifier.align(Alignment.End),
                colors = ButtonDefaults.textButtonColors(contentColor = if (isToday) Color.White else Green),
            ) {
                Text(if (meals.isEmpty()) "Tilføj ret" else "+ Tilføj ret", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun RecipeList(
    vm: AppViewModel,
    meals: List<PlannedMeal>,
    recipes: List<Recipe>,
    nav: NavController,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val me by vm.me.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf(KIND_DINNER) }
    var filter by rememberSaveable { mutableIntStateOf(0) }
    var pickFor by remember { mutableStateOf<Recipe?>(null) }

    val todayIso = LocalDate.now().toString()
    // Hvor tit og hvornår hver opskrift har stået på madplanen.
    val cooked = meals.filter { it.recipeId.isNotBlank() && it.date <= todayIso }.groupBy { it.recipeId }
    val matching = recipes.filter { it.kind == kind }.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
    val shown = when (filter) {
        1 -> matching.sortedBy { r -> cooked[r.id]?.maxOf { it.date } ?: "" }
        2 -> matching.filter { it.minutes in 1..30 }.sortedBy { it.minutes }
        else -> matching.sortedBy { it.name.lowercase() }
    }

    LazyColumn(
        modifier,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "kind") {
            Segmented(
                options = listOf("Aftensmad", "Desserter"),
                selected = if (kind == KIND_DESSERT) 1 else 0,
                onSelect = { kind = if (it == 1) KIND_DESSERT else KIND_DINNER },
            )
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Søg i opskrifter") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                ),
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Alle", "Længe siden", "Hurtigt").forEachIndexed { index, label ->
                    FilterChip(
                        selected = filter == index,
                        onClick = { filter = index },
                        label = { Text(label) },
                    )
                }
            }
        }
        if (recipes.isEmpty()) {
            item {
                Text(
                    "Ingen opskrifter endnu. Tryk på plusset for at importere en fra nettet eller skrive den selv.",
                    Modifier.padding(vertical = 12.dp),
                    color = Muted,
                )
            }
        }
        if (recipes.isNotEmpty() && shown.isEmpty() && query.isBlank()) {
            item(key = "none-of-kind") {
                Text(
                    if (kind == KIND_DESSERT) "Ingen desserter endnu." else "Ingen aftensretter endnu.",
                    Modifier.padding(vertical = 12.dp),
                    color = Muted,
                )
            }
        }
        items(shown, key = { it.id }) { recipe ->
            val history = cooked[recipe.id].orEmpty()
            val meta = buildList {
                if (history.isEmpty()) {
                    add("Ikke lavet endnu")
                } else {
                    add("Lavet ${history.size} ${if (history.size == 1) "gang" else "gange"}")
                    add("sidst " + lastLabel(history.maxOf { it.date }, todayIso))
                }
                if (recipe.minutes > 0) add("${recipe.minutes} min")
            }.joinToString(" · ")
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White)
                    .clickable { nav.navigate("recipe/${recipe.id}") }
                    .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(recipe.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(meta, fontSize = 13.sp, color = Muted)
                }
                if (me?.role == ROLE_PARENT) FilledTonalIconButton(onClick = { pickFor = recipe }) {
                    Icon(Icons.Filled.Add, contentDescription = "Læg ${recipe.name} på madplanen")
                }
            }
        }
    }

    pickFor?.let { recipe ->
        DayPickDialog(
            meals = meals,
            onPick = { date ->
                vm.planRecipe(date, recipe)
                pickFor = null
                context.toast("${recipe.name} lagt på ${Dates.long(date)}")
            },
            onDismiss = { pickFor = null },
        )
    }
}

private fun lastLabel(isoDate: String, todayIso: String): String =
    if (isoDate == todayIso) "i dag" else Dates.short(LocalDate.parse(isoDate))

/** Vælg ret til en dag: skriv selv eller vælg fra opskrifterne. */
@Composable
fun PlanMealDialog(
    date: LocalDate,
    existing: PlannedMeal?,
    recipes: List<Recipe>,
    onSave: (title: String, recipeId: String, cook: String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf(existing?.title.orEmpty()) }
    var recipeId by remember { mutableStateOf(existing?.recipeId.orEmpty()) }
    var cook by remember { mutableStateOf(existing?.cook.orEmpty()) }
    val suggestions = recipes
        .filter { title.isBlank() || it.name.contains(title, ignoreCase = true) }
        .sortedBy { it.name.lowercase() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Dates.long(date).replaceFirstChar { it.uppercase() }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        recipeId = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Ret") },
                    keyboardOptions = SentenceCaps,
                    singleLine = true,
                )
                OutlinedTextField(
                    value = cook,
                    onValueChange = { cook = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Hvem laver mad? (valgfrit)") },
                    keyboardOptions = SentenceCaps,
                    singleLine = true,
                )
                if (suggestions.isNotEmpty()) {
                    Text("Fra opskrifterne", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Muted)
                    LazyColumn(Modifier.heightIn(max = 190.dp)) {
                        items(suggestions, key = { it.id }) { recipe ->
                            Text(
                                recipe.name,
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        title = recipe.name
                                        recipeId = recipe.id
                                    }
                                    .padding(vertical = 12.dp),
                                fontWeight = if (recipe.id == recipeId) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(title.trim(), recipeId, cook.trim()) }, enabled = title.isNotBlank()) {
                Text("Gem")
            }
        },
        dismissButton = {
            Row {
                if (existing != null) {
                    TextButton(onClick = onClear) { Text("Fjern") }
                }
                TextButton(onClick = onDismiss) { Text("Annullér") }
            }
        },
    )
}

/** Vælg en af de næste 14 dage til en opskrift. */
@Composable
fun DayPickDialog(meals: List<PlannedMeal>, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val today = LocalDate.now()
    val days = (0L..13L).map { today.plusDays(it) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Læg på madplanen") },
        text = {
            LazyColumn(Modifier.heightIn(max = 380.dp)) {
                items(days, key = { it.toString() }) { date ->
                    val planned = meals.onDate(date.toString())
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(date) }
                            .heightIn(min = 48.dp)
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${Dates.dayAbbr(date)} ${Dates.short(date)}",
                            Modifier.width(110.dp),
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            if (planned.isEmpty()) "Ledig" else planned.joinToString(" · ") { it.title },
                            Modifier.weight(1f),
                            fontSize = 14.sp,
                            color = if (planned.isEmpty()) Green else Muted,
                            maxLines = 1,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Annullér") }
        },
    )
}
