package es.criosrango.shared.account

import es.criosrango.shared.createStoreHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.plugins.ResponseException
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType

class AccountClient(
    private val baseUrl: String = "https://criosrango.es/wp-json/criosrango/v1/",
    private val client: HttpClient = createStoreHttpClient()
) {
    suspend fun register(request: AccountRegisterRequest): AccountLoginResponse =
        client.post(baseUrl + "register") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.requireSuccessfulAccountResponse().body()

    suspend fun forgotPassword(request: AccountForgotPasswordRequest): AccountMessageResponse =
        client.post(baseUrl + "forgot-password") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.requireSuccessfulAccountResponse().body()

    suspend fun login(request: AccountLoginRequest): AccountLoginResponse =
        client.post(baseUrl + "login") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.requireSuccessfulAccountResponse().body()

    suspend fun me(token: String): AccountMeResponse =
        client.get(baseUrl + "me") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }.requireSuccessfulAccountResponse().body()

    suspend fun logout(token: String) {
        client.post(baseUrl + "logout") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }.requireSuccessfulAccountResponse()
    }

    suspend fun customerAddress(token: String): AccountCustomerAddress =
        client.get(baseUrl + "customer-address") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }.requireSuccessfulAccountResponse().body()

    suspend fun saveCustomerAddress(
        token: String,
        address: AccountCustomerAddress
    ) {
        client.post(baseUrl + "customer-address-save") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(address)
        }.requireSuccessfulAccountResponse()
    }

    suspend fun ordersDetailed(token: String, perPage: Int = 20): AccountOrdersResponse {
        val response = client.get(baseUrl + "orders-detailed") {
            header(HttpHeaders.Authorization, "Bearer $token")
            url.parameters.append("per_page", perPage.toString())
        }.requireSuccessfulAccountResponse()
        return response.body()
    }
    suspend fun claimOrder(
        token: String,
        request: AccountClaimOrderRequest
    ): AccountClaimOrderResponse =
        client.post(baseUrl + "claim-order") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(request)
        }.requireSuccessfulAccountResponse().body()

    fun close() = client.close()

    private suspend fun HttpResponse.requireSuccessfulAccountResponse(): HttpResponse {
        if (status.value !in 200..299) {
            throw ResponseException(this, bodyAsText())
        }
        return this
    }
}
