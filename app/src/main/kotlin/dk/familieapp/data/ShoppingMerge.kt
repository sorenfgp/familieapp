package dk.familieapp.data

import java.util.Locale

/** En vare, der skal på indkøbslisten. [source] er fx opskriftens navn. */
data class Addition(val name: String, val amount: String, val source: String = "")

/** Samler ens varer på indkøbslisten til én linje. */
object ShoppingMerge {

    /** "Tomat" og "tomater" regnes for samme vare. */
    fun sameName(a: String, b: String): Boolean {
        val x = a.trim().lowercase()
        val y = b.trim().lowercase()
        if (x.isEmpty() || y.isEmpty()) return false
        return x == y || listOf("er", "r", "e").any { x + it == y || y + it == x }
    }

    /**
     * Lægger [additions] ind i listen. Findes varen allerede (og er ikke købt),
     * lægges mængden til den eksisterende linje. Returnerer de linjer, der skal gemmes.
     */
    fun merge(
        existing: List<ShoppingItem>,
        additions: List<Addition>,
        newItem: (Addition) -> ShoppingItem,
    ): List<ShoppingItem> {
        val open = existing.filter { !it.checked }.toMutableList()
        val changed = LinkedHashMap<String, ShoppingItem>()
        for (addition in additions) {
            if (addition.name.isBlank()) continue
            val index = open.indexOfFirst { sameName(it.name, addition.name) }
            if (index >= 0) {
                val current = open[index]
                val sources = (current.allSources() + listOf(addition.source))
                    .filter { it.isNotBlank() }
                    .distinct()
                val updated = current.copy(
                    amount = Amounts.add(current.amount, addition.amount),
                    sources = sources,
                    source = "",
                )
                open[index] = updated
                changed[updated.id] = updated
            } else {
                val item = newItem(addition)
                open += item
                changed[item.id] = item
            }
        }
        return changed.values.toList()
    }

    /**
     * Forkorter opskriftsnavne, så de fylder højst ca. [budget] tegn tilsammen.
     * Jo flere opskrifter, jo kortere hvert navn. Højst tre navne vises.
     */
    fun shortSources(sources: List<String>, budget: Int = 18): String {
        if (sources.isEmpty()) return ""
        val shown = sources.take(3)
        val extra = sources.size - shown.size
        val perName = maxOf(5, (budget - 2 * (shown.size - 1)) / shown.size)
        val text = shown.joinToString(" · ") { name ->
            if (name.length <= perName) name else name.take(perName - 1).trimEnd() + "…"
        }
        return if (extra > 0) "$text +$extra" else text
    }
}

/** Lægger mængder sammen: "500 g" + "1 kg" = "1,5 kg", "2" + "3" = "5". */
object Amounts {
    private val mass = mapOf("g" to 1.0, "gram" to 1.0, "kg" to 1000.0)
    private val volume = mapOf("ml" to 1.0, "cl" to 10.0, "dl" to 100.0, "l" to 1000.0, "liter" to 1000.0)
    private val singular = mapOf(
        "dåser" to "dåse", "pakker" to "pakke", "poser" to "pose", "skiver" to "skive",
        "bakker" to "bakke", "bundter" to "bundt", "stk" to "stk", "styk" to "stk",
    )
    private val plural = mapOf(
        "dåse" to "dåser", "pakke" to "pakker", "pose" to "poser", "skive" to "skiver",
        "bakke" to "bakker", "bundt" to "bundter",
    )

    private data class Quantity(val value: Double, val family: String)

    private val pattern = Regex("""^\s*(\d+(?:[.,]\d+)?|\d+/\d+|[½¼¾])\s*(.*)$""")

    private fun parseNumber(text: String): Double? = when (text) {
        "½" -> 0.5
        "¼" -> 0.25
        "¾" -> 0.75
        else -> if (text.contains('/')) {
            val (a, b) = text.split('/')
            val denominator = b.toDoubleOrNull()
            if (denominator == null || denominator == 0.0) null else a.toDoubleOrNull()?.div(denominator)
        } else {
            text.replace(',', '.').toDoubleOrNull()
        }
    }

    /** "500 g" → 500 i familien "mass". Ukendte enheder bliver deres egen familie. */
    private fun parse(text: String): Quantity? {
        val match = pattern.find(text.trim()) ?: return null
        val number = parseNumber(match.groupValues[1]) ?: return null
        val unit = match.groupValues[2].trim().lowercase().trimEnd('.')
        if (unit.any { it.isDigit() }) return null
        return when {
            unit in mass -> Quantity(number * mass.getValue(unit), "mass")
            unit in volume -> Quantity(number * volume.getValue(unit), "volume")
            else -> Quantity(number, singular[unit] ?: unit)
        }
    }

    private fun number(value: Double): String {
        val rounded = Math.round(value * 100) / 100.0
        return if (rounded % 1.0 == 0.0) {
            rounded.toLong().toString()
        } else {
            String.format(Locale.ROOT, "%.2f", rounded).trimEnd('0').trimEnd('.').replace('.', ',')
        }
    }

    private fun format(q: Quantity): String = when (q.family) {
        "mass" -> if (q.value >= 1000) "${number(q.value / 1000)} kg" else "${number(q.value)} g"
        "volume" -> when {
            q.value >= 1000 -> "${number(q.value / 1000)} l"
            q.value >= 100 -> "${number(q.value / 100)} dl"
            else -> "${number(q.value)} ml"
        }
        "" -> number(q.value)
        else -> {
            val unit = if (q.value > 1) plural[q.family] ?: q.family else q.family
            "${number(q.value)} $unit"
        }
    }

    fun add(current: String, addition: String): String {
        if (addition.isBlank()) return current
        if (current.isBlank()) return addition.trim()
        val extra = parse(addition)
        val parts = current.split(" + ").map { it.trim() }.toMutableList()
        if (extra != null) {
            for (i in parts.indices) {
                val part = parse(parts[i])
                if (part != null && part.family == extra.family) {
                    parts[i] = format(Quantity(part.value + extra.value, part.family))
                    return parts.joinToString(" + ")
                }
            }
        }
        parts += addition.trim()
        return parts.joinToString(" + ")
    }
}
