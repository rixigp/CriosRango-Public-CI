package es.criosrango.shared.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import es.criosrango.shared.model.CustomerAddress
import es.criosrango.shared.model.CreateOrderRequest
import es.criosrango.shared.model.SelectShippingRateRequest
import es.criosrango.shared.model.UpdateCustomerRequest

class StoreApiClientCheckoutTest {
    private val cartJson = """{"items":[],"coupons":[],"totals":{"total_price":"12300","total_shipping":"1000"},"payment_methods":["cecabank_gateway","cheque","redsys"],"shipping_rates":[],"items_count":0,"errors":[]}"""
    private val checkoutJson = """{"order_id":321,"order_key":"wc_order_key","status":"pending","payment_method":"cecabank_gateway","payment_methods":["cecabank_gateway","cheque","cod","bacs","redsys"],"payment_requirements":[],"redirect_url":"https://pay.example/321","totals":{"total_price":"12300"},"errors":[]}"""

    private fun client(requests: MutableList<io.ktor.client.request.HttpRequestData>): HttpClient =
        HttpClient(MockEngine { request ->
            requests += request
            val body = when {
                request.url.encodedPath.endsWith("/checkout") -> checkoutJson
                else -> cartJson
            }
            respond(body, HttpStatusCode.OK, headersOf(
                HttpHeaders.ContentType to listOf(ContentType.Application.Json.toString()),
                "Cart-Token" to listOf("cart-next"),
                "Nonce" to listOf("nonce-next"),
                HttpHeaders.SetCookie to listOf("woocommerce_cart_hash=hash-next; Path=/")
            ))
        }) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

    private fun address() = CustomerAddress(
        firstName="Test", lastName="User", email="test@example.com", phone="600000000",
        address1="Calle Test 1", postcode="28001", city="Madrid", state="M", country="ES"
    )

    @Test
    fun couponInvalidation409PreservesCodeRemovedCouponsAndUpdatedCart() = runTest {
        val body = """{"code":"woocommerce_rest_cart_coupon_errors","message":"Coupon removed","data":{"removed_coupons":{"bienvenida":{"code":"bienvenida","label":"Bienvenida"}},"cart":{"items":[],"coupons":[{"code":"blackcrios","label":"Blackcrios","totals":{"total_discount":"100"}}],"totals":{"total_price":"4613","total_discount":"100","total_shipping":"0"},"payment_methods":["cheque"],"shipping_rates":[{"package_id":0,"shipping_rates":[{"rate_id":"local_pickup:6","selected":true,"price":"0"}]}],"items_count":0,"errors":[]}}}"""
        val api = StoreApiClient(
            "https://example.test/wp-json/wc/store/v1/",
            HttpClient(MockEngine {
                respond(body, HttpStatusCode.Conflict, headersOf(HttpHeaders.ContentType to listOf(ContentType.Application.Json.toString())))
            }) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        )
        val exception = runCatching {
            api.createCheckout(CreateOrderRequest(
                paymentMethod = "cheque",
                billingAddress = address(),
                shippingAddress = address(),
                shippingRate = "local_pickup:6",
                expectedTotal = "4613"
            ))
        }.exceptionOrNull() as StoreApiException

        assertEquals(409, exception.statusCode)
        assertEquals("woocommerce_rest_cart_coupon_errors", exception.apiCode)
        assertEquals(listOf("bienvenida"), exception.removedCoupons.keys.toList())
        assertEquals("Bienvenida", (exception.removedCoupons["bienvenida"] as? kotlinx.serialization.json.JsonObject)?.get("label")?.let { it.toString().trim('"') })
        assertTrue(exception.updatedCart != null)
        assertEquals(listOf("blackcrios"), exception.updatedCart?.coupons?.map { it.code })
        assertEquals("4613", exception.updatedCart?.totals?.totalPrice)
        assertEquals("local_pickup:6", exception.updatedCart?.shippingRates?.firstOrNull()?.rates?.firstOrNull()?.rateId)
        api.close()
    }

    @Test
    fun couponErrorCodeSurvivesMalformedNestedCartPayload() = runTest {
        val body = """{"code":"woocommerce_rest_cart_coupon_errors","message":"Coupon removed","data":{"removed_coupons":{"blackcrios":{"code":"blackcrios","label":"Blackcrios"}},"cart":{"items":"unexpected"}}}"""
        val api = StoreApiClient(
            "https://example.test/wp-json/wc/store/v1/",
            HttpClient(MockEngine {
                respond(body, HttpStatusCode.Conflict, headersOf(HttpHeaders.ContentType to listOf(ContentType.Application.Json.toString())))
            }) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        )
        val exception = runCatching {
            api.createCheckout(CreateOrderRequest(
                paymentMethod = "cheque",
                billingAddress = address(),
                shippingAddress = address(),
                shippingRate = "local_pickup:6",
                expectedTotal = "4613"
            ))
        }.exceptionOrNull() as StoreApiException

        assertEquals("woocommerce_rest_cart_coupon_errors", exception.apiCode)
        assertEquals(listOf("blackcrios"), exception.removedCoupons.keys.toList())
        assertTrue(exception.updatedCart == null)
        api.close()
    }

    @Test
    fun genericCheckoutErrorKeepsExistingExceptionContract() = runTest {
        val body = """{"code":"woocommerce_rest_checkout_error","message":"Generic checkout failure"}"""
        val api = StoreApiClient(
            "https://example.test/wp-json/wc/store/v1/",
            HttpClient(MockEngine {
                respond(body, HttpStatusCode.Conflict, headersOf(HttpHeaders.ContentType to listOf(ContentType.Application.Json.toString())))
            }) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        )
        val exception = runCatching {
            api.createCheckout(CreateOrderRequest(
                paymentMethod = "cheque",
                billingAddress = address(),
                shippingAddress = address(),
                shippingRate = "local_pickup:6",
                expectedTotal = "4613"
            ))
        }.exceptionOrNull() as StoreApiException

        assertEquals(409, exception.statusCode)
        assertEquals("woocommerce_rest_checkout_error", exception.apiCode)
        assertTrue(exception.updatedCart == null)
        assertTrue(exception.removedCoupons.isEmpty())
        api.close()
    }

    @Test
    fun checkoutShippingAndOrderUseSameSharedSession() = runTest {
        val requests = mutableListOf<io.ktor.client.request.HttpRequestData>()
        val session = InMemoryStoreSessionStore("cart-initial", "nonce-initial", "woocommerce_cart_hash=old")
        val api = StoreApiClient("https://example.test/wp-json/wc/store/v1/", client(requests), session)

        api.updateCustomer(UpdateCustomerRequest(address(), address()))
        api.checkout()
        api.selectShippingRate(SelectShippingRateRequest(0, "flat_rate:1"))
        val order = api.createCheckout(CreateOrderRequest(
            paymentMethod="cecabank_gateway",
            billingAddress=address(),
            shippingAddress=address(),
            shippingRate="flat_rate:1",
            expectedTotal="12300"
        ))

        assertEquals(4, requests.size)
        assertEquals(listOf(
            "/wp-json/wc/store/v1/cart/update-customer",
            "/wp-json/wc/store/v1/checkout",
            "/wp-json/wc/store/v1/cart/select-shipping-rate",
            "/wp-json/wc/store/v1/checkout"
        ), requests.map { it.url.encodedPath })
        assertTrue(requests.drop(1).all { it.headers["Cart-Token"] == "cart-next" })
        assertTrue(requests.drop(1).all { it.headers["Nonce"] == "nonce-next" })
        assertTrue(requests.drop(1).all { it.headers["Cookie"]?.contains("woocommerce_cart_hash=hash-next") == true })
        assertEquals("1", requests.last().headers["X-CriosRango-App"])
        assertEquals(321, order.orderId)
        assertEquals("wc_order_key", order.orderKey)
        assertEquals("https://pay.example/321", order.redirectUrl)
        api.close()
    }
}
