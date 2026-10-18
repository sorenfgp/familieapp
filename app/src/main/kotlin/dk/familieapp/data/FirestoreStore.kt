package dk.familieapp.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.WriteBatch
import kotlinx.coroutines.tasks.await
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

    override fun <T : Any> observeOne(col: Col<T>, id: String): Flow<T?> = callbackFlow {
        val registration = root.collection(col.name).document(id).addSnapshotListener { doc, _ ->
            if (doc != null) {
                trySend(runCatching { doc.toObject(col.type)?.let { col.withId(it, doc.id) } }.getOrNull())
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

    override suspend fun <T : Any> replaceAll(col: Col<T>, items: List<T>) {
        val ref = root.collection(col.name)
        val current = ref.get().await()
        val wanted = items.map { col.idOf(it) }.toSet()
        val existing = current.documents.associate { doc ->
            doc.id to runCatching { doc.toObject(col.type)?.let { col.withId(it, doc.id) } }.getOrNull()
        }
        val operations = mutableListOf<(WriteBatch) -> Unit>()
        current.documents.filter { it.id !in wanted }.forEach { doc ->
            operations += { batch -> batch.delete(doc.reference) }
        }
        items.filter { existing[col.idOf(it)] != it }.forEach { item ->
            operations += { batch -> batch.set(ref.document(col.idOf(item)), item) }
        }
        // Firestore tillader højst 500 skrivninger pr. batch.
        operations.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { it(batch) }
            batch.commit().await()
        }
    }

    override fun <T : Any> delete(col: Col<T>, ids: List<String>) {
        if (ids.isEmpty()) return
        val batch = db.batch()
        ids.forEach { batch.delete(root.collection(col.name).document(it)) }
        batch.commit()
    }
}
