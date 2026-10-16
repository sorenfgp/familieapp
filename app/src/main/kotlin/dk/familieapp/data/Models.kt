package dk.familieapp.data

import kotlinx.serialization.Serializable
import java.util.UUID

// Alle felter har standardværdier, så Firestore kan oprette objekterne.

const val ROLE_PARENT = "parent"

const val KIND_DINNER = "dinner"
const val KIND_DESSERT = "dessert"
const val ROLE_CHILD = "child"

const val CHORE_OPEN = "open"
const val CHORE_DONE = "done"
const val CHORE_APPROVED = "approved"

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
    /** Aftensmad ([KIND_DINNER]) eller dessert ([KIND_DESSERT]). */
    val kind: String = KIND_DINNER,
)

/** Én ret på madplanen. [date] er ISO-dato (2026-10-10). */
@Serializable
data class PlannedMeal(
    val date: String = "",
    val title: String = "",
    val recipeId: String = "",
    val cook: String = "",
    /** Hver ret har sit eget id, så en dag kan have flere retter. Ældre retter har datoen som id. */
    val id: String = "",
    val order: Long = 0,
)

/** Dagens retter i den rækkefølge, de blev tilføjet. */
fun List<PlannedMeal>.onDate(date: String): List<PlannedMeal> =
    filter { it.date == date }.sortedBy { it.order }

@Serializable
data class ShoppingItem(
    val id: String = "",
    val name: String = "",
    val amount: String = "",
    val category: String = "Andet",
    val checked: Boolean = false,
    /** Gammelt felt med én kilde. Nye varer bruger [sources]. */
    val source: String = "",
    /** Opskrifter (eller "Fast vare"), varen kommer fra. */
    val sources: List<String> = emptyList(),
    /** Profilen, der lagde varen i kurven. */
    val checkedBy: String = "",
    val createdAt: Long = 0,
) {
    fun allSources(): List<String> =
        if (sources.isEmpty() && source.isNotBlank()) listOf(source) else sources
}

/** En fast vare, der købes hver uge. */
@Serializable
data class Staple(
    val id: String = "",
    val name: String = "",
    val amount: String = "",
    val group: String = "Morgenmad",
)

/** Et familiemedlem. Hver enhed vælger en profil. */
@Serializable
data class Profile(
    val id: String = "",
    val name: String = "",
    val role: String = ROLE_CHILD,
    val color: Int = 0,
    /** Kortene, profilen vil se på forsiden (se [HomeCards]). */
    val homeCards: List<String> = HomeCards.default,
    /** Profilens rækkefølge på forsidens kort. Tom = standardrækkefølgen. */
    val cardOrder: List<String> = emptyList(),
)

/** Fælles indstillinger for familien. Gemmes som ét dokument med id "main". */
@Serializable
data class FamilySettings(
    val id: String = "main",
    val parentPinHash: String = "",
    /** Google-kalenderens id (fx abc@group.calendar.google.com). Ens på alle telefoner. */
    val calendarKey: String = "",
    val calendarName: String = "",
    /** "text" i dag. Senere evt. "points" eller "money". */
    val rewardMode: String = "text",
    /** Nøgle til Rejseplanens API, så forsiden kan vise S-togets afgange. */
    val rejseplanenKey: String = "",
    /** Butikker, der indgår i tilbudsforslagene. Gælder hele familien. */
    val offerStores: List<String> = Offers.STORES,
)

/** En fast pligt: samme barn på faste ugedage (1 = mandag … 7 = søndag). */
@Serializable
data class ChoreTemplate(
    val id: String = "",
    val title: String = "",
    val childId: String = "",
    val weekdays: List<Int> = emptyList(),
    val reward: String = "",
    val createdAt: Long = 0,
)

/**
 * En konkret pligt på en dato. Ad hoc-pligter gemmes, når de oprettes.
 * Faste pligter gemmes først, når nogen krydser af; id er da "{skabelon}_{dato}".
 */
@Serializable
data class ChoreEntry(
    val id: String = "",
    val title: String = "",
    val childId: String = "",
    val date: String = "",
    val reward: String = "",
    val templateId: String = "",
    val status: String = CHORE_OPEN,
    val doneBy: String = "",
    val doneAt: Long = 0,
    val approvedBy: String = "",
    val approvedAt: Long = 0,
    val createdBy: String = "",
    val createdAt: Long = 0,
)

/**
 * Kopi af én aftale fra familiekalenderen. En forælders telefon lægger kopierne i
 * familiens data, så enheder uden Google-konto kan læse kalenderen.
 * id er "{eventId}_{begin}", fordi gentagne aftaler deler eventId.
 */
@Serializable
data class MirroredEvent(
    val id: String = "",
    val eventId: Long = 0,
    val title: String = "",
    val begin: Long = 0,
    val end: Long = 0,
    val allDay: Boolean = false,
    val location: String = "",
    val color: Int = 0,
    val description: String = "",
    val recurring: Boolean = false,
)

/** Brugerens valg af afdeling for en vare, fx "Kartoffelmel" → "Kolonial". id er varens navn med små bogstaver. */
@Serializable
data class CategoryOverride(
    val id: String = "",
    val name: String = "",
    val category: String = "",
)

/** Hvornår kopien af familiekalenderen sidst blev opdateret. Ét dokument med id "main". */
@Serializable
data class CalendarSyncInfo(
    val id: String = "main",
    val calendarKey: String = "",
    val calendarName: String = "",
    val syncedAt: Long = 0,
)

fun newId(): String = UUID.randomUUID().toString()
