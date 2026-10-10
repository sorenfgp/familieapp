package dk.familieapp.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Familiens data i Firestore under households/{kode}/...
 * Skrivninger afventes ikke: Firestore gemmer dem lokalt med det samme
 * og synkroniserer, når der er net.
 */
class FirestoreStore(householdId: String) : FamilyStore {
    private val db = FirebaseFirestore.getInstance()
    private val root = db.collection("households").document(householdId)

    private fun <T : Any> listen(name: String, convert: (DocumentSnapshot) -> T?): Flow<List<T>> =
        callbackFlow {
            val registration = root.collection(name).addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    trySend(snapshot.documents.mapNotNull { doc -> runCatching { convert(doc) }.getOrNull() })
                }
            }
            awaitClose { registration.remove() }
        }

    override val recipes: Flow<List<Recipe>> =
        listen("recipes") { it.toObject(Recipe::class.java)?.copy(id = it.id) }
    override val meals: Flow<List<PlannedMeal>> =
        listen("meals") { it.toObject(PlannedMeal::class.java)?.copy(date = it.id) }
    override val shopping: Flow<List<ShoppingItem>> =
        listen("shopping") { it.toObject(ShoppingItem::class.java)?.copy(id = it.id) }
    override val staples: Flow<List<Staple>> =
        listen("staples") { it.toObject(Staple::class.java)?.copy(id = it.id) }

    override fun saveRecipe(recipe: Recipe) {
        root.collection("recipes").document(recipe.id).set(recipe)
    }

    override fun deleteRecipe(id: String) {
        root.collection("recipes").document(id).delete()
    }

    override fun saveMeal(meal: PlannedMeal) {
        root.collection("meals").document(meal.date).set(meal)
    }

    override fun deleteMeal(date: String) {
        root.collection("meals").document(date).delete()
    }

    override fun saveShoppingItems(items: List<ShoppingItem>) {
        if (items.isEmpty()) return
        val batch = db.batch()
        items.forEach { batch.set(root.collection("shopping").document(it.id), it) }
        batch.commit()
    }

    override fun deleteShoppingItems(ids: List<String>) {
        if (ids.isEmpty()) return
        val batch = db.batch()
        ids.forEach { batch.delete(root.collection("shopping").document(it)) }
        batch.commit()
    }

    override fun saveStaple(staple: Staple) {
        root.collection("staples").document(staple.id).set(staple)
    }

    override fun deleteStaple(id: String) {
        root.collection("staples").document(id).delete()
    }
}
