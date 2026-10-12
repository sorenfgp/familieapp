package dk.familieapp.ui

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dk.familieapp.data.Addition
import dk.familieapp.data.CalendarMirror
import dk.familieapp.data.CalendarSyncInfo
import dk.familieapp.data.CategoryOverride
import dk.familieapp.data.MirroredEvent
import kotlinx.coroutines.launch
import dk.familieapp.data.CHORE_APPROVED
import dk.familieapp.data.CHORE_DONE
import dk.familieapp.data.CHORE_OPEN
import dk.familieapp.data.Categories
import dk.familieapp.data.ChoreEntry
import dk.familieapp.data.ChoreTemplate
import dk.familieapp.data.Col
import dk.familieapp.data.Cols
import dk.familieapp.data.FamilySettings
import dk.familieapp.data.FamilyStore
import dk.familieapp.data.FirestoreStore
import dk.familieapp.data.Ingredient
import dk.familieapp.data.LocalStore
import dk.familieapp.data.Pins
import dk.familieapp.data.PlannedMeal
import dk.familieapp.data.Profile
import dk.familieapp.data.ROLE_PARENT
import dk.familieapp.data.Recipe
import dk.familieapp.data.Session
import dk.familieapp.data.ShoppingItem
import dk.familieapp.data.ShoppingMerge
import dk.familieapp.data.Staple
import dk.familieapp.data.newId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.io.File
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(app: Application) : AndroidViewModel(app) {
    val session = Session(app)
    private val prefs = app.getSharedPreferences("profile", Context.MODE_PRIVATE)
    private val localFile = File(app.filesDir, "familie-v2.json")

    private fun currentStore(): FamilyStore? = when {
        !session.firebaseEnabled -> LocalStore(localFile)
        else -> session.householdId?.let { FirestoreStore(it) }
    }

    private val _store = MutableStateFlow(currentStore())

    /** Null betyder: Firebase er sat op, men telefonen er ikke med i en familie endnu. */
    val store: StateFlow<FamilyStore?> = _store

    private fun <T : Any> observe(col: Col<T>): StateFlow<List<T>> =
        _store.flatMapLatest { s -> if (s == null) flowOf(emptyList()) else s.observe(col) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Som [observe], men null indtil data er hentet første gang. */
    private fun <T : Any> observeOrNull(col: Col<T>): StateFlow<List<T>?> =
        _store.flatMapLatest { s -> if (s == null) flowOf(null) else s.observe(col) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val recipes: StateFlow<List<Recipe>> = observe(Cols.recipes)
    val meals: StateFlow<List<PlannedMeal>> = observe(Cols.meals)
    val shopping: StateFlow<List<ShoppingItem>> = observe(Cols.shopping)
    val staples: StateFlow<List<Staple>> = observe(Cols.staples)
    val choreTemplates: StateFlow<List<ChoreTemplate>> = observe(Cols.choreTemplates)
    val chores: StateFlow<List<ChoreEntry>> = observe(Cols.chores)
    val profiles: StateFlow<List<Profile>?> = observeOrNull(Cols.profiles)
    val settings: StateFlow<FamilySettings?> = observeOrNull(Cols.settings)
        .map { list -> list?.let { it.firstOrNull { s -> s.id == "main" } ?: FamilySettings() } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // ---- Hvem bruger telefonen ----

    private fun householdKey(): String = session.householdId ?: "local"
    private fun profileKey(): String = "profile_" + householdKey()
    private val profileId = MutableStateFlow(prefs.getString(profileKey(), null))

    /** Profilen, der er valgt på denne enhed. */
    val me: StateFlow<Profile?> = combine(profiles, profileId) { list, id -> list?.firstOrNull { it.id == id } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val isParent: Boolean get() = me.value?.role == ROLE_PARENT
    private val myId: String get() = me.value?.id.orEmpty()

    private fun now(): Long = System.currentTimeMillis()

    private fun <T : Any> put(col: Col<T>, item: T) {
        _store.value?.save(col, listOf(item))
    }

    // Skærmtilstand, der skal overleve skift mellem faner.
    var mealTab by mutableIntStateOf(0)
    var shoppingTab by mutableIntStateOf(0)
    var choreTab by mutableIntStateOf(0)

    /** Opskrift på vej ind i redigeringsskærmen (ny eller importeret). */
    var draft by mutableStateOf<Recipe?>(null)

    // ---- Familie ----

    private fun refreshHousehold() {
        _store.value = currentStore()
        profileId.value = prefs.getString(profileKey(), null)
    }

    suspend fun createHousehold(name: String) {
        session.createHousehold(name)
        refreshHousehold()
    }

    suspend fun joinHousehold(code: String) {
        session.joinHousehold(code)
        refreshHousehold()
    }

    fun leaveHousehold() {
        session.leaveHousehold()
        refreshHousehold()
    }

    // ---- Profiler og PIN ----

    fun selectProfile(id: String?) {
        prefs.edit().putString(profileKey(), id).apply()
        profileId.value = id
    }

    fun saveProfile(profile: Profile): Profile {
        val saved = if (profile.id.isBlank()) profile.copy(id = newId()) else profile
        put(Cols.profiles, saved)
        return saved
    }

    fun deleteProfile(id: String) {
        _store.value?.delete(Cols.profiles, listOf(id))
    }

    fun hasPin(): Boolean = settings.value?.parentPinHash?.isNotBlank() == true

    fun checkPin(pin: String): Boolean =
        !hasPin() || Pins.hash(householdKey(), pin) == settings.value?.parentPinHash

    private fun saveSettings(change: (FamilySettings) -> FamilySettings) {
        put(Cols.settings, change(settings.value ?: FamilySettings()))
    }

    fun setPin(pin: String) = saveSettings { it.copy(parentPinHash = Pins.hash(householdKey(), pin)) }

    fun setFamilyCalendar(key: String, name: String) = saveSettings { it.copy(calendarKey = key, calendarName = name) }

    /** Første profil i en ny familie: en forælder, som også vælger PIN. */
    fun createFirstParent(name: String, pin: String) {
        setPin(pin)
        val profile = saveProfile(Profile(name = name, role = ROLE_PARENT, color = 0))
        selectProfile(profile.id)
    }

    // ---- Opskrifter ----

    fun saveRecipe(recipe: Recipe): String {
        val saved = if (recipe.id.isBlank()) recipe.copy(id = newId(), createdAt = now()) else recipe
        put(Cols.recipes, saved)
        return saved.id
    }

    fun deleteRecipe(id: String) {
        _store.value?.delete(Cols.recipes, listOf(id))
    }

    // ---- Madplan ----

    fun planMeal(date: LocalDate, title: String, recipeId: String, cook: String) {
        if (!isParent) return
        put(Cols.meals, PlannedMeal(date.toString(), title, recipeId, cook))
    }

    fun planRecipe(date: LocalDate, recipe: Recipe) {
        val cook = meals.value.firstOrNull { it.date == date.toString() }?.cook.orEmpty()
        planMeal(date, recipe.name, recipe.id, cook)
    }

    fun clearMeal(date: LocalDate) {
        if (!isParent) return
        _store.value?.delete(Cols.meals, listOf(date.toString()))
    }

    // ---- Familiekalender: kopi til enheder uden adgang til Google-kalenderen ----

    val calendarMirror: StateFlow<List<MirroredEvent>> = observe(Cols.calendarEvents)
    val calendarSync: StateFlow<CalendarSyncInfo?> = observe(Cols.calendarSync)
        .map { list -> list.firstOrNull { it.id == "main" } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private var lastMirrorSync = 0L

    /**
     * Har denne telefon familiekalenderen, kopieres dens aftaler til familiens data,
     * så børnenes enheder kan læse dem. Højst hvert 10. minut, medmindre [force].
     */
    fun syncCalendar(force: Boolean = false) {
        val key = settings.value?.calendarKey.orEmpty()
        val store = _store.value ?: return
        if (key.isBlank() || !session.firebaseEnabled) return
        if (!force && now() - lastMirrorSync < 10 * 60 * 1000L) return
        lastMirrorSync = now()
        val app = getApplication<Application>()
        viewModelScope.launch {
            runCatching {
                if (CalendarMirror.sync(app, store, key)) CalendarMirror.schedule(app)
            }
        }
    }

    // ---- Indkøbsliste (kun forældre må rette) ----

    /** Varer, som familien har flyttet til en anden afdeling. Valget huskes til næste gang. */
    val categoryOverrides: StateFlow<List<CategoryOverride>> = observe(Cols.categoryOverrides)

    private fun categoryFor(name: String): String =
        categoryOverrides.value.firstOrNull { ShoppingMerge.sameName(it.name, name) }?.category
            ?: Categories.of(name)

    /** Flytter varen til en anden afdeling og husker valget for varen. */
    fun moveToCategory(item: ShoppingItem, category: String) {
        if (!isParent) return
        put(Cols.shopping, item.copy(category = category))
        val id = item.name.trim().lowercase().replace('/', '_').ifBlank { "_" }
        put(Cols.categoryOverrides, CategoryOverride(id = id, name = item.name.trim(), category = category))
    }

    /** Tømmer hele indkøbslisten, også det der er i kurven. */
    fun clearShoppingList() {
        if (!isParent) return
        _store.value?.delete(Cols.shopping, shopping.value.map { it.id })
    }

    /** Lægger varer på listen og slår dem sammen med ens varer, der allerede står der. */
    private fun addToShopping(additions: List<Addition>): Int {
        if (!isParent) return 0
        var order = 0
        val changed = ShoppingMerge.merge(shopping.value, additions) { addition ->
            ShoppingItem(
                id = newId(),
                name = addition.name,
                amount = addition.amount,
                category = categoryFor(addition.name),
                sources = listOf(addition.source).filter { it.isNotBlank() },
                createdAt = now() + order++,
            )
        }
        _store.value?.save(Cols.shopping, changed)
        return additions.count { it.name.isNotBlank() }
    }

    fun addShoppingItem(name: String, amount: String) {
        if (name.isBlank()) return
        addToShopping(listOf(Addition(name.trim(), amount.trim())))
    }

    fun addIngredients(recipeName: String, ingredients: List<Ingredient>): Int =
        addToShopping(ingredients.map { Addition(it.name.trim(), it.amount.trim(), recipeName) })

    /** Lægger ingredienserne fra alle ugens planlagte opskrifter på listen. */
    fun addWeekIngredients(weekStart: LocalDate): Int {
        val dates = (0..6).map { weekStart.plusDays(it.toLong()).toString() }.toSet()
        val additions = meals.value
            .filter { it.date in dates && it.recipeId.isNotBlank() }
            .sortedBy { it.date }
            .flatMap { meal ->
                val recipe = recipes.value.firstOrNull { it.id == meal.recipeId }
                recipe?.ingredients?.map { Addition(it.name.trim(), it.amount.trim(), recipe.name) }.orEmpty()
            }
        return addToShopping(additions)
    }

    fun toggleShoppingItem(item: ShoppingItem) {
        if (!isParent) return
        val checked = !item.checked
        put(Cols.shopping, item.copy(checked = checked, checkedBy = if (checked) myId else ""))
    }

    fun clearCheckedItems() {
        if (!isParent) return
        _store.value?.delete(Cols.shopping, shopping.value.filter { it.checked }.map { it.id })
    }

    fun saveStaple(staple: Staple) {
        if (!isParent || staple.name.isBlank()) return
        put(Cols.staples, if (staple.id.isBlank()) staple.copy(id = newId()) else staple)
    }

    fun deleteStaple(id: String) {
        if (!isParent) return
        _store.value?.delete(Cols.staples, listOf(id))
    }

    /** Lægger de faste varer på ugens liste. Varer, der allerede står der, springes over. */
    fun addStaplesToShopping(): Int {
        val open = shopping.value.filter { !it.checked }
        val missing = staples.value.filter { staple -> open.none { ShoppingMerge.sameName(it.name, staple.name) } }
        return addToShopping(missing.map { Addition(it.name, it.amount, "Fast vare") })
    }

    // ---- Pligter ----

    fun saveChoreTemplate(template: ChoreTemplate) {
        if (!isParent || template.title.isBlank()) return
        put(Cols.choreTemplates, if (template.id.isBlank()) template.copy(id = newId(), createdAt = now()) else template)
    }

    fun deleteChoreTemplate(id: String) {
        if (!isParent) return
        _store.value?.delete(Cols.choreTemplates, listOf(id))
    }

    fun addAdHocChore(title: String, childId: String, date: LocalDate, reward: String) {
        if (!isParent || title.isBlank()) return
        put(
            Cols.chores,
            ChoreEntry(
                id = newId(),
                title = title.trim(),
                childId = childId,
                date = date.toString(),
                reward = reward.trim(),
                createdBy = myId,
                createdAt = now(),
            ),
        )
    }

    fun deleteChore(id: String) {
        if (!isParent) return
        _store.value?.delete(Cols.chores, listOf(id))
    }

    /** Barnet (eller en forælder) melder pligten udført. */
    fun markChoreDone(entry: ChoreEntry) {
        if (myId.isBlank()) return
        put(Cols.chores, entry.copy(status = CHORE_DONE, doneBy = myId, doneAt = now()))
    }

    fun undoChoreDone(entry: ChoreEntry) {
        if (entry.status == CHORE_APPROVED && !isParent) return
        put(Cols.chores, entry.copy(status = CHORE_OPEN, doneBy = "", doneAt = 0, approvedBy = "", approvedAt = 0))
    }

    /** En forælder bekræfter, at pligten er udført. */
    fun approveChore(entry: ChoreEntry) {
        if (!isParent) return
        put(Cols.chores, entry.copy(status = CHORE_APPROVED, approvedBy = myId, approvedAt = now()))
    }

    fun unapproveChore(entry: ChoreEntry) {
        if (!isParent) return
        val status = if (entry.doneBy.isNotBlank()) CHORE_DONE else CHORE_OPEN
        put(Cols.chores, entry.copy(status = status, approvedBy = "", approvedAt = 0))
    }
}
