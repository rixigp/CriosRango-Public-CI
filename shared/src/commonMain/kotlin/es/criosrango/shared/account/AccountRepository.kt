package es.criosrango.shared.account

import io.ktor.client.plugins.ResponseException
import io.ktor.http.HttpStatusCode

interface AccountTokenStore {
    fun load(): String?
    fun save(token: String): Boolean
    fun clear()
}

class AccountRepository(
    private val tokenStore: AccountTokenStore,
    private val client: AccountClient = AccountClient()
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

    private fun requireToken(): String =
        tokenStore.load()?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("No hay ninguna sesión iniciada.")

    private fun persistToken(token: String) {
        if (token.isBlank() || !tokenStore.save(token)) {
            throw IllegalStateException("No se ha podido guardar la sesión de cuenta.")
        }
    }
}
