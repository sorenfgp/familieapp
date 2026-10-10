package dk.familieapp.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dk.familieapp.data.Categories
import dk.familieapp.data.FamilyStore
import dk.familieapp.data.FirestoreStore
import dk.familieapp.data.Ingredient
import dk.familieapp.data.LocalStore
import dk.familieapp.data.PlannedMeal
import dk.familieapp.data.Recipe
import dk.familieapp.data.Session
import dk.familieapp.data.ShoppingItem
import dk.familieapp.data.Staple
import dk.familieapp.data.newId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import java.io.File
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(app: Application) : AndroidViewModel(app) {
    val session = Session(app)

    private val localFile = File(app.filesDir, "familie.json")

    private fun currentStore(): FamilyStore? = when {
        !session.firebaseEnabled -> LocalStore(localFile)
        else -> session.householdId?.let { FirestoreStore(it) }
    }

    private val _store = MutableStateFlow(currentStore())

    /** Null betyder: Firebase er sat op, men telefonen er ikke med i en familie endnu. */
    val store: StateFlow<FamilyStore?> = _store

    private fun <T> observe(select: (FamilyStore) -> Flow<List<T>>): StateFlow<List<T>> =
        _store.flatMapLatest { s -> if (s == null) flowOf(emptyList()) else select(s) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val recipes: StateFlow<List<Recipe>> = observe { it.recipes }
    val meals: StateFlow<List<PlannedMeal>> = observe { it.meals }
    val shopping: StateFlow<List<ShoppingItem>> = observe { it.shopping }
    val staples: StateFlow<List<Staple>> = observe { it.staples }

    // Skærmtilstand, der skal overleve skift mellem faner.
    var mealTab by mutableIntStateOf(0)
    var shoppingTab by mutableIntStateOf(0)

    /** Opskrift på vej ind i redigeringsskærmen (ny eller importeret). */
    var draft by mutableStateOf<Recipe?>(null)

    // ---- Familie ----

    suspend fun createHousehold(name: String) {
        session.createHousehold(name)
        _store.value = currentStore()
    }

    suspend fun joinHousehold(code: String) {
        session.joinHousehold(code)
        _store.value = currentStore()
    }

    fun leaveHousehold() {
        session.leaveHousehold()
        _store.value = currentStore()
    }

    // ---- Opskrifter ----

    fun saveRecipe(recipe: Recipe): String {
        val saved = if (recipe.id.isBlank()) {
            recipe.copy(id = newId(), createdAt = System.currentTimeMillis())
        } else {
            recipe
        }
        _store.value?.saveRecipe(saved)
        return saved.id
    }

    fun deleteRecipe(id: String) {
        _store.value?.deleteRecipe(id)
    }

    // ---- Madplan ----

    fun planMeal(date: LocalDate, title: String, recipeId: String, cook: String) {
        _store.value?.saveMeal(PlannedMeal(date.toString(), title, recipeId, cook))
    }

    fun planRecipe(date: LocalDate, recipe: Recipe) {
        val cook = meals.value.firstOrNull { it.date == date.toString() }?.cook.orEmpty()
        planMeal(date, recipe.name, recipe.id, cook)
    }

    fun clearMeal(date: LocalDate) {
        _store.value?.deleteMeal(date.toString())
    }

    // ---- Indkøbsliste ----

    private fun shoppingItem(name: String, amount: String, source: String, order: Int = 0) = ShoppingItem(
        id = newId(),
        name = name,
        amount = amount,
        category = Categories.of(name),
        source = source,
        createdAt = System.currentTimeMillis() + order,
    )

    fun addShoppingItem(name: String, amount: String) {
        if (name.isBlank()) return
        _store.value?.saveShoppingItems(listOf(shoppingItem(name.trim(), amount.trim(), "")))
    }

    fun addIngredients(recipeName: String, ingredients: List<Ingredient>): Int {
        val items = ingredients.filter { it.name.isNotBlank() }
            .mapIndexed { i, ing -> shoppingItem(ing.name, ing.amount, recipeName, i) }
        _store.value?.saveShoppingItems(items)
        return items.size
    }

    /** Lægger ingredienserne fra alle ugens planlagte opskrifter på listen. */
    fun addWeekIngredients(weekStart: LocalDate): Int {
        val dates = (0..6).map { weekStart.plusDays(it.toLong()).toString() }.toSet()
        val planned = meals.value.filter { it.date in dates && it.recipeId.isNotBlank() }
        var count = 0
        planned.forEach { meal ->
            val recipe = recipes.value.firstOrNull { it.id == meal.recipeId }
            if (recipe != null) count += addIngredients(recipe.name, recipe.ingredients)
        }
        return count
    }

    fun toggleShoppingItem(item: ShoppingItem) {
        _store.value?.saveShoppingItems(listOf(item.copy(checked = !item.checked)))
    }

    fun clearCheckedItems() {
        _store.value?.deleteShoppingItems(shopping.value.filter { it.checked }.map { it.id })
    }

    // ---- Faste varer ----

    fun saveStaple(staple: Staple) {
        if (staple.name.isBlank()) return
        _store.value?.saveStaple(if (staple.id.isBlank()) staple.copy(id = newId()) else staple)
    }

    fun deleteStaple(id: String) {
        _store.value?.deleteStaple(id)
    }

    /** Lægger de faste varer på ugens liste. Varer, der allerede står der, springes over. */
    fun addStaplesToShopping(): Int {
        val onList = shopping.value.filter { !it.checked }.map { it.name.lowercase() }.toSet()
        val items = staples.value.filter { it.name.lowercase() !in onList }
            .mapIndexed { i, s -> shoppingItem(s.name, s.amount, "Fast vare", i) }
        _store.value?.saveShoppingItems(items)
        return items.size
    }
}
