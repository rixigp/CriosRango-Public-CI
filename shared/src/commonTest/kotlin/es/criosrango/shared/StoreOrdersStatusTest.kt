package es.criosrango.shared

import es.criosrango.shared.account.AccountOrderSummary
import kotlin.test.Test
import kotlin.test.assertEquals

class StoreOrdersStatusTest {
    @Test fun completedVariantsShareGoldenClassification() {
        listOf("completed", "COMPLETED", "completado", "Completado").forEach {
            assertEquals(StoreOrderStatusKind.COMPLETED, StoreOrderStatusMapper.classify(it, ""))
        }
    }

    @Test fun processingVariantsHandleAccentsAndSeparators() {
        listOf("processing", "PROCESSING", "procesando", "preparación", "preparacion", "preparacion_pedido").forEach {
            assertEquals(StoreOrderStatusKind.PROCESSING, StoreOrderStatusMapper.classify(it, ""))
        }
    }

    @Test fun shippedPendingAndOnHoldMapToGoldenKinds() {
        assertEquals(StoreOrderStatusKind.SHIPPED, StoreOrderStatusMapper.classify("shipped", ""))
        assertEquals(StoreOrderStatusKind.SHIPPED, StoreOrderStatusMapper.classify("ENVIADO", ""))
        assertEquals(StoreOrderStatusKind.PENDING, StoreOrderStatusMapper.classify("pending payment", ""))
        assertEquals(StoreOrderStatusKind.PENDING, StoreOrderStatusMapper.classify("pendiente", ""))
        assertEquals(StoreOrderStatusKind.ON_HOLD, StoreOrderStatusMapper.classify("on-hold", ""))
        assertEquals(StoreOrderStatusKind.ON_HOLD, StoreOrderStatusMapper.classify("en_espera", ""))
    }

    @Test fun terminalStatusesAndUnknownMapCorrectly() {
        assertEquals(StoreOrderStatusKind.CANCELLED, StoreOrderStatusMapper.classify("cancelled", ""))
        assertEquals(StoreOrderStatusKind.CANCELLED, StoreOrderStatusMapper.classify("CANCELED", ""))
        assertEquals(StoreOrderStatusKind.REFUNDED, StoreOrderStatusMapper.classify("refunded", ""))
        assertEquals(StoreOrderStatusKind.REFUNDED, StoreOrderStatusMapper.classify("reembolsado", ""))
        assertEquals("Reembolsado", StoreOrderStatusMapper.style("refunded", "Refunded").label)
        assertEquals(0xFFEDE7F3L, StoreOrderStatusMapper.style("reembolsado", "").background)
        assertEquals(StoreOrderStatusKind.FAILED, StoreOrderStatusMapper.classify("failed", ""))
        assertEquals(StoreOrderStatusKind.UNKNOWN, StoreOrderStatusMapper.classify("custom_status", ""))
    }

    @Test fun nonBlankServerLabelIsPreferredVerbatim() {
        assertEquals("Preparación especial", StoreOrderStatusMapper.visibleLabel("processing", "Preparación especial"))
        assertEquals("processing", StoreOrderStatusMapper.visibleLabel("processing", " "))
    }
    @Test fun ordersFiltersUseSharedStatusMapperAndKeepCancelledInAllOnly() {
        val orders = listOf(
            AccountOrderSummary(id = 1, number = "1", status = "processing"),
            AccountOrderSummary(id = 2, number = "2", status = "pending payment"),
            AccountOrderSummary(id = 3, number = "3", status = "completed"),
            AccountOrderSummary(id = 4, number = "4", status = "shipped"),
            AccountOrderSummary(id = 5, number = "5", status = "refunded"),
            AccountOrderSummary(id = 6, number = "6", status = "cancelled"),
            AccountOrderSummary(id = 7, number = "7", status = "failed"),
            AccountOrderSummary(id = 8, number = "8", status = "preparacion_pedido"),
            AccountOrderSummary(id = 9, number = "9", status = "on hold"),
            AccountOrderSummary(id = 10, number = "10", status = "canceled"),
            AccountOrderSummary(id = 11, number = "11", status = "refunded"),
            AccountOrderSummary(id = 12, number = "12", status = "reembolsado")
        )
        assertEquals(orders, filterStoreOrders(orders, StoreOrdersFilter.ALL))
        assertEquals(listOf(1, 8), filterStoreOrders(orders, StoreOrdersFilter.PROCESSING).map { it.id })
        assertEquals(listOf(6, 10), filterStoreOrders(orders, StoreOrdersFilter.CANCELLED).map { it.id })
        assertEquals(listOf(2, 9), filterStoreOrders(orders, StoreOrdersFilter.ON_HOLD).map { it.id })
        assertEquals(listOf(3), filterStoreOrders(orders, StoreOrdersFilter.COMPLETED).map { it.id })
        assertEquals(listOf(5, 11, 12), filterStoreOrders(orders, StoreOrdersFilter.REFUNDED).map { it.id })
    }

    @Test fun returnActionIsEligibleOnlyForCompletedOrders() {
        assertEquals(true, storeOrderIsReturnEligible("completed", ""))
        assertEquals(true, storeOrderIsReturnEligible("completado", ""))
        listOf("processing", "procesando", "preparacion", "pending", "pending payment", "pendiente", "on hold", "en espera", "cancelled", "canceled", "cancelado", "failed", "refunded", "reembolsado", "shipped", "enviado").forEach { status ->
            assertEquals(false, storeOrderIsReturnEligible(status, ""), "status=$status")
        }
    }
}
