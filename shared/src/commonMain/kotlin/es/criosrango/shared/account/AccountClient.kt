package es.criosrango.shared.account

import es.criosrango.shared.createStoreHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.get
import io.ktor.client.request.setBody
import io.ktor.client.request.accept
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.client.plugins.ResponseException
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json

class AccountClient(
    private val baseUrl: String = "https://criosrango.es/wp-json/criosrango/v1/",
    private val client: HttpClient = createStoreHttpClient()
) {
    suspend fun register(request: AccountRegisterRequest): AccountLoginResponse =
        client.post(baseUrl + "register") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    suspend fun forgotPassword(request: AccountForgotPasswordRequest): AccountMessageResponse =
        client.post(baseUrl + "forgot-password") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    suspend fun login(request: AccountLoginRequest): AccountLoginResponse =
        client.post(baseUrl + "login") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    suspend fun me(token: String): AccountMeResponse =
        client.get(baseUrl + "me") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }.body()

    suspend fun logout(token: String) {
        client.post(baseUrl + "logout") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
    }

    fun close() = client.close()
}
