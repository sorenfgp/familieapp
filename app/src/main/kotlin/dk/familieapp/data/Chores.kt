package dk.familieapp.data

import java.security.MessageDigest
import java.time.LocalDate

object ChorePlan {
    fun templateEntryId(templateId: String, date: LocalDate): String = "${templateId}_$date"

    /**
     * Ugens pligter fra [start] og 7 dage frem: faste pligter på deres ugedage
     * (med status, hvis nogen har krydset af) plus ad hoc-pligter i ugen.
     */
    fun week(start: LocalDate, templates: List<ChoreTemplate>, entries: List<ChoreEntry>): List<ChoreEntry> {
        val stored = entries.associateBy { it.id }
        val days = (0L..6L).map { start.plusDays(it) }
        val dates = days.map { it.toString() }.toSet()

        val fixed = days.flatMap { day ->
            templates.filter { day.dayOfWeek.value in it.weekdays }.map { template ->
                val id = templateEntryId(template.id, day)
                stored[id] ?: ChoreEntry(
                    id = id,
                    title = template.title,
                    childId = template.childId,
                    date = day.toString(),
                    reward = template.reward,
                    templateId = template.id,
                )
            }
        }
        val fixedIds = fixed.map { it.id }.toSet()
        // Afkrydsede faste pligter vises stadig, selv om skabelonen er ændret eller slettet.
        val orphaned = entries.filter { it.templateId.isNotBlank() && it.date in dates && it.id !in fixedIds }
        val adHoc = entries.filter { it.templateId.isBlank() && it.date in dates }

        return (fixed + orphaned + adHoc).sortedWith(compareBy({ it.date }, { it.title.lowercase() }))
    }
}

object Pins {
    fun hash(household: String, pin: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest("$household:$pin".toByteArray())
            .joinToString("") { "%02x".format(it) }
}
