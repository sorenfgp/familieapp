package dk.familieapp.data

/** Et stykke tekst i et trin. [amount] er sand for de indsatte mængder i parentes. */
data class StepPart(val text: String, val amount: Boolean = false)

/**
 * Sætter ingrediensernes mængde i parentes, hver gang en ingrediens nævnes i fremgangsmåden,
 * fx "Brun oksekødet" → "Brun oksekødet (500 g)". Ændrer kun visningen, ikke den gemte opskrift.
 */
object StepAmounts {
    /** Danske bøjningsendelser, der må stå efter ingrediensens navn ("løg" → "løgene"). */
    private val endings = setOf("", "e", "en", "et", "er", "ene", "erne", "ne", "n", "s", "r", "ens", "ets", "ernes")

    /** Ordstammen for en ingrediens: det vigtigste ord uden flertalsendelse ("Hakkede tomater" → "tomat"). */
    fun stem(name: String): String? {
        val word = Offers.keyword(name) ?: return null
        for (ending in listOf("er", "e", "r")) {
            if (word.endsWith(ending) && word.length - ending.length >= 3) return word.dropLast(ending.length)
        }
        return word
    }

    fun annotate(step: String, ingredients: List<Ingredient>): List<StepPart> {
        val stems = ingredients
            .filter { it.amount.isNotBlank() }
            .mapNotNull { ing -> stem(ing.name)?.let { it to ing.amount.trim() } }
            // Længste stamme først, så "kyllingebryst" vinder over "kylling".
            .sortedByDescending { it.first.length }
        if (stems.isEmpty()) return listOf(StepPart(step))

        val parts = mutableListOf<StepPart>()
        var last = 0
        for (match in Regex("\\p{L}+").findAll(step)) {
            val word = match.value.lowercase()
            val amount = stems.firstOrNull { (stem, _) ->
                word.startsWith(stem) && word.removePrefix(stem) in endings
            }?.second ?: continue
            // Står mængden der allerede, sættes den ikke ind igen.
            val after = step.substring(match.range.last + 1).trimStart()
            if (after.startsWith("(")) continue
            parts += StepPart(step.substring(last, match.range.last + 1))
            parts += StepPart(" ($amount)", amount = true)
            last = match.range.last + 1
        }
        parts += StepPart(step.substring(last))
        return parts.filter { it.text.isNotEmpty() }
    }

    fun annotateText(step: String, ingredients: List<Ingredient>): String =
        annotate(step, ingredients).joinToString("") { it.text }
}
