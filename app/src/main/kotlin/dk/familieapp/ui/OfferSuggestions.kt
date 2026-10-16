@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package dk.familieapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dk.familieapp.data.Dates
import dk.familieapp.data.Offer
import dk.familieapp.data.Offers
import dk.familieapp.data.ROLE_PARENT
import dk.familieapp.data.Recipe
import dk.familieapp.data.RecipeSuggestion

private fun kroner(value: Double): String = "%.2f".format(value).replace('.', ',') + " kr."

private fun offerLine(offer: Offer): String = buildList {
    add("${offer.store} ${kroner(offer.price)}")
    offer.prePrice?.takeIf { it > offer.price }?.let { add("før ${kroner(it)}") }
    if (offer.quantity.isNotBlank()) add(offer.quantity)
    offer.until?.let { add("til ${Dates.short(it)}") }
}.joinToString(" · ")

/** Aftensretter fra kartoteket med flest ingredienser på tilbud i denne uge. */
@Composable
fun OfferSuggestions(vm: AppViewModel, nav: NavController, modifier: Modifier) {
    val context = LocalContext.current
    val recipes by vm.recipes.collectAsStateWithLifecycle()
    val meals by vm.meals.collectAsStateWithLifecycle()
    val me by vm.me.collectAsStateWithLifecycle()
    val isParent = me?.role == ROLE_PARENT
    var pickFor by remember { mutableStateOf<Recipe?>(null) }

    LaunchedEffect(recipes.size) { if (recipes.isNotEmpty()) vm.loadOfferSuggestions() }

    val settings by vm.settings.collectAsStateWithLifecycle()
    val stores = settings?.offerStores ?: Offers.STORES
    val result = vm.offerData
    // Kun tilbud fra de valgte butikker tæller med.
    val suggestions = result?.getOrNull()?.let { data ->
        Offers.suggest(recipes, data.mapValues { (_, offers) -> offers.filter { it.store in stores } })
    }

    LazyColumn(
        modifier,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "stores") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Offers.STORES.forEach { store ->
                        FilterChip(
                            selected = store in stores,
                            onClick = { vm.setOfferStore(store, store !in stores) },
                            enabled = isParent,
                            label = { Text(store) },
                        )
                    }
                }
                Text(
                    if (isParent) "Vælg de butikker, I vil handle i. Valget gælder hele familien."
                    else "Forældrene vælger butikkerne.",
                    fontSize = 13.sp,
                    color = Muted,
                )
            }
        }
        item(key = "intro") {
            Text(
                "Jeres aftensretter med flest ingredienser på tilbud i denne uge, inden for 15 km af Hillerød.",
                fontSize = 13.sp,
                color = Muted,
            )
        }
        if (stores.isEmpty()) {
            item(key = "no-stores") {
                Text("Vælg mindst én butik for at få forslag.", Modifier.padding(vertical = 12.dp), color = Muted)
            }
        } else
        when {
            recipes.isEmpty() -> item(key = "none") {
                Text("Der er ingen opskrifter endnu.", Modifier.padding(vertical = 12.dp), color = Muted)
            }
            vm.offersLoading && suggestions == null -> item(key = "loading") {
                Row(
                    Modifier.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    Text("Henter ugens tilbud …", color = Muted)
                }
            }
            result?.isFailure == true -> item(key = "error") {
                Column(Modifier.padding(vertical = 8.dp)) {
                    Text("Tilbuddene kunne ikke hentes. Tjek internetforbindelsen.", color = Muted)
                    TextButton(onClick = { vm.loadOfferSuggestions(force = true) }, contentPadding = PaddingValues(0.dp)) {
                        Text("Prøv igen", fontWeight = FontWeight.Bold)
                    }
                }
            }
            suggestions != null && suggestions.isEmpty() -> item(key = "empty") {
                Text(
                    "Ingen af aftensretternes ingredienser er på tilbud i de valgte butikker lige nu.",
                    Modifier.padding(vertical = 12.dp),
                    color = Muted,
                )
            }
            suggestions != null -> items(suggestions, key = { it.recipe.id }) { suggestion ->
                SuggestionCard(
                    suggestion = suggestion,
                    canPlan = isParent,
                    onOpen = { nav.navigate("recipe/${suggestion.recipe.id}") },
                    onPlan = { pickFor = suggestion.recipe },
                )
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

@Composable
private fun SuggestionCard(
    suggestion: RecipeSuggestion,
    canPlan: Boolean,
    onOpen: () -> Unit,
    onPlan: () -> Unit,
) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = Color.White) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 4.dp)) {
            Text(suggestion.recipe.name, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(
                "${suggestion.matches.size} af ${suggestion.recipe.ingredients.size} ingredienser på tilbud",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Green,
            )
            Spacer(Modifier.height(6.dp))
            suggestion.matches.forEach { (ingredient, offer) ->
                Column(Modifier.padding(vertical = 3.dp)) {
                    Text(ingredient.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(offerLine(offer), fontSize = 13.sp, color = Muted)
                    if (offer.heading.isNotBlank() && !offer.heading.equals(ingredient.name, ignoreCase = true)) {
                        Text("\"${offer.heading}\"", fontSize = 12.sp, color = Muted, maxLines = 1)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onOpen) { Text("Se opskrift", fontWeight = FontWeight.Bold) }
                if (canPlan) {
                    TextButton(onClick = onPlan) { Text("Læg på madplanen", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}
