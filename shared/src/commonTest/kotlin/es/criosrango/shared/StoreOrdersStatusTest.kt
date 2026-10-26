package es.criosrango.shared

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
        assertEquals(StoreOrderStatusKind.REFUNDED, StoreOrderStatusMapper.classify("reembolsado", ""))
        assertEquals(StoreOrderStatusKind.FAILED, StoreOrderStatusMapper.classify("failed", ""))
        assertEquals(StoreOrderStatusKind.UNKNOWN, StoreOrderStatusMapper.classify("custom_status", ""))
    }

    @Test fun nonBlankServerLabelIsPreferredVerbatim() {
        assertEquals("Preparación especial", StoreOrderStatusMapper.visibleLabel("processing", "Preparación especial"))
        assertEquals("processing", StoreOrderStatusMapper.visibleLabel("processing", " "))
    }
}
