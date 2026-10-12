package dk.familieapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dk.familieapp.data.Dates
import dk.familieapp.data.Ingredient
import dk.familieapp.data.NoRecipeFoundException
import dk.familieapp.data.Recipe
import dk.familieapp.data.RecipeImporter
import dk.familieapp.data.ROLE_PARENT
import kotlinx.coroutines.launch
import java.time.LocalDate

// ---------- Vis opskrift ----------

@Composable
fun RecipeDetailScreen(vm: AppViewModel, id: String, nav: NavController) {
    val recipes by vm.recipes.collectAsStateWithLifecycle()
    val meals by vm.meals.collectAsStateWithLifecycle()
    val recipe = recipes.firstOrNull { it.id == id }
    val me by vm.me.collectAsStateWithLifecycle()
    val isParent = me?.role == ROLE_PARENT

    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var skipped by remember { mutableStateOf(setOf<Int>()) }
    var picking by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        BackBar("Opskrifter", onBack = { nav.popBackStack() }) {
            if (recipe != null) {
                TextButton(onClick = { nav.navigate("edit?id=$id") }) {
                    Text("Rediger", fontWeight = FontWeight.Bold)
                }
            }
        }
        if (recipe == null) {
            Text("Opskriften findes ikke.", Modifier.padding(20.dp), color = Muted)
        } else {
            val todayIso = LocalDate.now().toString()
            val history = meals.filter { it.recipeId == recipe.id && it.date <= todayIso }
            val meta = buildList {
                if (recipe.minutes > 0) add("${recipe.minutes} min")
                add("${recipe.servings} personer")
                if (history.isNotEmpty()) {
                    val last = history.maxOf { it.date }
                    val lastText = if (last == todayIso) "i dag" else Dates.short(LocalDate.parse(last))
                    add("lavet ${history.size} ${if (history.size == 1) "gang" else "gange"}, sidst $lastText")
                }
            }.joinToString(" · ")

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(recipe.name, fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold)
                Text(meta, fontSize = 14.sp, color = Muted)
                if (isParent) Button(
                    onClick = { picking = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp),
                ) {
                    Text("Læg på madplanen", fontWeight = FontWeight.Bold)
                }
                Segmented(listOf("Ingredienser", "Fremgangsmåde"), tab, { tab = it })

                if (tab == 0) {
                    val chosen = recipe.ingredients.filterIndexed { index, _ -> index !in skipped }
                    if (recipe.ingredients.isEmpty()) {
                        Text("Ingen ingredienser endnu. Tryk Rediger for at tilføje dem.", color = Muted)
                    } else {
                        WhiteCard {
                            recipe.ingredients.forEachIndexed { index, ingredient ->
                                val included = index !in skipped
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { skipped = if (included) skipped + index else skipped - index }
                                        .heightIn(min = 48.dp)
                                        .padding(end = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(
                                        checked = included,
                                        onCheckedChange = null,
                                        modifier = Modifier.padding(horizontal = 14.dp),
                                    )
                                    Text(
                                        ingredient.name,
                                        Modifier.weight(1f),
                                        fontSize = 16.sp,
                                        color = if (included) Ink else Muted,
                                    )
                                    Text(
                                        if (included) ingredient.amount else "Har vi hjemme",
                                        fontSize = 14.sp,
                                        color = Muted,
                                    )
                                }
                            }
                        }
                        if (isParent) YellowButton(
                            text = "Læg ${chosen.size} ingredienser på indkøbslisten",
                            onClick = {
                                val count = vm.addIngredients(recipe.name, chosen)
                                context.toast("$count varer lagt på indkøbslisten")
                            },
                            enabled = chosen.isNotEmpty(),
                        )
                    }
                } else {
                    if (recipe.steps.isEmpty()) {
                        Text("Ingen fremgangsmåde endnu. Tryk Rediger for at tilføje trin.", color = Muted)
                    } else {
                        WhiteCard {
                            recipe.steps.forEachIndexed { index, step ->
                                Row(
                                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    StepNumber(index + 1)
                                    Text(step, Modifier.weight(1f), fontSize = 15.sp, lineHeight = 21.sp)
                                }
                            }
                        }
                    }
                    if (recipe.sourceUrl.isNotBlank()) {
                        TextButton(onClick = { runCatching { uriHandler.openUri(recipe.sourceUrl) } }) {
                            Text("Åbn original", fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    if (picking && recipe != null) {
        DayPickDialog(
            meals = meals,
            onPick = { date ->
                vm.planRecipe(date, recipe)
                picking = false
                context.toast("${recipe.name} lagt på ${Dates.long(date)}")
            },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun StepNumber(number: Int) {
    Box(
        Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(Mint),
        contentAlignment = Alignment.Center,
    ) {
        Text("$number", fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

// ---------- Skriv eller ret opskrift ----------

@Composable
fun RecipeEditScreen(vm: AppViewModel, id: String, nav: NavController) {
    val recipes by vm.recipes.collectAsStateWithLifecycle()
    val isNew = id.isBlank()
    val source: Recipe? = if (isNew) (vm.draft ?: Recipe()) else recipes.firstOrNull { it.id == id }

    if (source == null) {
        Column(Modifier.fillMaxSize()) {
            BackBar("Annullér", onBack = { nav.popBackStack() })
            Text("Henter opskrift …", Modifier.padding(20.dp), color = Muted)
        }
        return
    }

    RecipeForm(
        initial = source,
        isNew = isNew,
        onCancel = {
            vm.draft = null
            nav.popBackStack()
        },
        onSave = { recipe ->
            val savedId = vm.saveRecipe(recipe)
            vm.draft = null
            vm.mealTab = 1
            if (isNew) {
                nav.navigate("recipe/$savedId") {
                    popUpTo("edit?id={id}") { inclusive = true }
                }
            } else {
                nav.popBackStack()
            }
        },
        onDelete = {
            vm.deleteRecipe(id)
            nav.popBackStack("mealplan", inclusive = false)
        },
    )
}

@Composable
private fun RecipeForm(
    initial: Recipe,
    isNew: Boolean,
    onCancel: () -> Unit,
    onSave: (Recipe) -> Unit,
    onDelete: () -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var minutes by remember { mutableStateOf(if (initial.minutes > 0) initial.minutes.toString() else "") }
    var servings by remember { mutableStateOf(initial.servings.toString()) }
    val ingredients = remember { mutableStateListOf<Ingredient>().apply { addAll(initial.ingredients) } }
    val steps = remember { mutableStateListOf<String>().apply { addAll(initial.steps) } }
    var confirmDelete by remember { mutableStateOf(false) }

    val heading = when {
        !isNew -> "Ret opskrift"
        initial.name.isNotBlank() -> "Gennemse opskrift"
        else -> "Skriv opskrift"
    }

    Column(Modifier.fillMaxSize()) {
        BackBar("Annullér", onBack = onCancel) {
            Button(
                onClick = {
                    onSave(
                        initial.copy(
                            name = name.trim(),
                            minutes = minutes.toIntOrNull() ?: 0,
                            servings = servings.toIntOrNull() ?: 4,
                            ingredients = ingredients
                                .filter { it.name.isNotBlank() }
                                .map { Ingredient(it.name.trim(), it.amount.trim()) },
                            steps = steps.map { it.trim() }.filter { it.isNotBlank() },
                        )
                    )
                },
                enabled = name.isNotBlank(),
            ) {
                Text("Gem", fontWeight = FontWeight.Bold)
            }
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(heading, fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Navn") },
                keyboardOptions = SentenceCaps,
                singleLine = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = minutes,
                    onValueChange = { text -> minutes = text.filter { it.isDigit() }.take(4) },
                    modifier = Modifier.weight(1f),
                    label = { Text("Tid i minutter") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    value = servings,
                    onValueChange = { text -> servings = text.filter { it.isDigit() }.take(2) },
                    modifier = Modifier.weight(1f),
                    label = { Text("Personer") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }

            SectionLabel("Ingredienser")
            ingredients.forEachIndexed { index, ingredient ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    OutlinedTextField(
                        value = ingredient.name,
                        onValueChange = { ingredients[index] = ingredient.copy(name = it) },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Vare") },
                        keyboardOptions = SentenceCaps,
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = ingredient.amount,
                        onValueChange = { ingredients[index] = ingredient.copy(amount = it) },
                        modifier = Modifier.width(108.dp),
                        placeholder = { Text("Mængde") },
                        singleLine = true,
                    )
                    IconButton(onClick = { ingredients.removeAt(index) }) {
                        Icon(Icons.Filled.Close, contentDescription = "Fjern ingrediens ${index + 1}")
                    }
                }
            }
            TextButton(onClick = { ingredients.add(Ingredient()) }) {
                Text("+ Tilføj ingrediens", fontWeight = FontWeight.Bold)
            }

            SectionLabel("Fremgangsmåde")
            steps.forEachIndexed { index, step ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.padding(top = 14.dp)) { StepNumber(index + 1) }
                    OutlinedTextField(
                        value = step,
                        onValueChange = { steps[index] = it },
                        modifier = Modifier.weight(1f),
                        minLines = 2,
                        keyboardOptions = SentenceCaps,
                    )
                    IconButton(onClick = { steps.removeAt(index) }) {
                        Icon(Icons.Filled.Close, contentDescription = "Fjern trin ${index + 1}")
                    }
                }
            }
            TextButton(onClick = { steps.add("") }) {
                Text("+ Tilføj trin", fontWeight = FontWeight.Bold)
            }

            if (!isNew) {
                TextButton(
                    onClick = { confirmDelete = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Slet opskrift")
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Slet opskriften?") },
            text = { Text("Den forsvinder fra kartoteket for hele familien. Madplanen beholder rettens navn.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("Slet") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Annullér") }
            },
        )
    }
}

// ---------- Ny opskrift: importér fra link ----------

@Composable
fun ImportScreen(vm: AppViewModel, initialUrl: String, nav: NavController) {
    var url by rememberSaveable { mutableStateOf(initialUrl) }
    var loading by remember { mutableStateOf(false) }
    var found by remember { mutableStateOf<Recipe?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun openForm(recipe: Recipe) {
        vm.draft = recipe
        nav.navigate("edit") {
            popUpTo("import?url={url}") { inclusive = true }
        }
    }

    fun fetch() {
        if (url.isBlank() || loading) return
        loading = true
        error = null
        found = null
        scope.launch {
            try {
                found = RecipeImporter.fetch(url)
            } catch (e: NoRecipeFoundException) {
                error = "Siden har ikke opskriften i et format, appen kan læse. Du kan skrive den selv."
            } catch (e: Exception) {
                error = "Siden kunne ikke hentes. Tjek linket og internetforbindelsen."
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        if (initialUrl.isNotBlank()) fetch()
    }

    Column(Modifier.fillMaxSize()) {
        BackBar("Annullér", onBack = { nav.popBackStack() })
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Ny opskrift", fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold)
            Segmented(
                options = listOf("Importér fra link", "Skriv selv"),
                selected = 0,
                onSelect = { if (it == 1) openForm(Recipe()) },
            )
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Link til opskriften") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
            Button(
                onClick = { fetch() },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                enabled = url.isNotBlank() && !loading,
            ) {
                if (loading) {
                    CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text("Hent opskrift", fontWeight = FontWeight.Bold)
                }
            }

            error?.let { message ->
                Surface(shape = RoundedCornerShape(18.dp), color = Color.White) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(message, fontSize = 15.sp)
                        TextButton(onClick = { openForm(Recipe(sourceUrl = RecipeImporter.normalizeUrl(url))) }) {
                            Text("Skriv selv", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            found?.let { recipe ->
                Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = Color.White) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Fundet på siden", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Muted)
                        Text(recipe.name, fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Pill("${recipe.ingredients.size} ingredienser")
                            Pill("${recipe.steps.size} trin")
                            if (recipe.minutes > 0) Pill("${recipe.minutes} min")
                        }
                        Text(
                            "Du kan rette alt, før opskriften gemmes.",
                            fontSize = 14.sp,
                            color = Muted,
                        )
                        YellowButton("Gennemse og gem", onClick = { openForm(recipe) })
                    }
                }
            }

            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = Mint) {
                Text(
                    "Endnu hurtigere: Tryk Del i browseren, og vælg Familien. Så hentes opskriften uden at kopiere linket.",
                    Modifier.padding(16.dp),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
            }
        }
    }
}
