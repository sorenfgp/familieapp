package dk.familieapp

import dk.familieapp.data.Addition
import dk.familieapp.data.Amounts
import dk.familieapp.data.CHORE_DONE
import dk.familieapp.data.CHORE_OPEN
import dk.familieapp.data.ChoreEntry
import dk.familieapp.data.ChorePlan
import dk.familieapp.data.ChoreTemplate
import dk.familieapp.data.Dates
import dk.familieapp.data.ShoppingItem
import dk.familieapp.data.ShoppingMerge
import dk.familieapp.data.onDate
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ShoppingAndChoresTest {

    private var counter = 0
    private fun newItem(a: Addition) = ShoppingItem(
        id = "id${counter++}",
        name = a.name,
        amount = a.amount,
        sources = listOf(a.source).filter { it.isNotBlank() },
    )

    @Test
    fun addsAmounts() {
        assertEquals("5", Amounts.add("2", "3"))
        assertEquals("1,5 kg", Amounts.add("500 g", "1 kg"))
        assertEquals("5 dl", Amounts.add("2 dl", "3 dl"))
        assertEquals("3 dåser", Amounts.add("1 dåse", "2 dåser"))
        assertEquals("1 pakke + 200 g", Amounts.add("1 pakke", "200 g"))
        assertEquals("1 pakke + 500 g", Amounts.add("1 pakke + 200 g", "300 g"))
        assertEquals("2", Amounts.add("", "2"))
        assertEquals("2", Amounts.add("2", ""))
    }

    @Test
    fun mergesSameIngredientFromTwoRecipes() {
        val changed = ShoppingMerge.merge(
            existing = emptyList(),
            additions = listOf(Addition("Æg", "2", "Lasagne"), Addition("æg", "3", "Pandekager")),
            newItem = ::newItem,
        )
        assertEquals(1, changed.size)
        assertEquals("5", changed[0].amount)
        assertEquals(listOf("Lasagne", "Pandekager"), changed[0].sources)
    }

    @Test
    fun mergesIntoOpenItemButNotIntoBoughtItem() {
        val open = ShoppingItem(id = "a", name = "Tomater", amount = "4", sources = listOf("Salat"))
        val bought = ShoppingItem(id = "b", name = "Mælk", amount = "1 l", checked = true)
        val changed = ShoppingMerge.merge(
            existing = listOf(open, bought),
            additions = listOf(Addition("Tomat", "2", "Suppe"), Addition("Mælk", "5 dl", "Suppe")),
            newItem = ::newItem,
        )
        assertEquals(2, changed.size)
        val tomatoes = changed.first { it.id == "a" }
        assertEquals("6", tomatoes.amount)
        assertEquals(listOf("Salat", "Suppe"), tomatoes.sources)
        val milk = changed.first { it.id != "a" }
        assertEquals("5 dl", milk.amount)
    }

    @Test
    fun shortensRecipeNamesMoreWhenThereAreMore() {
        assertEquals("Lasagne med salat", ShoppingMerge.shortSources(listOf("Lasagne med salat")))
        assertEquals(
            "Lasagne… · Kylling…",
            ShoppingMerge.shortSources(listOf("Lasagne med salat", "Kylling i karry")),
        )
        assertEquals(
            "Pizza · Tacos · Suppe +1",
            ShoppingMerge.shortSources(listOf("Pizza", "Tacos", "Suppe", "Lasagne")),
        )
    }

    @Test
    fun severalDishesOnOneDayKeepTheirOrder() {
        val meals = listOf(
            dk.familieapp.data.PlannedMeal(date = "2026-10-12", title = "Is", id = "b", order = 2),
            dk.familieapp.data.PlannedMeal(date = "2026-10-12", title = "Tapas", id = "a", order = 1),
            dk.familieapp.data.PlannedMeal(date = "2026-10-13", title = "Suppe", id = "c", order = 3),
        )
        assertEquals(listOf("Tapas", "Is"), meals.onDate("2026-10-12").map { it.title })
        // Ældre retter uden eget id bruger datoen som id.
        val old = dk.familieapp.data.PlannedMeal(date = "2026-10-01", title = "Lasagne")
        assertEquals("2026-10-01", dk.familieapp.data.Cols.meals.idOf(old))
    }

    @Test
    fun mealPlanWeekStartsOnSunday() {
        assertEquals(LocalDate.of(2026, 10, 11), Dates.planWeekStart(LocalDate.of(2026, 10, 12)))
        assertEquals(LocalDate.of(2026, 10, 11), Dates.planWeekStart(LocalDate.of(2026, 10, 11)))
        assertEquals(LocalDate.of(2026, 10, 11), Dates.planWeekStart(LocalDate.of(2026, 10, 17)))
    }

    @Test
    fun buildsChoreWeekFromTemplatesAndAdHoc() {
        val monday = LocalDate.of(2026, 10, 12)
        val template = ChoreTemplate(id = "t1", title = "Tøm opvaskemaskinen", childId = "ida", weekdays = listOf(1, 3))
        val doneMonday = ChoreEntry(
            id = ChorePlan.templateEntryId("t1", monday),
            title = "Tøm opvaskemaskinen",
            childId = "ida",
            date = monday.toString(),
            templateId = "t1",
            status = CHORE_DONE,
            doneBy = "ida",
        )
        val adHocFriday = ChoreEntry(id = "x", title = "Rydde op", childId = "noah", date = "2026-10-16")
        val adHocNextWeek = ChoreEntry(id = "y", title = "Støvsuge", childId = "noah", date = "2026-10-20")

        val week = ChorePlan.week(monday, listOf(template), listOf(doneMonday, adHocFriday, adHocNextWeek))

        assertEquals(listOf("2026-10-12", "2026-10-14", "2026-10-16"), week.map { it.date })
        assertEquals(CHORE_DONE, week[0].status)
        assertEquals(CHORE_OPEN, week[1].status)
        assertEquals("x", week[2].id)
    }
}
