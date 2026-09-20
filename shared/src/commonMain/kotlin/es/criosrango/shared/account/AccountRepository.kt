package es.criosrango.shared.account

import io.ktor.client.plugins.ResponseException
import io.ktor.http.HttpStatusCode

interface AccountTokenStore {
    fun load(): String?
    fun save(token: String): Boolean
    fun clear()
}

data class PendingClaimOrder(
    val orderId: Int,
    val orderKey: String
)

interface ClaimOrderStore {
    fun load(): PendingClaimOrder?
    fun save(order: PendingClaimOrder): Boolean
    fun clear()
}

class AccountRepository(
    private val tokenStore: AccountTokenStore,
    private val client: AccountClient = AccountClient(),
    private val claimOrderStore: ClaimOrderStore? = null
) {
    val hasSession: Boolean
        get() = !tokenStore.load().isNullOrBlank()

    suspend fun login(login: String, password: String): AccountUser {
        val response = client.login(AccountLoginRequest(login.trim(), password))
        persistToken(response.token)
        return response.user
    }

    suspend fun register(
        email: String,
        password: String,
        firstName: String,
        lastName: String,
        phone: String
    ): AccountUser {
        val response = client.register(
            AccountRegisterRequest(
                email = email.trim(),
                password = password,
                firstName = firstName.trim(),
                lastName = lastName.trim(),
                phone = phone.trim()
            )
        )
        persistToken(response.token)
        return response.user
    }

    suspend fun forgotPassword(login: String): String =
        client.forgotPassword(AccountForgotPasswordRequest(login.trim())).message

    suspend fun me(): AccountUser {
        val token = requireToken()
        return try {
            client.me(token).user
        } catch (exception: ResponseException) {
            if (exception.response.status == HttpStatusCode.Unauthorized) {
                tokenStore.clear()
            }
            throw exception
        }
    }

    suspend fun customerAddress(): AccountCustomerAddress =
        authenticated { token -> client.customerAddress(token) }

    suspend fun saveCustomerAddress(address: AccountCustomerAddress) {
        authenticated { token ->
            client.saveCustomerAddress(token, address)
            Unit
        }
    }

    suspend fun orders(perPage: Int = 20): AccountOrdersResponse =
        authenticated { token -> client.ordersDetailed(token, perPage) }

    fun prepareClaimOrder(orderId: Int, orderKey: String): Boolean {
        if (!hasSession && claimOrderStore == null) return false
        if (orderId <= 0 || orderKey.isBlank()) return false
        return claimOrderStore?.save(PendingClaimOrder(orderId, orderKey)) ?: false
    }

    fun pendingClaimOrder(): PendingClaimOrder? = claimOrderStore?.load()

    suspend fun claimOrder(orderId: Int, orderKey: String): AccountClaimOrderResponse {
        requireToken()
        require(orderId > 0 && orderKey.isNotBlank()) { "Datos de pedido no válidos." }
        return try {
            val response = client.claimOrder(
                tokenStore.load()!!,
                AccountClaimOrderRequest(orderId, orderKey)
            )
            claimOrderStore?.let { store ->
                if (store.load() == PendingClaimOrder(orderId, orderKey)) store.clear()
            }
            response
        } catch (exception: ResponseException) {
            if (exception.response.status == HttpStatusCode.Unauthorized) tokenStore.clear()
            throw exception
        }
    }

    suspend fun claimPendingOrder(): AccountClaimOrderResponse? {
        val pending = claimOrderStore?.load() ?: return null
        if (!hasSession) return null
        return claimOrder(pending.orderId, pending.orderKey)
    }

    suspend fun logout() {
        val token = tokenStore.load()?.takeIf { it.isNotBlank() }
        try {
            if (token != null) client.logout(token)
        } finally {
            tokenStore.clear()
        }
    }

    fun clearLocalSession() {
        tokenStore.clear()
    }

    private suspend fun <T> authenticated(block: suspend (String) -> T): T {
        val token = requireToken()
        return try {
            block(token)
        } catch (exception: ResponseException) {
            if (exception.response.status == HttpStatusCode.Unauthorized) {
                tokenStore.clear()
            }
            throw exception
        }
    }

    private fun requireToken(): String =
        tokenStore.load()?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("No hay ninguna sesión iniciada.")

    private fun persistToken(token: String) {
        if (token.isBlank() || !tokenStore.save(token)) {
            throw IllegalStateException("No se ha podido guardar la sesión de cuenta.")
        }
    }
}
