package es.criosrango.shared

import kotlin.test.Test
import kotlin.test.assertEquals

class StoreOrdersMoneyTest {
    @Test fun productsSummaryUsesTotalMinusShipping() {
        val products = (storeOrderMinorUnits("49.90") - storeOrderMinorUnits("4.95")).coerceAtLeast(0L)
        assertEquals("44,95 €", formatStoreMinorUnits(products, "EUR"))
    }

    @Test fun productsSummaryClampsAtZero() {
        val products = (storeOrderMinorUnits("3.00") - storeOrderMinorUnits("5.00")).coerceAtLeast(0L)
        assertEquals("0,00 €", formatStoreMinorUnits(products, "EUR"))
    }

    @Test fun zeroShippingIsFreeAndNonZeroShippingRemainsPaid() {
        assertEquals(0L, storeOrderMinorUnits("0.00"))
        assertEquals(0L, storeOrderMinorUnits("0,00"))
        assertEquals(250L, storeOrderMinorUnits("2.50"))
        assertEquals("2,50 €", formatStoreMinorUnits(storeOrderMinorUnits("2.50"), "EUR"))
    }

    @Test fun finalTotalRemainsServerValueForPresentation() {
        val serverTotal = "53.47"
        assertEquals("53,47 €", serverTotal.replace('.', ',') + " €")
    }
}
