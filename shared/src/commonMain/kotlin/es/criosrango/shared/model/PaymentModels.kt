package es.criosrango.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OrderStatusResponse(
    @SerialName("order_id") val orderId: Int = 0,
    val status: String = "",
    val paid: Boolean = false,
    @SerialName("needs_payment") val needsPayment: Boolean = true,
    val terminal: Boolean = false
)

@Serializable
data class PendingPayment(
    @SerialName("order_id") val orderId: Int,
    @SerialName("order_key") val orderKey: String
)

sealed interface PaymentReturn {
    data class Ok(val orderId: Int?) : PaymentReturn
    data class Cancelled(val orderId: Int?) : PaymentReturn
    data object Unknown : PaymentReturn
}

enum class PaymentState { PENDING, PAID, FAILED, CANCELLED, UNKNOWN_ERROR }
enum class PaymentVerificationResult { PAID, FAILED, PENDING, ERROR }

fun classifyPaymentStatus(response: OrderStatusResponse): PaymentVerificationResult {
    val status = response.status.trim().lowercase()
    if (response.paid) return PaymentVerificationResult.PAID
    if (response.terminal || status == "cancelled" || status == "failed" || status == "refunded") {
        return PaymentVerificationResult.FAILED
    }
    return PaymentVerificationResult.PENDING
}

fun parsePaymentReturn(rawUrl: String): PaymentReturn {
    val value = rawUrl.trim()
    if (value.isBlank()) return PaymentReturn.Unknown
    val parts = value.split("?", limit = 2)
    val base = parts.first()
    val isHttps = base == "https://criosrango.es/app-payment-return" ||
        base.startsWith("https://criosrango.es/app-payment-return/") ||
        base == "https://www.criosrango.es/app-payment-return" ||
        base.startsWith("https://www.criosrango.es/app-payment-return/")
    val isCustom = base == "criosrango://payment-return" || base.startsWith("criosrango://payment-return/")
    if (!isHttps && !isCustom) return PaymentReturn.Unknown
    val query = parts.getOrNull(1).orEmpty().substringBefore("#")
    val params = query.split("&").mapNotNull { item ->
        val pair = item.split("=", limit = 2)
        if (pair.size != 2) null else decode(pair[0]) to decode(pair[1])
    }.toMap()
    val orderId = params["order_id"]?.toIntOrNull()
    return when (params["result"]?.lowercase()) {
        "ok" -> PaymentReturn.Ok(orderId)
        "cancel" -> PaymentReturn.Cancelled(orderId)
        else -> PaymentReturn.Unknown
    }
}

private fun decode(value: String): String =
    value.replace("+", " ").replace("%2F", "/").replace("%3A", ":").replace("%3F", "?")
        .replace("%3D", "=").replace("%26", "&").replace("%25", "%")
