package es.criosrango.shared

import es.criosrango.shared.account.AccountOrderItem
import es.criosrango.shared.account.AccountOrderSummary
import es.criosrango.shared.account.AccountClaimOrderResponse

sealed class StoreOrderSelectionResolution {
    data class Resolved(val order: AccountOrderSummary) : StoreOrderSelectionResolution()
    object Pending : StoreOrderSelectionResolution()
    object NotFound : StoreOrderSelectionResolution()
    data class TransientError(val message: String) : StoreOrderSelectionResolution()
}

fun resolveStoreOrderSelection(orderId: Int, state: StoreOrdersState, loadCompletedForCurrentUser: Boolean): StoreOrderSelectionResolution {
    if (orderId <= 0) return StoreOrderSelectionResolution.NotFound
    state.orders.firstOrNull { it.id == orderId }?.let { return StoreOrderSelectionResolution.Resolved(it) }
    if (state.error != null) return StoreOrderSelectionResolution.TransientError(state.error)
    if (state.loading || state.refreshing || !loadCompletedForCurrentUser || state.authenticatedUserId == null) return StoreOrderSelectionResolution.Pending
    return StoreOrderSelectionResolution.NotFound
}

data class StoreReturnRequest(val orderId: Int, val orderNumber: String, val productName: String, val quantity: Int, val variations: List<Pair<String, String>>, val message: String)

fun storeReturnRequests(order: AccountOrderSummary): List<StoreReturnRequest> {
    if (!storeOrderIsReturnEligible(order.status, order.statusLabel) || order.items.isEmpty()) return emptyList()
    return order.items.map { item -> buildStoreReturnRequest(order, item) }
}

fun buildStoreReturnRequest(order: AccountOrderSummary, item: AccountOrderItem): StoreReturnRequest {
    val variations = item.variations.filter { it.name.isNotBlank() && it.value.isNotBlank() }
        .map { (name, value) -> (if (name.equals("Tallas", true)) "Talla" else name) to cleanReturnText(value) }
    val message = buildString {
        append("Hola, quiero solicitar la devolución de un producto de mi pedido #")
        append(order.number.ifBlank { order.id.toString() })
        append(".\n\nProducto: ")
        append(cleanReturnText(item.name))
        append("\nCantidad: ")
        append(item.quantity)
        variations.forEach { (name, value) -> append("\n$name: $value") }
        append("\n\n¿Podéis indicarme cómo proceder?")
    }
    return StoreReturnRequest(order.id, order.number.ifBlank { order.id.toString() }, cleanReturnText(item.name), item.quantity, variations, message)
}

fun storeOrderIsReturnEligible(status: String, label: String): Boolean {
    val normalized = StoreOrderStatusMapper.normalize("$status $label")
    val allowed = listOf("processing", "procesando", "preparacion", "completed", "completado", "shipped", "enviado")
    val denied = listOf("cancelled", "canceled", "cancelado", "failed", "fallido", "refunded", "reembolsado", "pending", "pending payment", "pendiente", "on hold", "en espera")
    return allowed.any { normalized.contains(it) } && denied.none { normalized.contains(it) }
}

private fun cleanReturnText(value: String): String = value
    .replace("&amp;", "&").replace("&#038;", "&").replace("&quot;", "\"")
    .replace("&#8217;", "’").replace("&rsquo;", "’").replace("&nbsp;", " ").trim()

fun encodeStoreWhatsAppMessage(value: String): String {
    val hex = "0123456789ABCDEF"
    return buildString {
        value.encodeToByteArray().forEach { byte ->
            val c = byte.toInt() and 0xFF
            if ((c in 0x41..0x5A) || (c in 0x61..0x7A) || (c in 0x30..0x39) || c == 0x2D || c == 0x2E || c == 0x5F || c == 0x7E) append(c.toChar())
            else { append('%'); append(hex[c shr 4]); append(hex[c and 0x0F]) }
        }
    }
}

suspend fun claimAndRefreshIfSuccessful(
    claim: suspend () -> AccountClaimOrderResponse?,
    refresh: suspend () -> Unit
): AccountClaimOrderResponse? {
    val result = claim()
    if (result?.success == true) refresh()
    return result
}
