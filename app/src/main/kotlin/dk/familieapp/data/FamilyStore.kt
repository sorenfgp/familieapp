package dk.familieapp.data

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.KSerializer

/** Beskriver én samling af data (fx opskrifter), så lagrene kan håndtere dem ens. */
class Col<T : Any>(
    val name: String,
    val type: Class<T>,
    val serializer: KSerializer<T>,
    val idOf: (T) -> String,
    val withId: (T, String) -> T,
)

/** Alle familiens samlinger. Et nyt modul tilføjer sin samling her. */
object Cols {
    val recipes = Col("recipes", Recipe::class.java, Recipe.serializer(), { it.id }, { r, id -> r.copy(id = id) })
    val meals = Col("meals", PlannedMeal::class.java, PlannedMeal.serializer(), { it.date }, { m, id -> m.copy(date = id) })
    val shopping = Col("shopping", ShoppingItem::class.java, ShoppingItem.serializer(), { it.id }, { s, id -> s.copy(id = id) })
    val staples = Col("staples", Staple::class.java, Staple.serializer(), { it.id }, { s, id -> s.copy(id = id) })
    val profiles = Col("profiles", Profile::class.java, Profile.serializer(), { it.id }, { p, id -> p.copy(id = id) })
    val settings = Col("settings", FamilySettings::class.java, FamilySettings.serializer(), { it.id }, { s, id -> s.copy(id = id) })
    val choreTemplates = Col("choreTemplates", ChoreTemplate::class.java, ChoreTemplate.serializer(), { it.id }, { t, id -> t.copy(id = id) })
    val chores = Col("chores", ChoreEntry::class.java, ChoreEntry.serializer(), { it.id }, { c, id -> c.copy(id = id) })
}

/**
 * Familiens data. To udgaver: [LocalStore] (kun denne telefon) og
 * [FirestoreStore] (delt mellem familiens telefoner).
 */
interface FamilyStore {
    fun <T : Any> observe(col: Col<T>): Flow<List<T>>
    fun <T : Any> save(col: Col<T>, items: List<T>)
    fun <T : Any> delete(col: Col<T>, ids: List<String>)
}
