package dk.familieapp.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields

/** Gætter butiksafdelingen ud fra varens navn. Første match vinder. */
object Categories {
    val order = listOf("Frugt og grønt", "Kød og fisk", "Mejeri", "Brød", "Kolonial", "Frost", "Andet")

    private val keywords: List<Pair<String, List<String>>> = listOf(
        "Kolonial" to listOf(
            "hakkede tomater", "flåede tomater", "tomatpuré", "tomatpure", "dåse", "kokosmælk",
            "bouillon", "ketchup", "peanutbutter", "nøddesmør",
        ),
        "Frost" to listOf("frost", "frosne", "is"),
        "Brød" to listOf("brød", "boller", "bolle", "toast", "tortilla", "pita", "wraps", "baguette"),
        "Kød og fisk" to listOf(
            "pålæg", "leverpostej", "oksekød", "svinekød", "kylling", "bacon", "pølse", "skinke",
            "laks", "torsk", "fisk", "rejer", "tun", "kød", "fars", "medister", "spegepølse", "kalkun",
        ),
        "Mejeri" to listOf(
            "mælk", "ost", "smør", "yoghurt", "fløde", "skyr", "æg", "creme fraiche", "cremefraiche",
            "mozzarella", "parmesan", "feta", "kærnemælk",
        ),
        "Frugt og grønt" to listOf(
            "gulerod", "gulerødder", "salat", "tomat", "løg", "agurk", "æble", "banan", "kartof",
            "peberfrugt", "citron", "squash", "broccoli", "spinat", "porre", "champignon", "avocado",
            "pære", "appelsin", "bær", "persille", "basilikum", "ingefær", "kål", "selleri", "majs",
            "frugt", "chili", "lime", "rødbede", "ærter", "druer", "melon", "dild", "purløg", "koriander",
        ),
        "Kolonial" to listOf(
            "pasta", "ris", "mel", "sukker", "havregryn", "müsli", "mysli", "cornflakes", "olie",
            "eddike", "krydderi", "salt", "peber", "karry", "paprika", "kanel", "lasagneplader",
            "spaghetti", "nudler", "bønner", "linser", "kikærter", "rosiner", "nødder", "mandler",
            "marmelade", "honning", "kaffe", "te", "kakao", "chokolade", "kiks", "gær", "soja", "sennep",
            "mayonnaise", "remoulade", "juice", "saft", "sodavand",
        ),
    )

    fun of(name: String): String {
        val text = name.lowercase()
        val words = text.split(Regex("[^\\p{L}]+")).filter { it.isNotEmpty() }
        return keywords.firstOrNull { (_, keys) -> keys.any { matches(it, text, words) } }?.first ?: "Andet"
    }

    /** Korte nøgleord skal passe på et helt ord, så "te" ikke rammer "batterier". */
    private fun matches(key: String, text: String, words: List<String>): Boolean = when {
        key.contains(' ') -> text.contains(key)
        key.length <= 2 -> words.any { it == key }
        key.length == 3 -> words.any { it == key || it.endsWith(key) }
        else -> words.any { it.contains(key) }
    }
}

/** Danske datoformater. */
object Dates {
    val dayShort = listOf("Man", "Tirs", "Ons", "Tors", "Fre", "Lør", "Søn")
    val dayLong = listOf("mandag", "tirsdag", "onsdag", "torsdag", "fredag", "lørdag", "søndag")
    val months = listOf(
        "januar", "februar", "marts", "april", "maj", "juni",
        "juli", "august", "september", "oktober", "november", "december",
    )

    fun weekStart(date: LocalDate): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** Madplanens uge går fra søndag til lørdag, så søndagen kommer med i ugens indkøb. */
    fun planWeekStart(date: LocalDate): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))

    fun weekNumber(date: LocalDate): Int = date.get(WeekFields.ISO.weekOfWeekBasedYear())

    fun dayName(date: LocalDate): String = dayLong[date.dayOfWeek.value - 1]

    fun dayAbbr(date: LocalDate): String = dayShort[date.dayOfWeek.value - 1]

    /** "5. okt." */
    fun short(date: LocalDate): String =
        "${date.dayOfMonth}. ${months[date.monthValue - 1].take(3)}."

    /** "lørdag 10. oktober" */
    fun long(date: LocalDate): String =
        "${dayName(date)} ${date.dayOfMonth}. ${months[date.monthValue - 1]}"

    /** "5.–11. oktober" */
    fun weekRange(start: LocalDate): String {
        val end = start.plusDays(6)
        return if (start.month == end.month) {
            "${start.dayOfMonth}.–${end.dayOfMonth}. ${months[end.monthValue - 1]}"
        } else {
            "${short(start)} – ${short(end)}"
        }
    }
}
