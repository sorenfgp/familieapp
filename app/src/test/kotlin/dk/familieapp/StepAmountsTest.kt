package dk.familieapp

import dk.familieapp.data.Ingredient
import dk.familieapp.data.StepAmounts
import org.junit.Assert.assertEquals
import org.junit.Test

class StepAmountsTest {
    private val ingredients = listOf(
        Ingredient("Hakket oksekød", "500 g"),
        Ingredient("Løg", "2"),
        Ingredient("Hakkede tomater", "2 dåser"),
        Ingredient("Mel", "2 spsk"),
        Ingredient("Salt", ""),
    )

    @Test
    fun addsAmountEveryTimeAnIngredientIsMentioned() {
        assertEquals(
            "Hak løgene (2), og brun dem med oksekødet (500 g). Kom resten af løgene (2) i.",
            StepAmounts.annotateText("Hak løgene, og brun dem med oksekødet. Kom resten af løgene i.", ingredients),
        )
        assertEquals(
            "Tilsæt tomaterne (2 dåser) og salt.",
            StepAmounts.annotateText("Tilsæt tomaterne og salt.", ingredients),
        )
    }

    @Test
    fun leavesSimilarWordsAndExistingAmountsAlone() {
        // "mellem" er ikke "mel", og en mængde i parentes sættes ikke ind igen.
        assertEquals(
            "Rør mel (2 spsk) i mellem hver portion.",
            StepAmounts.annotateText("Rør mel i mellem hver portion.", ingredients),
        )
        assertEquals("Rør mel (2 spsk) i.", StepAmounts.annotateText("Rør mel (2 spsk) i.", ingredients))
    }
}
