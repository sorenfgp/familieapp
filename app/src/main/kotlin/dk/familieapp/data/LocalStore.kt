package dk.familieapp.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class LocalData(
    val recipes: List<Recipe> = emptyList(),
    val meals: List<PlannedMeal> = emptyList(),
    val shopping: List<ShoppingItem> = emptyList(),
    val staples: List<Staple> = emptyList(),
)

/** Gemmer alt i én JSON-fil på telefonen. Bruges, når Firebase ikke er sat op. */
class LocalStore(private val file: File) : FamilyStore {
    private val json = Json { ignoreUnknownKeys = true }
    private val state = MutableStateFlow(load())

    private fun load(): LocalData =
        runCatching { json.decodeFromString<LocalData>(file.readText()) }.getOrElse { LocalData() }

    @Synchronized
    private fun update(change: (LocalData) -> LocalData) {
        state.value = change(state.value)
        runCatching { file.writeText(json.encodeToString(state.value)) }
    }

    override val recipes: Flow<List<Recipe>> = state.map { it.recipes }
    override val meals: Flow<List<PlannedMeal>> = state.map { it.meals }
    override val shopping: Flow<List<ShoppingItem>> = state.map { it.shopping }
    override val staples: Flow<List<Staple>> = state.map { it.staples }

    override fun saveRecipe(recipe: Recipe) = update { d ->
        d.copy(recipes = d.recipes.filter { it.id != recipe.id } + recipe)
    }

    override fun deleteRecipe(id: String) = update { d ->
        d.copy(recipes = d.recipes.filter { it.id != id })
    }

    override fun saveMeal(meal: PlannedMeal) = update { d ->
        d.copy(meals = d.meals.filter { it.date != meal.date } + meal)
    }

    override fun deleteMeal(date: String) = update { d ->
        d.copy(meals = d.meals.filter { it.date != date })
    }

    override fun saveShoppingItems(items: List<ShoppingItem>) = update { d ->
        val ids = items.map { it.id }.toSet()
        d.copy(shopping = d.shopping.filter { it.id !in ids } + items)
    }

    override fun deleteShoppingItems(ids: List<String>) = update { d ->
        d.copy(shopping = d.shopping.filter { it.id !in ids })
    }

    override fun saveStaple(staple: Staple) = update { d ->
        d.copy(staples = d.staples.filter { it.id != staple.id } + staple)
    }

    override fun deleteStaple(id: String) = update { d ->
        d.copy(staples = d.staples.filter { it.id != id })
    }
}
