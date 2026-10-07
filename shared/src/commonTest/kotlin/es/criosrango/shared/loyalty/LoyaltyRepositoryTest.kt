package es.criosrango.shared.loyalty

import es.criosrango.shared.account.AccountTokenStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class TestTokenStore : AccountTokenStore {
    private var token: String? = "test-token"
    override fun load(): String? = token
    override fun save(token: String): Boolean { this.token = token; return true }
    override fun clear() { token = null }
}

class LoyaltyRepositoryTest {
    @Test
    fun walletUsesBackendConfiguration() = runTest {
        val engine = MockEngine {
            respond(
                """{"points":368,"wallet_value":"36.80","minimum_redeem_points":100,"minimum_redeem_value":"10.00","maximum_redeem_points":null,"conversion_points":100,"conversion_value":"10.00","currency":"EUR","pending_rewards":[]}""",
                HttpStatusCode.OK,
                headersOf("Content-Type", ContentType.Application.Json.toString())
            )
        }
        val client = LoyaltyClient(
            TestTokenStore(),
            baseUrl = "https://example.test/wp-json/criosrango/v1/",
            client = HttpClient(engine)
        )
        val wallet = LoyaltyRepository(TestTokenStore(), client).getWallet()
        assertEquals(368, wallet.points)
        assertEquals("36.80", wallet.walletValue)
        assertEquals(100, wallet.minimumRedeemPoints)
        assertEquals("10.00", wallet.conversionValue)
    }

    @Test
    fun redeemSendsTheProvidedRequestId() = runTest {
        val engine = MockEngine { incoming ->
            assertEquals(HttpMethod.Post, incoming.method)
            assertEquals("/wp-json/criosrango/v1/loyalty/redeem", incoming.url.encodedPath)
            assertEquals("Bearer test-token", incoming.headers[HttpHeaders.Authorization])
            val body = (incoming.body as TextContent).text
            assertEquals("""{"points":100,"request_id":"operation-123"}""", body)

            respond(
                """{"success":true,"points_redeemed":100,"value":"10.00","remaining_points":268,"remaining_wallet_value":"26.80","coupon":{"id":123,"user_reward_id":123,"coupon_id":456,"code":"CR-MONEDERO-TEST","amount":"10.00"},"idempotent_replay":false}""",
                HttpStatusCode.OK,
                headersOf("Content-Type", ContentType.Application.Json.toString())
            )
        }
        val repository = LoyaltyRepository(
            TestTokenStore(),
            LoyaltyClient(
                TestTokenStore(),
                "https://example.test/wp-json/criosrango/v1/",
                HttpClient(engine) {
                    install(ContentNegotiation) {
                        json(Json { ignoreUnknownKeys = true; coerceInputValues = true })
                    }
                }
            )
        )
        val response = repository.redeem(100, "operation-123")
        assertEquals("CR-MONEDERO-TEST", response.coupon?.code)
        assertTrue(!response.idempotentReplay)
    }

    @Test
    fun redeemOptionsAreDynamicAndRespectCartLimit() {
        val wallet = LoyaltyWallet(
            points = 368,
            walletValue = "36.80",
            minimumRedeemPoints = 100,
            minimumRedeemValue = "10.00",
            maximumRedeemPoints = null,
            conversionPoints = 100,
            conversionValue = "10.00",
            currency = "EUR"
        )
        val options = wallet.redeemableOptions("25.00")
        assertEquals(listOf(100, 200, 250), options.map { it.points })
        assertEquals(listOf("10.00", "20.00", "25.00"), options.map { it.value })
        assertTrue(options.last().isMaximum)
        assertEquals("25.00", subtractMoneyAmounts("35.00", "10.00"))
    }
}
