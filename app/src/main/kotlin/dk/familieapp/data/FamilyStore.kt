package dk.familieapp.data

import kotlinx.coroutines.flow.Flow

/**
 * Familiens data. To udgaver: [LocalStore] (kun denne telefon) og
 * [FirestoreStore] (delt mellem familiens telefoner).
 * Nye moduler tilføjes ved at udvide dette interface.
 */
interface FamilyStore {
    val recipes: Flow<List<Recipe>>
    val meals: Flow<List<PlannedMeal>>
    val shopping: Flow<List<ShoppingItem>>
    val staples: Flow<List<Staple>>

    fun saveRecipe(recipe: Recipe)
    fun deleteRecipe(id: String)

    fun saveMeal(meal: PlannedMeal)
    fun deleteMeal(date: String)

    fun saveShoppingItems(items: List<ShoppingItem>)
    fun deleteShoppingItems(ids: List<String>)

    fun saveStaple(staple: Staple)
    fun deleteStaple(id: String)
}
