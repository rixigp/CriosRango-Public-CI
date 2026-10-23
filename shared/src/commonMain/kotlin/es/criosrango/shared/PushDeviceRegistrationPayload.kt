package es.criosrango.shared

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PushDeviceRegistrationPayload(
    val platform: String,
    @SerialName("identifier_type") val identifierType: String,
    val identifier: String,
    @SerialName("new_products") val newProducts: Boolean,
    @SerialName("order_updates") val orderUpdates: Boolean
)

@Serializable
data class PushDeviceUnregisterPayload(
    val platform: String,
    val token: String
)
