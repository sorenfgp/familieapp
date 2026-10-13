package dk.familieapp

import dk.familieapp.data.Ingredient
import dk.familieapp.data.KIND_DESSERT
import dk.familieapp.data.Offer
import dk.familieapp.data.Offers
import dk.familieapp.data.Recipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class OffersTest {
    @Test
    fun picksMeaningfulKeyword() {
        assertEquals("oksekød", Offers.keyword("Hakket oksekød"))
        assertEquals("tomater", Offers.keyword("Hakkede tomater"))
        assertEquals("kyllingebryst", Offers.keyword("Kyllingebryst"))
        assertNull(Offers.keyword("Salt"))
    }

    @Test
    fun readsTjekOffersFromTheFourChainsOnly() {
        val text = """
            [
              {"heading":"Hakket oksekød 8-12%","pricing":{"price":35.0,"pre_price":49.95},
               "run_till":"2026-10-18T21:59:59+0000","branding":{"name":"Netto"},
               "quantity":{"unit":{"symbol":"g"},"size":{"from":500,"to":500},"pieces":{"from":1,"to":1}}},
              {"heading":"Oksekød","pricing":{"price":30.0},"run_till":"2026-10-18T21:59:59+0000","branding":{"name":"Lidl"}},
              {"heading":"Oksekød gammelt","pricing":{"price":20.0},"run_till":"2026-10-01T21:59:59+0000","branding":{"name":"REMA 1000"}}
            ]
        """.trimIndent()
        val offers = Offers.parse(text, today = LocalDate.of(2026, 10, 13))
        assertEquals(1, offers.size)
        val offer = offers[0]
        assertEquals("Netto", offer.store)
        assertEquals(35.0, offer.price, 0.001)
        assertEquals("500 g", offer.quantity)
        assertEquals(LocalDate.of(2026, 10, 18), offer.until)
    }

    @Test
    fun ranksDinnersByNumberOfIngredientsOnOffer() {
        fun offer(store: String, price: Double) = Offer("x", store, price, null, null, "")
        val lasagne = Recipe(id = "1", name = "Lasagne", ingredients = listOf(
            Ingredient("Hakket oksekød", "500 g"), Ingredient("Lasagneplader"), Ingredient("Mælk")))
        val karry = Recipe(id = "2", name = "Kylling i karry", ingredients = listOf(
            Ingredient("Kyllingebryst"), Ingredient("Ris")))
        val kage = Recipe(id = "3", name = "Kage", kind = KIND_DESSERT, ingredients = listOf(Ingredient("Mælk")))
        val offers = mapOf(
            "oksekød" to listOf(offer("Netto", 35.0), offer("Bilka", 32.0)),
            "lasagneplader" to listOf(offer("Meny", 12.0)),
            "kyllingebryst" to listOf(offer("Rema 1000", 45.0)),
            "mælk" to listOf(offer("Netto", 10.0)),
        )
        val suggestions = Offers.suggest(listOf(karry, lasagne, kage), offers)
        assertEquals(listOf("Lasagne", "Kylling i karry"), suggestions.map { it.recipe.name })
        assertEquals("Bilka", suggestions[0].matches.first().second.store)
    }
}
