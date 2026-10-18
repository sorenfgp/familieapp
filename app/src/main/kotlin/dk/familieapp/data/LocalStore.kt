package dk.familieapp.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.io.File

/**
 * Gemmer alt i én JSON-fil på telefonen: samling → id → dokument.
 * Bruges, når appen er bygget uden Firebase.
 */
class LocalStore(private val file: File) : FamilyStore {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val state = MutableStateFlow(load())

    private fun load(): Map<String, Map<String, JsonElement>> = runCatching {
        json.parseToJsonElement(file.readText()).jsonObject.mapValues { (_, value) -> value.jsonObject.toMap() }
    }.getOrElse { emptyMap() }

    @Synchronized
    private fun update(name: String, change: (Map<String, JsonElement>) -> Map<String, JsonElement>) {
        val all = state.value
        state.value = all + (name to change(all[name].orEmpty()))
        runCatching {
            file.writeText(JsonObject(state.value.mapValues { JsonObject(it.value) }).toString())
        }
    }

    override fun <T : Any> observe(col: Col<T>): Flow<List<T>> = state.map { all ->
        all[col.name].orEmpty().mapNotNull { (id, element) ->
            runCatching { col.withId(json.decodeFromJsonElement(col.serializer, element), id) }.getOrNull()
        }
    }

    override fun <T : Any> observeOne(col: Col<T>, id: String): Flow<T?> = state.map { all ->
        all[col.name]?.get(id)?.let { element ->
            runCatching { col.withId(json.decodeFromJsonElement(col.serializer, element), id) }.getOrNull()
        }
    }

    override fun <T : Any> save(col: Col<T>, items: List<T>) {
        if (items.isEmpty()) return
        update(col.name) { docs ->
            docs + items.associate { col.idOf(it) to json.encodeToJsonElement(col.serializer, it) }
        }
    }

    override suspend fun <T : Any> replaceAll(col: Col<T>, items: List<T>) {
        update(col.name) {
            items.associate { col.idOf(it) to json.encodeToJsonElement(col.serializer, it) }
        }
    }

    override fun <T : Any> delete(col: Col<T>, ids: List<String>) {
        if (ids.isEmpty()) return
        update(col.name) { docs -> docs - ids.toSet() }
    }
}
