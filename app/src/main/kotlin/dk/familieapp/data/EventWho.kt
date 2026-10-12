package dk.familieapp.data

/**
 * Hvem en aftale gælder for. Gemmes som en linje i aftalens beskrivelse, fx
 * "Hvem: Ida, Noah" eller "Hvem: Alle", så den også kan læses i Google Kalender.
 */
object EventWho {
    const val ALL = "Alle"
    private const val PREFIX = "Hvem:"

    private fun isWhoLine(line: String) = line.trim().startsWith(PREFIX, ignoreCase = true)

    /** Navnene fra "Hvem"-linjen, eller null, hvis aftalen ikke har en. */
    fun parse(description: String): List<String>? =
        description.lines().lastOrNull { isWhoLine(it) }
            ?.trim()
            ?.drop(PREFIX.length)
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.takeIf { it.isNotEmpty() }

    fun isAll(who: List<String>?): Boolean = who?.any { it.equals(ALL, ignoreCase = true) } == true

    /** Beskrivelsen uden "Hvem"-linjen. */
    fun strip(description: String): String =
        description.lines().filterNot { isWhoLine(it) }.joinToString("\n").trim()

    /** Erstatter "Hvem"-linjen. Tom eller null [who] fjerner den. */
    fun apply(description: String, who: List<String>?): String {
        val kept = strip(description)
        if (who.isNullOrEmpty()) return kept
        val line = "$PREFIX ${who.joinToString(", ")}"
        return if (kept.isBlank()) line else "$kept\n$line"
    }
}
