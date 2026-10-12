package dk.familieapp.data

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Familiens data i Firestore under households/{kode}/{samling}/{id}.
 * Skrivninger afventes ikke: Firestore gemmer dem lokalt med det samme
 * og synkroniserer, når der er net.
 */
class FirestoreStore(householdId: String) : FamilyStore {
    private val db = FirebaseFirestore.getInstance()
    private val root = db.collection("households").document(householdId)

    override fun <T : Any> observe(col: Col<T>): Flow<List<T>> = callbackFlow {
        val registration = root.collection(col.name).addSnapshotListener { snapshot, _ ->
            if (snapshot != null) {
                trySend(
                    snapshot.documents.mapNotNull { doc ->
                        runCatching { doc.toObject(col.type)?.let { col.withId(it, doc.id) } }.getOrNull()
                    }
                )
            }
        }
        awaitClose { registration.remove() }
    }

    override fun <T : Any> save(col: Col<T>, items: List<T>) {
        if (items.isEmpty()) return
        val batch = db.batch()
        items.forEach { batch.set(root.collection(col.name).document(col.idOf(it)), it) }
        batch.commit()
    }

    override fun <T : Any> delete(col: Col<T>, ids: List<String>) {
        if (ids.isEmpty()) return
        val batch = db.batch()
        ids.forEach { batch.delete(root.collection(col.name).document(it)) }
        batch.commit()
    }
}
