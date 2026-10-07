package es.criosrango.shared.loyalty

import es.criosrango.shared.account.AccountTokenStore
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class LoyaltyRepository(
    tokenStore: AccountTokenStore,
    client: LoyaltyClient = LoyaltyClient(tokenStore)
) {
    private val client = client

    suspend fun getWallet(): LoyaltyWallet = client.getWallet()

    suspend fun redeem(points: Int, requestId: String): RedeemWalletResponse {
        require(points > 0) { "Los puntos a canjear deben ser mayores que cero." }
        require(requestId.isNotBlank()) { "El identificador de la operación no puede estar vacío." }
        return client.redeem(RedeemWalletRequest(points, requestId))
    }

    fun newRequestId(): String = Uuid.random().toString()
}
