package dk.familieapp

import dk.familieapp.data.HomeCards
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeCardsTest {
    @Test
    fun customOrderKeepsNewCardsLast() {
        val order = HomeCards.order(listOf(HomeCards.SHOPPING, HomeCards.WEATHER, "ukendt"))
        assertEquals(HomeCards.SHOPPING, order[0])
        assertEquals(HomeCards.WEATHER, order[1])
        assertEquals(HomeCards.all.size, order.size)
    }
}
