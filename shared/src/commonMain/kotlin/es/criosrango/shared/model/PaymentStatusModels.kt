package es.criosrango.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PaymentStatusResponse(
    @SerialName("order_id") val id: Int = 0,
    @SerialName("status") val status: String = "",
    @SerialName("paid") val paid: Boolean = false,
    @SerialName("needs_payment") val needsPayment: Boolean = true,
    @SerialName("terminal") val terminal: Boolean = false
)
