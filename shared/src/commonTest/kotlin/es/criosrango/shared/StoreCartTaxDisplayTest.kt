package es.criosrango.shared

import es.criosrango.shared.model.ProductPrices
import es.criosrango.shared.model.StoreCartLine
import es.criosrango.shared.model.StoreCartLineTotals
import es.criosrango.shared.model.StoreCartTotals
import es.criosrango.shared.model.consumerLineTotal
import es.criosrango.shared.model.consumerShipping
import es.criosrango.shared.model.consumerSubtotal
import es.criosrango.shared.model.consumerTotal
import es.criosrango.shared.model.consumerUnitPrice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StoreCartTaxDisplayTest {
    @Test
    fun linePriceUsesServerUnitPriceAndAddsTaxToFinalLineAmount() {
        val line = StoreCartLine(
            prices = ProductPrices(price = "1250"),
            totals = StoreCartLineTotals(linePrice = "1200", lineTotal = "1000", lineTotalTax = "210")
        )

        assertEquals("1250", line.consumerUnitPrice())
        assertEquals("1210", line.consumerLineTotal())
    }

    @Test
    fun totalsIncludeTaxExactlyOnce() {
        val totals = StoreCartTotals(
            totalItems = "10000",
            totalItemsTax = "2100",
            totalDiscount = "1000",
            totalDiscountTax = "210",
            totalShipping = "500",
            totalShippingTax = "105",
            totalPrice = "9500",
            totalPriceTax = "1995"
        )

        assertEquals("12100", totals.consumerSubtotal())
        assertEquals("605", totals.consumerShipping())
        assertEquals("11495", totals.consumerTotal())
    }

    @Test
    fun shippingRemainsUncalculatedWhenServerOmitsIt() {
        assertNull(StoreCartTotals(totalShipping = null).consumerShipping())
    }
}
