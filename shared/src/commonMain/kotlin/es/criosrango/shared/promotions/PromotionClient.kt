package es.criosrango.shared.promotions

import es.criosrango.shared.createStoreHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json

class PromotionClient(
    private val baseUrl: String = "https://criosrango.es/wp-json/criosrango/v1/",
    private val client: HttpClient = createStoreHttpClient()
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getPromotions(): List<Promotion> {
        val response = client.get(baseUrl + "promotions")
        val raw = response.bodyAsText()

        if (!response.status.isSuccess()) {
            val error = runCatching {
                json.decodeFromString<PromotionErrorResponse>(raw)
            }.getOrNull()

            throw PromotionApiException(
                statusCode = response.status.value,
                code = error?.code ?: "HTTP_" + response.status.value,
                message = error?.message?.takeIf { it.isNotBlank() }
                    ?: raw.ifBlank { response.status.description }
            )
        }

        return json.decodeFromString<PromotionsResponse>(raw).promotions
    }

    fun close() = client.close()
}
