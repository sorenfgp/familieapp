package dk.familieapp

import dk.familieapp.data.Categories
import dk.familieapp.data.Dates
import dk.familieapp.data.Ingredient
import dk.familieapp.data.RecipeImporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class RecipeImporterTest {

    @Test
    fun splitsAmountAndUnit() {
        assertEquals(Ingredient("Hakket oksekød", "500 g"), RecipeImporter.splitIngredient("500 g hakket oksekød"))
        assertEquals(Ingredient("Mælk", "1,5 dl"), RecipeImporter.splitIngredient("1,5 dl mælk"))
        assertEquals(Ingredient("Hakkede tomater", "1 dåse"), RecipeImporter.splitIngredient("1 dåse hakkede tomater"))
        assertEquals(Ingredient("Mel", "200 g"), RecipeImporter.splitIngredient("200g mel"))
    }

    @Test
    fun splitsCountWithoutUnit() {
        assertEquals(Ingredient("Løg", "2"), RecipeImporter.splitIngredient("2 løg"))
        assertEquals(Ingredient("Store løg", "2"), RecipeImporter.splitIngredient("2 store løg"))
    }

    @Test
    fun keepsTextWithoutAmount() {
        assertEquals(Ingredient("Salt og peber", ""), RecipeImporter.splitIngredient("salt og peber"))
    }

    @Test
    fun readsRecipeFromGraph() {
        val block = """
            {"@context":"https://schema.org","@graph":[
              {"@type":"WebPage","name":"En side"},
              {"@type":["Recipe"],"name":"Butter chicken &amp; ris","totalTime":"PT40M",
               "recipeYield":"4 personer",
               "recipeIngredient":["500 g kylling","2 løg","salt"],
               "recipeInstructions":[
                 {"@type":"HowToStep","text":"Steg kyllingen."},
                 {"@type":"HowToSection","name":"Sovs","itemListElement":[
                   {"@type":"HowToStep","text":"<p>Tilsæt sovs.</p>"}]}]}
            ]}
        """.trimIndent()

        val recipe = RecipeImporter.parseJsonLd(listOf("ikke json", block), "https://example.com/x")

        assertNotNull(recipe)
        recipe!!
        assertEquals("Butter chicken & ris", recipe.name)
        assertEquals(40, recipe.minutes)
        assertEquals(4, recipe.servings)
        assertEquals(3, recipe.ingredients.size)
        assertEquals(Ingredient("Kylling", "500 g"), recipe.ingredients[0])
        assertEquals(listOf("Steg kyllingen.", "Tilsæt sovs."), recipe.steps)
        assertEquals("https://example.com/x", recipe.sourceUrl)
    }

    @Test
    fun readsPlainTextInstructionsAndSummedTimes() {
        val block = """
            {"@type":"Recipe","name":"Suppe","prepTime":"PT10M","cookTime":"PT20M",
             "recipeYield":6,"recipeIngredient":[],"recipeInstructions":"Kog vand.\nTilsæt grønt."}
        """.trimIndent()

        val recipe = RecipeImporter.parseJsonLd(listOf(block), "https://example.com/y")!!

        assertEquals(30, recipe.minutes)
        assertEquals(6, recipe.servings)
        assertEquals(listOf("Kog vand.", "Tilsæt grønt."), recipe.steps)
    }

    @Test
    fun returnsNullWithoutRecipe() {
        assertNull(RecipeImporter.parseJsonLd(listOf("""{"@type":"Article","name":"x"}"""), "https://example.com"))
    }

    @Test
    fun categorizesCommonItems() {
        assertEquals("Mejeri", Categories.of("Mælk"))
        assertEquals("Kolonial", Categories.of("Hakkede tomater"))
        assertEquals("Frugt og grønt", Categories.of("Gulerødder"))
        assertEquals("Kød og fisk", Categories.of("Hakket oksekød"))
        assertEquals("Andet", Categories.of("Batterier"))
    }

    @Test
    fun formatsDanishDates() {
        val saturday = LocalDate.of(2026, 10, 10)
        assertEquals(LocalDate.of(2026, 10, 5), Dates.weekStart(saturday))
        assertEquals(41, Dates.weekNumber(saturday))
        assertEquals("lørdag 10. oktober", Dates.long(saturday))
        assertEquals("5.–11. oktober", Dates.weekRange(Dates.weekStart(saturday)))
    }
}
