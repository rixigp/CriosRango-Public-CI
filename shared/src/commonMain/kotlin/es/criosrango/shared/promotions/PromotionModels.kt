package es.criosrango.shared.promotions

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Promotion(
    val id: String,
    val title: String,
    val description: String = "",
    val code: String? = null,
    val type: String? = null,
    val personal: Boolean = false,
    @SerialName("requires_login") val requiresLogin: Boolean = false,
    @SerialName("starts_at") val startsAt: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
    val priority: Int = 0
)

@Serializable
data class PromotionsResponse(
    val promotions: List<Promotion> = emptyList()
)

@Serializable
internal data class PromotionErrorResponse(
    val success: Boolean = false,
    val code: String = "SERVICE_UNAVAILABLE",
    val message: String = "No se han podido cargar las promociones."
)

class PromotionApiException(
    val statusCode: Int?,
    val code: String,
    override val message: String
) : Exception(message)
/** Promotions offered to guests must not depend on an account or personal eligibility. */
fun Promotion.isVisibleToAnonymous(): Boolean {
    if (personal || requiresLogin) return false
    val identity = "${id} ${code.orEmpty()} ${title}".lowercase().trim()
    if (identity.contains("bienvenida") || identity.contains("welcome")) return false
    if (identity.contains("cumple") || identity.contains("birthday")) return false
    if (identity.contains("cr-monedero-") || identity.contains("cr-monedero")) return false
    return true
}
