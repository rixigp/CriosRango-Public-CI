package es.criosrango.shared

import es.criosrango.shared.model.StoreCartCouponTotals
import es.criosrango.shared.model.StoreCartTotals
import es.criosrango.shared.model.consumerDiscount
import kotlin.test.Test
import kotlin.test.assertEquals

class StoreCartCouponTest {
    @Test
    fun consumerDiscountSumsDiscountAndTax() {
        assertEquals(
            "121",
            StoreCartTotals(totalDiscount = "100", totalDiscountTax = "21").consumerDiscount()
        )
        assertEquals(
            "121",
            StoreCartCouponTotals(totalDiscount = "100", totalDiscountTax = "21").consumerDiscount()
        )
    }

    @Test
    fun consumerDiscountDefaultsMissingValuesToZero() {
        assertEquals(
            "0",
            StoreCartTotals(totalDiscount = "0", totalDiscountTax = "0").consumerDiscount()
        )
        assertEquals(
            "21",
            StoreCartCouponTotals(totalDiscount = "0", totalDiscountTax = "21").consumerDiscount()
        )
    }
}
