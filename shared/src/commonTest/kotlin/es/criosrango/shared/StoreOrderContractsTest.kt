package es.criosrango.shared

import es.criosrango.shared.account.AccountOrderItem
import es.criosrango.shared.account.AccountOrderSummary
import es.criosrango.shared.account.AccountOrderVariation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import es.criosrango.shared.account.AccountClaimOrderResponse

class StoreOrderContractsTest {
    private fun order(status: String = "completed", items: List<AccountOrderItem> = listOf(AccountOrderItem(name = "Vestido azul", quantity = 1))) =
        AccountOrderSummary(id = 42, number = "CR-1042", status = status, items = items)
    @Test fun loadedOrderResolvesById() {
        val order = order()
        val result = resolveStoreOrderSelection(42, StoreOrdersState(orders = listOf(order), authenticatedUserId = 7), true)
        assertEquals(order, assertIs<StoreOrderSelectionResolution.Resolved>(result).order)
    }
    @Test fun unresolvedWhileLoadingIsPending() {
        assertIs<StoreOrderSelectionResolution.Pending>(resolveStoreOrderSelection(42, StoreOrdersState(loading = true, authenticatedUserId = 7), false))
    }
    @Test fun notFoundAfterSuccessfulLoadIsNotFound() {
        assertIs<StoreOrderSelectionResolution.NotFound>(resolveStoreOrderSelection(42, StoreOrdersState(authenticatedUserId = 7), true))
    }
    @Test fun transientErrorDoesNotDiscardRequestedId() {
        assertIs<StoreOrderSelectionResolution.TransientError>(resolveStoreOrderSelection(42, StoreOrdersState(error = "temporal", authenticatedUserId = 7), false))
    }
    @Test fun oneProductBuildsProductSpecificRequest() {
        val request = storeReturnRequests(order(items = listOf(AccountOrderItem(name = "Vestido azul", quantity = 2)))).single()
        assertTrue(request.message.contains("#CR-1042"))
        assertTrue(request.message.contains("Producto: Vestido azul"))
        assertTrue(request.message.contains("Cantidad: 2"))
    }
    @Test fun multipleProductsProduceSelectableProductRequests() {
        val requests = storeReturnRequests(order(items = listOf(AccountOrderItem(name = "Vestido azul", quantity = 1), AccountOrderItem(name = "Zapatos", quantity = 2))))
        assertEquals(listOf("Vestido azul", "Zapatos"), requests.map { it.productName })
        assertTrue(requests[1].message.contains("Producto: Zapatos"))
    }
    @Test fun selectedVariationIsIncludedInPayload() {
        val request = storeReturnRequests(order(items = listOf(AccountOrderItem(name = "Vestido azul", quantity = 1, variations = listOf(AccountOrderVariation("Tallas", "M")))))).single()
        assertTrue(request.message.contains("Talla: M"))
    }
    @Test fun cancelledAndRefundedOrdersCannotRequestReturn() {
        assertTrue(storeReturnRequests(order(status = "cancelled")).isEmpty())
        assertTrue(storeReturnRequests(order(status = "refunded")).isEmpty())
    }
    @Test fun ineligibleOrderProducesNoReturnRequest() {
        assertTrue(storeReturnRequests(order(status = "pending")).isEmpty())
    }

    @Test fun successfulPendingClaimRefreshesSharedOrders() = runTest {
        var refreshCount = 0
        val response = claimAndRefreshIfSuccessful(
            claim = { AccountClaimOrderResponse(success = true, orderId = 42, customerId = 7) },
            refresh = { refreshCount++ }
        )
        assertTrue(response?.success == true)
        assertEquals(1, refreshCount)
    }

    @Test fun failedPendingClaimDoesNotRefreshSharedOrders() = runTest {
        var refreshCount = 0
        val response = claimAndRefreshIfSuccessful(
            claim = { AccountClaimOrderResponse(success = false, orderId = 42, customerId = 0) },
            refresh = { refreshCount++ }
        )
        assertTrue(response?.success == false)
        assertEquals(0, refreshCount)
    }

    @Test fun pendingClaimFailurePropagatesWithoutRefreshing() = runTest {
        var refreshCount = 0
        val result = runCatching {
            claimAndRefreshIfSuccessful(
                claim = { error("claim failed") },
                refresh = { refreshCount++ }
            )
        }
        assertTrue(result.isFailure)
        assertEquals(0, refreshCount)
    }
}
