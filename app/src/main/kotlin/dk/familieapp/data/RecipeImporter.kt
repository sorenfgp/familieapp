package dk.familieapp.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jsoup.Jsoup
import java.time.Duration

class NoRecipeFoundException : Exception("Ingen opskrift fundet på siden")

/**
 * Henter en opskrift fra en webside. De fleste opskriftssider har opskriften
 * indlejret som schema.org/Recipe i et JSON-LD-script; det er det, der læses her.
 */
object RecipeImporter {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"

    fun normalizeUrl(input: String): String {
        val url = input.trim()
        return if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
    }

    suspend fun fetch(inputUrl: String): Recipe = withContext(Dispatchers.IO) {
        val url = normalizeUrl(inputUrl)
        val page = Jsoup.connect(url).userAgent(USER_AGENT).timeout(15_000).get()
        val blocks = page.select("script[type=application/ld+json]").map { it.data() }
        parseJsonLd(blocks, url) ?: throw NoRecipeFoundException()
    }

    fun parseJsonLd(blocks: List<String>, url: String): Recipe? {
        for (block in blocks) {
            val element = runCatching { json.parseToJsonElement(block) }.getOrNull() ?: continue
            val node = findRecipe(element) ?: continue
            return toRecipe(node, url)
        }
        return null
    }

    private fun findRecipe(element: JsonElement): JsonObject? = when (element) {
        is JsonArray -> element.firstNotNullOfOrNull { findRecipe(it) }
        is JsonObject -> if (isRecipe(element)) element else element["@graph"]?.let { findRecipe(it) }
        else -> null
    }

    private fun isRecipe(node: JsonObject): Boolean = when (val type = node["@type"]) {
        is JsonArray -> type.any { it is JsonPrimitive && it.content == "Recipe" }
        is JsonPrimitive -> type.content == "Recipe"
        else -> false
    }

    private fun toRecipe(node: JsonObject, url: String): Recipe {
        val minutes = minutes(node["totalTime"])
            ?: listOfNotNull(minutes(node["prepTime"]), minutes(node["cookTime"])).takeIf { it.isNotEmpty() }?.sum()
            ?: 0
        return Recipe(
            name = clean(firstText(node["name"])),
            minutes = minutes,
            servings = Regex("\\d+").find(firstText(node["recipeYield"]))?.value?.toIntOrNull() ?: 4,
            ingredients = texts(node["recipeIngredient"]).map { splitIngredient(it) },
            steps = steps(node["recipeInstructions"]),
            sourceUrl = url,
            kind = guessKind(node),
            imageUrl = imageOf(node["image"]).orEmpty(),
        )
    }

    /** schema.org-billede: en adresse, en liste eller et ImageObject med "url". */
    private fun imageOf(element: JsonElement?): String? = when (element) {
        is JsonPrimitive -> element.content.takeIf { it.startsWith("http") }
        is JsonArray -> element.firstNotNullOfOrNull { imageOf(it) }
        is JsonObject -> imageOf(element["url"]) ?: imageOf(element["contentUrl"])
        else -> null
    }

    private fun minutes(element: JsonElement?): Int? {
        val text = firstText(element)
        if (text.isBlank()) return null
        return runCatching { Duration.parse(text).toMinutes().toInt() }.getOrNull()?.takeIf { it > 0 }
    }

    private fun firstText(element: JsonElement?): String = texts(element).firstOrNull().orEmpty()

    private fun texts(element: JsonElement?): List<String> = when (element) {
        null, is JsonNull -> emptyList()
        is JsonPrimitive -> listOf(element.content)
        is JsonArray -> element.flatMap { texts(it) }
        else -> emptyList()
    }

    private fun steps(element: JsonElement?): List<String> = when (element) {
        null, is JsonNull -> emptyList()
        is JsonPrimitive -> element.content.split("\n").map { clean(it) }.filter { it.isNotBlank() }
        is JsonArray -> element.flatMap { steps(it) }
        is JsonObject -> when {
            element["itemListElement"] != null -> steps(element["itemListElement"])
            element["text"] != null -> steps(element["text"])
            element["name"] != null -> steps(element["name"])
            else -> emptyList()
        }
        else -> emptyList()
    }

    private val dessertWords = listOf(
        "dessert", "kage", "kager", "småkage", "cookie", "cake", "muffin", "tærte", "pie",
        "pudding", "budding", "mousse", "trifle", "sorbet", "is", "flødeis", "sweet", "brownie",
    )

    /** Dessert, hvis sidens kategori, nøgleord eller navn tyder på det. Ellers aftensmad. */
    private fun guessKind(node: JsonObject): String {
        val text = (texts(node["recipeCategory"]) + texts(node["keywords"]) + texts(node["name"]))
            .joinToString(" ").lowercase()
        val words = text.split(Regex("[^\\p{L}]+")).filter { it.isNotEmpty() }
        val isDessert = dessertWords.any { key ->
            if (key.length <= 2) words.any { it == key } else words.any { it.contains(key) }
        }
        return if (isDessert) KIND_DESSERT else KIND_DINNER
    }

    /** Fjerner HTML-tags og -entiteter. */
    private fun clean(text: String): String = Jsoup.parse(text).text().trim()

    private val units = setOf(
        "g", "gram", "kg", "ml", "cl", "dl", "l", "liter", "tsk", "spsk", "stk", "dåse", "dåser",
        "pakke", "pakker", "fed", "bundt", "knsp", "nip", "håndfuld", "pose", "poser", "glas",
        "skive", "skiver", "bakke", "bakker", "cup", "cups", "tbsp", "tsp", "oz", "lb",
    )

    private val amountPattern = Regex(
        """^\s*((?:ca\.?\s*)?[\d½¼¾⅓⅔]+(?:[.,/\-–]\d+)?(?:\s*[½¼¾])?)\s*(\p{L}+\.?)?\s+(.*)$"""
    )

    /** "500 g hakket oksekød" → navn "Hakket oksekød", mængde "500 g". */
    fun splitIngredient(raw: String): Ingredient {
        val text = clean(raw)
        val match = amountPattern.find(text) ?: return Ingredient(name = capitalize(text))
        val number = match.groupValues[1].trim()
        val word = match.groupValues[2]
        val rest = match.groupValues[3].trim()
        return if (word.isNotEmpty() && word.trimEnd('.').lowercase() in units) {
            Ingredient(name = capitalize(rest), amount = "$number $word")
        } else {
            val name = if (word.isEmpty()) rest else "$word $rest"
            Ingredient(name = capitalize(name), amount = number)
        }
    }

    private fun capitalize(text: String): String = text.replaceFirstChar { it.uppercase() }
}
