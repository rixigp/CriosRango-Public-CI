package es.criosrango.shared.loyalty

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.max
import kotlin.math.min

@Serializable
data class LoyaltyReward(
    val id: Int,
    val code: String,
    val amount: String,
    val points: Int,
    val status: String,
    @SerialName("expires_at") val expiresAt: String? = null
)

@Serializable
data class LoyaltyWallet(
    val points: Int = 0,
    @SerialName("wallet_value") val walletValue: String = "0.00",
    @SerialName("minimum_redeem_points") val minimumRedeemPoints: Int = 0,
    @SerialName("minimum_redeem_value") val minimumRedeemValue: String = "0.00",
    @SerialName("maximum_redeem_points") val maximumRedeemPoints: Int? = null,
    @SerialName("conversion_points") val conversionPoints: Int = 0,
    @SerialName("conversion_value") val conversionValue: String = "0.00",
    val currency: String = "EUR",
    @SerialName("pending_rewards") val pendingRewards: List<LoyaltyReward> = emptyList()
)

@Serializable
data class RedeemWalletRequest(
    val points: Int,
    @SerialName("request_id") val requestId: String
)

@Serializable
data class RedeemWalletCoupon(
    val id: Int,
    @SerialName("user_reward_id") val userRewardId: Int,
    @SerialName("coupon_id") val couponId: Int,
    val code: String,
    val amount: String
)

@Serializable
data class RedeemWalletResponse(
    val success: Boolean,
    @SerialName("points_redeemed") val pointsRedeemed: Int = 0,
    val value: String = "0.00",
    @SerialName("remaining_points") val remainingPoints: Int = 0,
    @SerialName("remaining_wallet_value") val remainingWalletValue: String = "0.00",
    val coupon: RedeemWalletCoupon? = null,
    @SerialName("idempotent_replay") val idempotentReplay: Boolean = false
)

@Serializable
internal data class LoyaltyErrorResponse(
    val success: Boolean = false,
    val code: String = "SERVICE_UNAVAILABLE",
    val message: String = "No se ha podido completar la operación."
)

class LoyaltyApiException(
    val statusCode: Int?,
    val code: String,
    override val message: String
) : Exception(message)

data class LoyaltyRedeemOption(
    val points: Int,
    val value: String,
    val isMaximum: Boolean = false
)

internal fun parseMoneyMinorUnits(value: String): Long {
    val normalized = value.trim().replace(',', '.')
    val negative = normalized.startsWith("-")
    val unsigned = normalized.removePrefix("+").removePrefix("-")
    val parts = unsigned.split('.', limit = 2)
    val whole = parts.firstOrNull()?.filter(Char::isDigit).orEmpty().ifBlank { "0" }
    val fraction = parts.getOrNull(1).orEmpty().filter(Char::isDigit).padEnd(2, '0').take(2)
    val minor = (whole.toLongOrNull() ?: 0L) * 100L + (fraction.toLongOrNull() ?: 0L)
    return if (negative) -minor else minor
}

internal fun formatMoneyMinorUnits(minor: Long): String {
    val sign = if (minor < 0) "-" else ""
    val absoluteValue = kotlin.math.abs(minor)
    return sign + (absoluteValue / 100L).toString() + "." +
        (absoluteValue % 100L).toString().padStart(2, '0')
}

fun addMoneyAmounts(first: String, second: String): String =
    formatMoneyMinorUnits(parseMoneyMinorUnits(first) + parseMoneyMinorUnits(second))

fun subtractMoneyAmounts(first: String, second: String): String =
    formatMoneyMinorUnits(max(0L, parseMoneyMinorUnits(first) - parseMoneyMinorUnits(second)))

fun LoyaltyWallet.redeemableOptions(
    consumerProductSubtotalAfterDiscounts: String? = null
): List<LoyaltyRedeemOption> {
    val conversionMinor = parseMoneyMinorUnits(conversionValue)
    if (points < minimumRedeemPoints || conversionPoints <= 0 || conversionMinor <= 0L) {
        return emptyList()
    }

    val cartLimitPoints = consumerProductSubtotalAfterDiscounts?.let {
        val eligibleMinor = max(0L, parseMoneyMinorUnits(it))
        ((eligibleMinor * conversionPoints) / conversionMinor).toInt()
    }

    val configuredMax = maximumRedeemPoints?.takeIf { it > 0 } ?: Int.MAX_VALUE
    val effectiveMax = min(points, min(configuredMax, cartLimitPoints ?: points))
    if (effectiveMax < minimumRedeemPoints) return emptyList()

    val increment = max(1, conversionPoints)
    val options = mutableListOf<LoyaltyRedeemOption>()
    var candidate = increment
    while (candidate < effectiveMax && options.size < 6) {
        if (candidate >= minimumRedeemPoints) {
            val minor = (candidate.toLong() * conversionMinor) / conversionPoints
            options += LoyaltyRedeemOption(candidate, formatMoneyMinorUnits(minor))
        }
        candidate += increment
    }

    val maximumMinor = (effectiveMax.toLong() * conversionMinor) / conversionPoints
    if (options.lastOrNull()?.points != effectiveMax) {
        options += LoyaltyRedeemOption(effectiveMax, formatMoneyMinorUnits(maximumMinor), isMaximum = true)
    } else {
        options[options.lastIndex] = options.last().copy(isMaximum = true)
    }
    return options.distinctBy { it.points }
}
