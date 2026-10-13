package dk.familieapp.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import java.net.URLEncoder
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Et tilbud fra en tilbudsavis. */
data class Offer(
    val heading: String,
    val store: String,
    val price: Double,
    val prePrice: Double?,
    val until: LocalDate?,
    val quantity: String,
)

/** En opskrift og de af dens ingredienser, der er på tilbud (billigste tilbud pr. ingrediens). */
data class RecipeSuggestion(
    val recipe: Recipe,
    val matches: List<Pair<Ingredient, Offer>>,
)

/**
 * Ugens tilbud fra Bilka, Rema 1000, Netto og Meny via Tjek (bag eTilbudsavis).
 * Tjeks API er ikke officielt dokumenteret til offentlig brug og kan ændre sig.
 */
object Offers {
    val STORES = listOf("Bilka", "Rema 1000", "Netto", "Meny")

    // Hillerød. Tilbud inden for 15 km.
    private const val LAT = 55.9267
    private const val LNG = 12.3109
    private const val RADIUS = 15_000

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val tjekTime = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ")

    /** Ord, der ikke siger noget om selve varen. */
    private val fillerWords = setOf(
        "hakket", "hakkede", "frisk", "friske", "stor", "store", "lille", "små", "rød", "røde", "grøn",
        "grønne", "gul", "gule", "hvid", "hvide", "fin", "finthakket", "revet", "revne", "skiver", "skåret",
        "økologisk", "økologiske", "dåse", "dåser", "pakke", "pakker", "tørret", "tørrede", "kogt", "kogte",
        "frosne", "frossen", "eller", "til", "med", "uden", "fra", "evt", "ca", "efter", "smag", "servering",
        "stk", "gram", "liter", "spsk", "tsk", "knsp", "bundt", "pose", "glas",
    )

    /** Det ord, der søges efter for en ingrediens: det længste meningsbærende ord ("Hakket oksekød" → "oksekød"). */
    fun keyword(name: String): String? =
        name.lowercase()
            .split(Regex("[^\\p{L}]+"))
            .filter { it.length >= 3 && it !in fillerWords }
            .maxByOrNull { it.length }

    fun storeOf(name: String): String? =
        STORES.firstOrNull { name.replace(" ", "").contains(it.replace(" ", ""), ignoreCase = true) }

    suspend fun search(keyword: String): List<Offer> = withContext(Dispatchers.IO) {
        val url = "https://squid-api.tjek.com/v2/offers/search?query=${URLEncoder.encode(keyword, "UTF-8")}" +
            "&r_lat=$LAT&r_lng=$LNG&r_radius=$RADIUS&limit=100"
        parse(httpGet(url), today = LocalDate.now()).filter { it.heading.lowercase().contains(keyword) }
    }

    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun JsonObject.num(key: String): Double? = (this[key] as? JsonPrimitive)?.doubleOrNull

    private fun parseDate(text: String?): LocalDate? {
        if (text.isNullOrBlank()) return null
        return runCatching {
            OffsetDateTime.parse(text, tjekTime).atZoneSameInstant(ZoneId.of("Europe/Copenhagen")).toLocalDate()
        }.getOrElse { runCatching { LocalDate.parse(text.take(10)) }.getOrNull() }
    }

    private fun quantityOf(offer: JsonObject): String {
        val quantity = offer.obj("quantity") ?: return ""
        val unit = quantity.obj("unit")?.str("symbol").orEmpty()
        val size = quantity.obj("size")?.num("from")
        val pieces = quantity.obj("pieces")?.num("from")
        fun number(v: Double) = if (v % 1.0 == 0.0) v.toLong().toString() else v.toString().replace('.', ',')
        return when {
            size != null && size > 0 && unit.isNotBlank() && unit != "pcs" -> "${number(size)} $unit"
            pieces != null && pieces > 1 -> "${number(pieces)} stk."
            else -> ""
        }
    }

    /** Tilbud fra de fire kæder, der stadig gælder [today]. */
    fun parse(text: String, today: LocalDate): List<Offer> {
        val root = json.parseToJsonElement(text)
        val list = when (root) {
            is JsonArray -> root.mapNotNull { it as? JsonObject }
            is JsonObject -> (root["offers"] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
            else -> emptyList()
        }
        return list.mapNotNull { o ->
            val storeName = o.obj("branding")?.str("name") ?: o.obj("dealer")?.str("name") ?: return@mapNotNull null
            val store = storeOf(storeName) ?: return@mapNotNull null
            val price = o.obj("pricing")?.num("price") ?: return@mapNotNull null
            Offer(
                heading = o.str("heading").orEmpty(),
                store = store,
                price = price,
                prePrice = o.obj("pricing")?.num("pre_price"),
                until = parseDate(o.str("run_till")),
                quantity = quantityOf(o),
            )
        }.filter { it.until == null || !it.until.isBefore(today) }
    }

    /**
     * Aftensretter sorteret efter, hvor mange af deres ingredienser der er på tilbud.
     * [offersByKeyword] er tilbud fundet for hvert søgeord.
     */
    fun suggest(recipes: List<Recipe>, offersByKeyword: Map<String, List<Offer>>): List<RecipeSuggestion> =
        recipes.filter { it.kind == KIND_DINNER }
            .map { recipe ->
                val matches = recipe.ingredients.mapNotNull { ingredient ->
                    val offer = keyword(ingredient.name)?.let { offersByKeyword[it] }?.minByOrNull { it.price }
                    offer?.let { ingredient to it }
                }
                RecipeSuggestion(recipe, matches)
            }
            .filter { it.matches.isNotEmpty() }
            .sortedWith(
                compareByDescending<RecipeSuggestion> { it.matches.size }
                    .thenByDescending { it.matches.size.toDouble() / it.recipe.ingredients.size.coerceAtLeast(1) }
            )
}
