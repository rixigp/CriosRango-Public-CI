package es.criosrango.shared

import es.criosrango.shared.loyalty.LoyaltyRepository
import es.criosrango.shared.loyalty.LoyaltyReward
import es.criosrango.shared.loyalty.LoyaltyWallet
import es.criosrango.shared.loyalty.RedeemWalletResponse
import es.criosrango.shared.loyalty.redeemableOptions
import es.criosrango.shared.promotions.Promotion
import es.criosrango.shared.promotions.PromotionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class CouponApplicationStatus { SUCCESS, FAILURE }

data class CouponApplicationResult(
    val status: CouponApplicationStatus,
    val message: String? = null
) {
    val success: Boolean get() = status == CouponApplicationStatus.SUCCESS
    companion object {
        val Success = CouponApplicationResult(CouponApplicationStatus.SUCCESS)
        fun failure(message: String? = null) = CouponApplicationResult(CouponApplicationStatus.FAILURE, message)
    }
}

data class StoreWalletState(
    val wallet: LoyaltyWallet? = null,
    val promotions: List<Promotion> = emptyList(),
    val walletLoading: Boolean = false,
    val promotionsLoading: Boolean = false,
    val redeemLoading: Boolean = false,
    val couponApplyLoading: Boolean = false,
    val walletError: String? = null,
    val promotionsError: String? = null,
    val operationError: String? = null,
    val pendingRedemptionPoints: Int? = null,
    val pendingRequestId: String? = null,
    val pendingRewardCode: String? = null,
    val pendingReward: LoyaltyReward? = null,
    val appliedCouponCodes: Set<String> = emptySet()
) {
    val busy: Boolean get() = redeemLoading || couponApplyLoading
}

/** Shared owner for wallet, rewards, promotions and session-bound lifecycle. */
class StoreWalletOwner internal constructor(
    private val loadWallet: suspend () -> LoyaltyWallet,
    private val loadPromotions: suspend () -> List<Promotion>,
    private val redeemPoints: suspend (Int, String) -> RedeemWalletResponse,
    private val createRequestId: () -> String,
    private val scope: CoroutineScope
) {
    constructor(
        loyaltyRepository: LoyaltyRepository,
        promotionRepository: PromotionRepository,
        scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    ) : this(
        { loyaltyRepository.getWallet() },
        { promotionRepository.getPromotions() },
        { points, requestId -> loyaltyRepository.redeem(points, requestId) },
        { loyaltyRepository.newRequestId() },
        scope
    )

    private var sessionId: String? = null
    private var generation: Long = 0L
    private var redemptionInFlight = false
    private var applyInFlight = false
    private val mutableState = MutableStateFlow(StoreWalletState())
    val state: StateFlow<StoreWalletState> = mutableState.asStateFlow()

    fun onSessionChanged(userOrSessionId: String?) {
        if (sessionId == userOrSessionId) return
        sessionId = userOrSessionId
        generation += 1L
        redemptionInFlight = false
        applyInFlight = false
        mutableState.value = StoreWalletState(
            walletLoading = userOrSessionId != null,
            promotionsLoading = userOrSessionId != null
        )
        if (userOrSessionId != null) refresh()
    }

    fun clearForLogout() {
        sessionId = null
        generation += 1L
        redemptionInFlight = false
        applyInFlight = false
        mutableState.value = StoreWalletState()
    }

    fun setAppliedCouponCodes(codes: Set<String>) {
        mutableState.value = mutableState.value.copy(
            appliedCouponCodes = codes.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        )
    }

    fun refresh() {
        val currentGeneration = generation
        mutableState.value = mutableState.value.copy(
            walletLoading = true, promotionsLoading = true,
            walletError = null, promotionsError = null
        )
        scope.launch {
            try {
                val result = loadWallet()
                if (currentGeneration == generation) mutableState.value = mutableState.value.copy(wallet = result, walletError = null)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (currentGeneration == generation) mutableState.value = mutableState.value.copy(walletError = error.message ?: "No se ha podido cargar el monedero.")
            } finally {
                if (currentGeneration == generation) mutableState.value = mutableState.value.copy(walletLoading = false)
            }
        }
        scope.launch {
            try {
                val result = loadPromotions().sortedByDescending { it.priority }
                if (currentGeneration == generation) mutableState.value = mutableState.value.copy(promotions = result, promotionsError = null)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (currentGeneration == generation) mutableState.value = mutableState.value.copy(promotionsError = error.message ?: "No se han podido cargar las promociones.")
            } finally {
                if (currentGeneration == generation) mutableState.value = mutableState.value.copy(promotionsLoading = false)
            }
        }
    }

    suspend fun refreshAwait() {
        val currentGeneration = generation
        mutableState.value = mutableState.value.copy(walletLoading = true, promotionsLoading = true, walletError = null, promotionsError = null)
        try {
            val result = loadWallet()
            if (currentGeneration == generation) mutableState.value = mutableState.value.copy(wallet = result, walletError = null)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (currentGeneration == generation) mutableState.value = mutableState.value.copy(walletError = error.message ?: "No se ha podido cargar el monedero.")
        } finally {
            if (currentGeneration == generation) mutableState.value = mutableState.value.copy(walletLoading = false)
        }
        try {
            val result = loadPromotions().sortedByDescending { it.priority }
            if (currentGeneration == generation) mutableState.value = mutableState.value.copy(promotions = result, promotionsError = null)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (currentGeneration == generation) mutableState.value = mutableState.value.copy(promotionsError = error.message ?: "No se han podido cargar las promociones.")
        } finally {
            if (currentGeneration == generation) mutableState.value = mutableState.value.copy(promotionsLoading = false)
        }
    }

    fun redeem(points: Int, applyCoupon: suspend (String) -> CouponApplicationResult) {
        if (redemptionInFlight || mutableState.value.redeemLoading || points <= 0 || !mutableState.value.pendingRewardCode.isNullOrBlank()) return
        if (mutableState.value.wallet?.redeemableOptions()?.none { it.points == points } != false) return
        val previous = mutableState.value
        val requestId = if (previous.pendingRedemptionPoints == points && !previous.pendingRequestId.isNullOrBlank()) {
            previous.pendingRequestId!!
        } else {
            createRequestId().also { id ->
                mutableState.value = previous.copy(pendingRedemptionPoints = points, pendingRequestId = id)
            }
        }
        redemptionInFlight = true
        val currentGeneration = generation
        scope.launch {
            mutableState.value = mutableState.value.copy(redeemLoading = true, operationError = null)
            try {
                val response = redeemPoints(points, requestId)
                if (currentGeneration != generation) return@launch
                if (!response.success) {
                    mutableState.value = mutableState.value.copy(operationError = "No se ha podido completar el canje.")
                    return@launch
                }
                // Server redemption succeeded: clear the retry token before touching Cart.
                // Applying a created credit must never repeat redemption or create another credit.
                val createdReward = response.coupon?.let { coupon ->
                    LoyaltyReward(coupon.userRewardId, coupon.code, coupon.amount, points, "pending")
                }
                val currentWallet = mutableState.value.wallet
                mutableState.value = mutableState.value.copy(
                    wallet = currentWallet?.copy(
                        points = response.remainingPoints,
                        walletValue = response.remainingWalletValue,
                        pendingRewards = (currentWallet.pendingRewards + listOfNotNull(createdReward)).distinctBy { it.code.trim().lowercase() }
                    ),
                    pendingRedemptionPoints = null,
                    pendingRequestId = null,
                    pendingRewardCode = response.coupon?.code,
                    pendingReward = createdReward
                )
                refresh()
                val coupon = response.coupon
                if (coupon == null || coupon.code.isBlank()) {
                    mutableState.value = mutableState.value.copy(operationError = "El canje se ha creado, pero el servidor no ha devuelto un cupón utilizable.")
                    return@launch
                }
                applyExistingCoupon(coupon.code, applyCoupon, currentGeneration,
                    "El crédito se ha creado, pero no se ha podido aplicar al carrito. Puedes aplicarlo desde «Saldo listo para usar».",
                    createdCredit = true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (currentGeneration == generation) mutableState.value = mutableState.value.copy(operationError = error.message ?: "No se ha podido utilizar el monedero.")
                // Keep the same request ID after a network/server failure for an idempotent retry.
            } finally {
                if (currentGeneration == generation) {
                    redemptionInFlight = false
                    mutableState.value = mutableState.value.copy(redeemLoading = false)
                }
            }
        }
    }

    fun applyReward(reward: LoyaltyReward, applyCoupon: suspend (String) -> CouponApplicationResult) {
        applyExistingCoupon(reward.code, applyCoupon, generation, "No se ha podido aplicar el crédito. Puedes volver a intentarlo.", createdCredit = true)
    }

    fun applyPromotion(code: String, applyCoupon: suspend (String) -> CouponApplicationResult) {
        applyExistingCoupon(code, applyCoupon, generation, "No se ha podido aplicar la promoción. Comprueba el carrito e inténtalo de nuevo.")
    }

    private fun applyExistingCoupon(
        code: String,
        applyCoupon: suspend (String) -> CouponApplicationResult,
        currentGeneration: Long,
        failureMessage: String,
        createdCredit: Boolean = false
    ) {
        if (applyInFlight || mutableState.value.couponApplyLoading || code.isBlank()) return
        applyInFlight = true
        scope.launch {
            mutableState.value = mutableState.value.copy(
                couponApplyLoading = true,
                operationError = null,
                pendingRewardCode = if (createdCredit) code else mutableState.value.pendingRewardCode
            )
            try {
                val result = applyCoupon(code)
                if (currentGeneration != generation) return@launch
                if (result.success) {
                    val current = mutableState.value
                    mutableState.value = current.copy(
                        operationError = null,
                        pendingRewardCode = current.pendingRewardCode?.takeUnless { it.equals(code, ignoreCase = true) },
                        pendingReward = current.pendingReward?.takeUnless { it.code.equals(code, ignoreCase = true) }
                    )
                    refresh()
                } else {
                    mutableState.value = mutableState.value.copy(operationError = if (createdCredit) failureMessage else result.message ?: failureMessage)
                    refresh()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (currentGeneration == generation) {
                    mutableState.value = mutableState.value.copy(operationError = if (createdCredit) failureMessage else error.message ?: failureMessage)
                    refresh()
                }
            } finally {
                if (currentGeneration == generation) {
                    applyInFlight = false
                    mutableState.value = mutableState.value.copy(couponApplyLoading = false)
                }
            }
        }
    }
}
