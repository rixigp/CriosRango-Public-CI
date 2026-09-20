package es.criosrango.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CustomerAddress(
    @SerialName("first_name") val firstName: String = "",
    @SerialName("last_name") val lastName: String = "",
    val email: String = "",
    val phone: String = "",
    @SerialName("address_1") val address1: String = "",
    @SerialName("address_2") val address2: String = "",
    val postcode: String = "",
    val city: String = "",
    val state: String = "",
    val country: String = ""
)

@Serializable
data class UpdateCustomerRequest(
    @SerialName("billing_address") val billingAddress: CustomerAddress,
    @SerialName("shipping_address") val shippingAddress: CustomerAddress
)

@Serializable
data class SelectShippingRateRequest(
    @SerialName("package_id") val packageId: Int,
    @SerialName("rate_id") val rateId: String
)

@Serializable
data class CheckoutResponse(
    @SerialName("order_id") val orderId: Int? = null,
    @SerialName("order_key") val orderKey: String? = null,
    @SerialName("order_number") val orderNumber: String? = null,
    val status: String? = null,
    @SerialName("payment_method") val paymentMethod: String? = null,
    @SerialName("payment_methods") val paymentMethods: List<String> = emptyList(),
    @SerialName("payment_requirements") val paymentRequirements: List<String> = emptyList(),
    @SerialName("redirect_url") val redirectUrl: String? = null,
    @SerialName("payment_result") val paymentResult: PaymentResult? = null,
    @SerialName("__experimentalCart") val experimentalCart: ExperimentalCart? = null,
    val totals: StoreCartTotals = StoreCartTotals(),
    val errors: List<StoreCartError> = emptyList()
)

@Serializable
data class PaymentResult(
    @SerialName("payment_status") val paymentStatus: String? = null,
    @SerialName("redirect_url") val redirectUrl: String? = null,
    @SerialName("payment_url") val paymentUrl: String? = null,
    @SerialName("payment_details") val paymentDetails: List<PaymentDetail> = emptyList()
)

@Serializable
data class PaymentDetail(
    val key: String = "",
    val value: String = ""
)

@Serializable
data class ExperimentalCart(
    @SerialName("payment_methods") val paymentMethods: List<String> = emptyList(),
    @SerialName("payment_requirements") val paymentRequirements: List<String> = emptyList(),
    val totals: StoreCartTotals = StoreCartTotals()
)

@Serializable
data class CreateOrderRequest(
    @SerialName("payment_method") val paymentMethod: String,
    @SerialName("billing_address") val billingAddress: CustomerAddress,
    @SerialName("shipping_address") val shippingAddress: CustomerAddress,
    @SerialName("shipping_rate") val shippingRate: String? = null,
    @SerialName("expected_total") val expectedTotal: String? = null,
    @SerialName("payment_data") val paymentData: Map<String, String> = emptyMap(),
    @SerialName("customer_note") val customerNote: String? = null
)

enum class CheckoutPaymentKind { CARD, BIZUM }

@Serializable
data class CheckoutPaymentOption(
    val kind: CheckoutPaymentKind,
    val gatewayId: String
)

fun CheckoutResponse.rawPaymentGatewayIds(): List<String> =
    (paymentMethods + experimentalCart?.paymentMethods.orEmpty())
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinct()

/**
 * Maps only the gateways supported by the app. Cecabank is the sole card gateway.
 * Bizum/cheque are historical aliases for the same manual Bizum flow; when both
 * are returned, bizum is preferred as the canonical gateway id.
 */
fun normalizePaymentGatewayIds(raw: List<String>): List<CheckoutPaymentOption> {
    val gateways = raw.map(String::trim).filter(String::isNotBlank).distinct()
    val result = mutableListOf<CheckoutPaymentOption>()
    if ("cecabank_gateway" in gateways) {
        result += CheckoutPaymentOption(CheckoutPaymentKind.CARD, "cecabank_gateway")
    }
    val bizumGateway = when {
        "bizum" in gateways -> "bizum"
        "cheque" in gateways -> "cheque"
        else -> null
    }
    if (bizumGateway != null) {
        result += CheckoutPaymentOption(CheckoutPaymentKind.BIZUM, bizumGateway)
    }
    return result
}

fun CheckoutResponse.supportedPaymentOptions(): List<CheckoutPaymentOption> =
    normalizePaymentGatewayIds(rawPaymentGatewayIds())
