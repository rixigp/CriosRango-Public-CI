package es.criosrango.shared.loyalty

import es.criosrango.shared.account.AccountTokenStore
import es.criosrango.shared.createStoreHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class LoyaltyClient(
    private val tokenStore: AccountTokenStore,
    private val baseUrl: String = "https://criosrango.es/wp-json/criosrango/v1/",
    private val client: HttpClient = createStoreHttpClient()
) {
    private val json = Json { ignoreUnknownKeys = true }

    private fun token(): String =
        tokenStore.load()?.takeIf { it.isNotBlank() }
            ?: throw LoyaltyApiException(401, "UNAUTHORIZED", "Inicia sesión para utilizar tu monedero.")

    private suspend fun execute(request: suspend () -> io.ktor.client.statement.HttpResponse): String {
        val response = request()
        val raw = response.bodyAsText()
        if (!response.status.isSuccess()) throw decodeError(response.status.value, raw)
        val success = runCatching {
            json.parseToJsonElement(raw).jsonObject["success"]?.jsonPrimitive?.booleanOrNull
        }.getOrNull()
        if (success == false) throw decodeError(response.status.value, raw)
        return raw
    }

    private fun decodeError(statusCode: Int?, raw: String): LoyaltyApiException {
        val error = runCatching { json.decodeFromString<LoyaltyErrorResponse>(raw) }.getOrNull()
        return LoyaltyApiException(
            statusCode = statusCode,
            code = error?.code ?: "SERVICE_UNAVAILABLE",
            message = error?.message?.takeIf { it.isNotBlank() } ?: "No se ha podido completar la operación."
        )
    }

    suspend fun getWallet(): LoyaltyWallet {
        val token = token()
        val raw = execute {
            client.get(baseUrl + "loyalty") {
                header(HttpHeaders.Authorization, "Bearer " + token)
            }
        }
        return json.decodeFromString(raw)
    }

    suspend fun redeem(request: RedeemWalletRequest): RedeemWalletResponse {
        require(request.points > 0) { "Los puntos a canjear deben ser mayores que cero." }
        require(request.requestId.isNotBlank()) { "El identificador de la operación no puede estar vacío." }
        val token = token()
        val raw = execute {
            client.post(baseUrl + "loyalty/redeem") {
                header(HttpHeaders.Authorization, "Bearer " + token)
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }
        return json.decodeFromString(raw)
    }

    fun close() = client.close()
}
