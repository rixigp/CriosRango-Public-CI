package es.criosrango.shared.account

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AccountLoginRequest(
    val login: String,
    val password: String
)

@Serializable
data class AccountRegisterRequest(
    val email: String,
    val password: String,
    @SerialName("first_name") val firstName: String,
    @SerialName("last_name") val lastName: String,
    val phone: String = ""
)

@Serializable
data class AccountForgotPasswordRequest(
    val login: String
)

@Serializable
data class AccountUser(
    val id: Int,
    val email: String,
    @SerialName("display_name") val displayName: String = "",
    @SerialName("first_name") val firstName: String = "",
    @SerialName("last_name") val lastName: String = ""
)

@Serializable
data class AccountLoginResponse(
    val token: String,
    val user: AccountUser
)

@Serializable
data class AccountMessageResponse(
    val success: Boolean = false,
    val message: String = ""
)

@Serializable
data class AccountMeResponse(
    val user: AccountUser
)

@Serializable
data class AccountCustomerAddress(
    @SerialName("first_name") val firstName: String = "",
    @SerialName("last_name") val lastName: String = "",
    val email: String = "",
    val phone: String = "",
    @SerialName("address_1") val address1: String = "",
    @SerialName("address_2") val address2: String = "",
    val postcode: String = "",
    val city: String = "",
    val state: String = "",
    val country: String = "ES"
)

@Serializable
data class AccountClaimOrderRequest(
    @SerialName("order_id") val orderId: Int,
    @SerialName("order_key") val orderKey: String
)

@Serializable
data class AccountClaimOrderResponse(
    val success: Boolean = false,
    @SerialName("order_id") val orderId: Int = 0,
    @SerialName("customer_id") val customerId: Int = 0
)

@Serializable
data class AccountOrderVariation(
    val name: String = "",
    val value: String = ""
)

@Serializable
data class AccountOrderItem(
    val name: String = "",
    val quantity: Int = 0,
    val variations: List<AccountOrderVariation> = emptyList()
)

@Serializable
data class AccountOrderShippingAddress(
    @SerialName("address_1") val address1: String = "",
    @SerialName("address_2") val address2: String = "",
    val city: String = "",
    val state: String = "",
    val postcode: String = "",
    val country: String = ""
)

@Serializable
data class AccountOrderSummary(
    val id: Int,
    val number: String,
    val status: String,
    @SerialName("status_label") val statusLabel: String = "",
    @SerialName("date_created") val dateCreated: String? = null,
    val total: String = "",
    val currency: String = "EUR",
    @SerialName("payment_method") val paymentMethod: String = "",
    @SerialName("payment_method_title") val paymentMethodTitle: String = "",
    val items: List<AccountOrderItem> = emptyList(),
    val subtotal: String = "",
    @SerialName("shipping_total") val shippingTotal: String = "",
    @SerialName("shipping_method") val shippingMethod: String = "",
    @SerialName("shipping_address") val shippingAddress: AccountOrderShippingAddress = AccountOrderShippingAddress()
)

@Serializable
data class AccountOrdersResponse(
    val orders: List<AccountOrderSummary> = emptyList(),
    val total: Int = 0
)
