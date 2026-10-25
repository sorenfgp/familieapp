@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dk.familieapp.data.Dates
import dk.familieapp.data.Ingredient
import dk.familieapp.data.NoRecipeFoundException
import dk.familieapp.data.KIND_DESSERT
import dk.familieapp.data.KIND_DINNER
import dk.familieapp.data.Recipe
import dk.familieapp.data.RecipeImporter
import dk.familieapp.data.ROLE_PARENT
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlinx.coroutines.launch
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import dk.familieapp.data.StepAmounts
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.core.content.FileProvider
import dk.familieapp.data.RecipeImages
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
                RecipePhoto(vm, recipe.id)
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
                                    // Mængderne sættes i parentes efter hver ingrediens, der nævnes.
                                    val text = buildAnnotatedString {
                                        StepAmounts.annotate(step, recipe.ingredients).forEach { part ->
                                            if (part.amount) {
                                                withStyle(SpanStyle(color = Green, fontWeight = FontWeight.SemiBold)) { append(part.text) }
                                            } else {
                                                append(part.text)
                                            }
                                        }
                                    }
                                    Text(text, Modifier.weight(1f), fontSize = 15.sp, lineHeight = 21.sp)
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

private val IngredientListSaver = listSaver<SnapshotStateList<Ingredient>, String>(
    save = { list -> list.flatMap { listOf(it.name, it.amount) } },
    restore = { flat ->
        mutableStateListOf<Ingredient>().apply {
            flat.chunked(2).forEach { add(Ingredient(it[0], it.getOrElse(1) { "" })) }
        }
    },
)

private val StringListSaver = listSaver<SnapshotStateList<String>, String>(
    save = { it.toList() },
    restore = { it.toMutableStateList() },
)

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

    val savedImage by remember(id) { vm.recipeImage(id) }.collectAsStateWithLifecycle(null)
    RecipeForm(
        initial = source,
        initialImage = savedImage?.data,
        isNew = isNew,
        onCancel = {
            vm.draft = null
            nav.popBackStack()
        },
        onSave = { recipe, image, imageChanged ->
            val savedId = vm.saveRecipe(recipe)
            if (imageChanged) vm.saveRecipeImage(savedId, image)
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
    initialImage: String?,
    isNew: Boolean,
    onCancel: () -> Unit,
    onSave: (Recipe, image: String?, imageChanged: Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    // rememberSaveable: det skrevne overlever, når skærmen vendes eller appen lægges i baggrunden.
    var name by rememberSaveable { mutableStateOf(initial.name) }
    var minutes by rememberSaveable { mutableStateOf(if (initial.minutes > 0) initial.minutes.toString() else "") }
    var servings by rememberSaveable { mutableStateOf(initial.servings.toString()) }
    var kind by rememberSaveable { mutableStateOf(initial.kind) }
    val ingredients = rememberSaveable(saver = IngredientListSaver) {
        mutableStateListOf<Ingredient>().apply { addAll(initial.ingredients) }
    }
    val steps = rememberSaveable(saver = StringListSaver) {
        mutableStateListOf<String>().apply { addAll(initial.steps) }
    }
    var confirmDelete by remember { mutableStateOf(false) }
    // Billedet: det gemte, et importeret eller et nyt fra kamera/galleri.
    var image by remember { mutableStateOf<String?>(null) }
    var imageChanged by remember { mutableStateOf(false) }
    LaunchedEffect(initialImage) { if (!imageChanged) image = initialImage }
    LaunchedEffect(initial.imageUrl) {
        // Importeret opskrift: hent sidens billede med det samme.
        if (isNew && initial.imageUrl.isNotBlank() && image == null) {
            RecipeImages.fromUrl(initial.imageUrl)?.let {
                image = it
                imageChanged = true
            }
        }
    }
    // Rækken, hvis felt skal have fokus efter næste tegning (-1 = ingen).
    var focusIngredient by remember { mutableIntStateOf(-1) }
    var focusStep by remember { mutableIntStateOf(-1) }

    fun addIngredient() {
        ingredients.add(Ingredient())
        focusIngredient = ingredients.lastIndex
    }

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
                            kind = kind,
                            servings = servings.toIntOrNull() ?: 4,
                            ingredients = ingredients
                                .filter { it.name.isNotBlank() }
                                .map { Ingredient(it.name.trim(), it.amount.trim()) },
                            steps = steps.map { it.trim() }.filter { it.isNotBlank() },
                        ),
                        image,
                        imageChanged,
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
            PhotoPicker(image) { newImage ->
                image = newImage
                imageChanged = true
            }
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

            SectionLabel("Type")
            Segmented(
                options = listOf("Aftensmad", "Dessert"),
                selected = if (kind == KIND_DESSERT) 1 else 0,
                onSelect = { kind = if (it == 1) KIND_DESSERT else KIND_DINNER },
            )
            SectionLabel("Ingredienser")
            ingredients.forEachIndexed { index, ingredient ->
                key(index) {
                    val nameFocus = remember { FocusRequester() }
                    val amountFocus = remember { FocusRequester() }
                    // Nyt felt: hop direkte ind i det, så man ikke skal trykke to gange.
                    LaunchedEffect(focusIngredient) {
                        if (focusIngredient == index) {
                            nameFocus.requestFocus()
                            focusIngredient = -1
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        OutlinedTextField(
                            value = ingredient.name,
                            onValueChange = { ingredients[index] = ingredient.copy(name = it) },
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(nameFocus),
                            placeholder = { Text("Vare") },
                            // Enter går videre til mængden.
                            keyboardOptions = SentenceCaps.copy(imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { amountFocus.requestFocus() }),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = ingredient.amount,
                            onValueChange = { ingredients[index] = ingredient.copy(amount = it) },
                            modifier = Modifier
                                .width(108.dp)
                                .focusRequester(amountFocus),
                            placeholder = { Text("Mængde") },
                            // Enter i sidste række tilføjer en ny ingrediens, ellers går den til næste række.
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = {
                                if (index == ingredients.lastIndex) addIngredient() else focusIngredient = index + 1
                            }),
                            singleLine = true,
                        )
                        IconButton(onClick = { ingredients.removeAt(index) }) {
                            Icon(Icons.Filled.Close, contentDescription = "Fjern ingrediens ${index + 1}")
                        }
                    }
                }
            }
            TextButton(onClick = { addIngredient() }) {
                Text("+ Tilføj ingrediens", fontWeight = FontWeight.Bold)
            }

            SectionLabel("Fremgangsmåde")
            steps.forEachIndexed { index, step ->
                key(index) {
                    val stepFocus = remember { FocusRequester() }
                    LaunchedEffect(focusStep) {
                        if (focusStep == index) {
                            stepFocus.requestFocus()
                            focusStep = -1
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.padding(top = 14.dp)) { StepNumber(index + 1) }
                        OutlinedTextField(
                            value = step,
                            onValueChange = { steps[index] = it },
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(stepFocus),
                            minLines = 2,
                            keyboardOptions = SentenceCaps,
                        )
                        IconButton(onClick = { steps.removeAt(index) }) {
                            Icon(Icons.Filled.Close, contentDescription = "Fjern trin ${index + 1}")
                        }
                    }
                }
            }
            TextButton(onClick = {
                steps.add("")
                focusStep = steps.lastIndex
            }) {
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

// ---------- Billede af retten ----------

@Composable
private fun RecipePhoto(vm: AppViewModel, recipeId: String) {
    val stored by remember(recipeId) { vm.recipeImage(recipeId) }.collectAsStateWithLifecycle(null)
    val bitmap = remember(stored?.data) { stored?.data?.let { RecipeImages.decode(it)?.asImageBitmap() } }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = "Billede af retten",
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .clip(RoundedCornerShape(20.dp)),
            contentScale = ContentScale.Crop,
        )
    }
}

/** Viser billedet og knapper til at tage et nyt, vælge et fra galleriet eller fjerne det. */
@Composable
private fun PhotoPicker(image: String?, onChange: (String?) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val photoUri = remember {
        val dir = java.io.File(context.cacheDir, "images").apply { mkdirs() }
        FileProvider.getUriForFile(context, context.packageName + ".fileprovider", java.io.File(dir, "ret.jpg"))
    }

    fun load(uri: Uri) {
        busy = true
        scope.launch {
            val data = RecipeImages.fromUri(context, uri)
            busy = false
            if (data != null) onChange(data) else context.toast("Billedet kunne ikke bruges")
        }
    }

    /** Indsætter et billede (eller et link til et billede) fra udklipsholderen. */
    fun paste() {
        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = clipboard.primaryClip
        val item = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)
        val uri = item?.uri
        val text = item?.coerceToText(context)?.toString()?.trim().orEmpty()
        when {
            uri != null -> load(uri)
            text.startsWith("http") -> {
                busy = true
                scope.launch {
                    val data = RecipeImages.fromUrl(text)
                    busy = false
                    if (data != null) onChange(data) else context.toast("Linket i udklipsholderen er ikke et billede")
                }
            }
            else -> context.toast("Der er ikke noget billede i udklipsholderen")
        }
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) load(photoUri)
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) load(uri)
    }

    val bitmap = remember(image) { image?.let { RecipeImages.decode(it)?.asImageBitmap() } }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = "Billede af retten",
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .clip(RoundedCornerShape(20.dp)),
            contentScale = ContentScale.Crop,
        )
    }
    FlowRow(
        verticalArrangement = Arrangement.Center,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        OutlinedButton(onClick = { camera.launch(photoUri) }, enabled = !busy) {
            Text(if (bitmap == null) "Tag billede" else "Nyt billede")
        }
        OutlinedButton(
            onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            enabled = !busy,
        ) { Text("Vælg billede") }
        OutlinedButton(onClick = { paste() }, enabled = !busy) { Text("Indsæt") }
        if (busy) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        } else if (bitmap != null) {
            TextButton(onClick = { onChange(null) }) { Text("Fjern") }
        }
    }
}
