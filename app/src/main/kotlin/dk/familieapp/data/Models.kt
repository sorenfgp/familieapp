package dk.familieapp.data

import kotlinx.serialization.Serializable
import java.util.UUID

// Alle felter har standardværdier, så Firestore kan oprette objekterne.

@Serializable
data class Ingredient(
    val name: String = "",
    val amount: String = "",
)

@Serializable
data class Recipe(
    val id: String = "",
    val name: String = "",
    val minutes: Int = 0,
    val servings: Int = 4,
    val ingredients: List<Ingredient> = emptyList(),
    val steps: List<String> = emptyList(),
    val sourceUrl: String = "",
    val createdAt: Long = 0,
)

/** Én ret på madplanen. [date] er ISO-dato (2026-10-10) og bruges også som id. */
@Serializable
data class PlannedMeal(
    val date: String = "",
    val title: String = "",
    val recipeId: String = "",
    val cook: String = "",
)

@Serializable
data class ShoppingItem(
    val id: String = "",
    val name: String = "",
    val amount: String = "",
    val category: String = "Andet",
    val checked: Boolean = false,
    val source: String = "",
    val createdAt: Long = 0,
)

/** En fast vare, der købes hver uge. */
@Serializable
data class Staple(
    val id: String = "",
    val name: String = "",
    val amount: String = "",
    val group: String = "Morgenmad",
)

fun newId(): String = UUID.randomUUID().toString()
