package dk.familieapp.data

import android.app.Application
import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.security.SecureRandom

/**
 * Holder styr på, om Firebase er sat op, og hvilken familie telefonen hører til.
 * En familie er et dokument i households/{kode}; koden deles med resten af familien.
 */
class Session(app: Application) {
    /** Falsk, når appen er bygget uden google-services.json. */
    val firebaseEnabled: Boolean = FirebaseApp.getApps(app).isNotEmpty()

    private val prefs = app.getSharedPreferences("session", Context.MODE_PRIVATE)
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    var householdId: String? = prefs.getString("household", null)
        private set

    private fun remember(code: String?) {
        householdId = code
        prefs.edit().putString("household", code).apply()
    }

    private suspend fun ensureSignedIn(): String {
        val user = auth.currentUser ?: auth.signInAnonymously().await().user
        return user?.uid ?: error("Kunne ikke logge ind.")
    }

    suspend fun createHousehold(name: String): String = withTimeout(20_000) {
        val uid = ensureSignedIn()
        val code = randomCode()
        db.collection("households").document(code).set(
            mapOf(
                "name" to name,
                "members" to listOf(uid),
                "createdAt" to System.currentTimeMillis(),
            )
        ).await()
        remember(code)
        code
    }

    suspend fun joinHousehold(rawCode: String) {
        val code = rawCode.trim().uppercase()
        require(code.length == CODE_LENGTH) { "Koden skal være på $CODE_LENGTH tegn." }
        withTimeout(20_000) {
            val uid = ensureSignedIn()
            val ref = db.collection("households").document(code)
            // Læsning lykkes kun, hvis telefonen allerede er medlem.
            val alreadyMember = try {
                ref.get().await().exists()
            } catch (e: com.google.firebase.FirebaseException) {
                false
            }
            if (!alreadyMember) ref.update("members", FieldValue.arrayUnion(uid)).await()
        }
        remember(code)
    }

    fun leaveHousehold() = remember(null)

    private fun randomCode(): String {
        val random = SecureRandom()
        return (1..CODE_LENGTH).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")
    }

    private companion object {
        const val CODE_LENGTH = 8
        const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    }
}
