package es.criosrango.app

import es.criosrango.shared.account.AccountOrderSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AccountOrderSystemBackTest {
    private fun order(id: Int) = AccountOrderSummary(
        id = id,
        number = "CR-$id",
        status = "completed",
        items = emptyList()
    )

    @Test
    fun systemBackClearsActiveOrderAndAllowsOpeningAnotherOrder() {
        val firstOrder = order(42)
        val secondOrder = order(43)
        val loadedOrders = listOf(firstOrder, secondOrder)

        var selection = AccountOrderSelectionState().select(firstOrder)
        assertEquals(firstOrder, selection.resolveFrom(loadedOrders))

        // Same selection transition used by Android's system/gesture Back and detail header.
        selection = selection.clear()
        assertNull(selection.orderId)
        assertNull(selection.sharedOrder)
        assertNull(selection.resolveFrom(loadedOrders))

        selection = selection.select(secondOrder)
        assertEquals(secondOrder, selection.resolveFrom(loadedOrders))
    }
}
