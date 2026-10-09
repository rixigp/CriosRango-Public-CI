package es.criosrango.shared

import es.criosrango.shared.api.InMemoryStoreSessionStore
import es.criosrango.shared.api.StoreApiClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StoreCartLogoutTest {
    @Test
    fun logoutRemovesRemoteCouponAndItemsThenClearsLocalSessionAndCart() = runBlocking {
        val requests = mutableListOf<Pair<String, String?>>()
        val withCoupon = """{"items":[{"key":"line-1","id":12,"name":"Jersey","quantity":1}],"coupons":[{"code":"cr-cumple","label":"Descuento aplicado"}]}"""
        val withItem = """{"items":[{"key":"line-1","id":12,"name":"Jersey","quantity":1}],"coupons":[]}"""
        val empty = """{"items":[],"coupons":[],"totals":{},"items_count":0}"""
        val client = HttpClient(MockEngine { request ->
            requests += request.url.encodedPath to request.headers["Cart-Token"]
            val body = when {
                request.url.encodedPath.endsWith("/cart/remove-coupon") -> withItem
                request.url.encodedPath.endsWith("/cart/remove-item") -> empty
                else -> withCoupon
            }
            respond(body, HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
        })
        val session = InMemoryStoreSessionStore(cartToken = "old-cart-token", nonce = "old-nonce", cookieHeader = "wp_woocommerce_session=old")
        val api = StoreApiClient(baseUrl = "https://example.test/wp-json/wc/store/v1/", client = client, session = session)
        val store = StoreCartStore(api, this)

        store.refreshAwait()
        assertEquals(1, store.cart.value.items.size)
        assertEquals(1, store.cart.value.coupons.size)

        store.clearForLogoutAwait()

        assertTrue(requests.any { it.first.endsWith("/cart/remove-coupon") })
        assertTrue(requests.any { it.first.endsWith("/cart/remove-item") })
        assertTrue(requests.filter { it.first.endsWith("/cart/remove-coupon") || it.first.endsWith("/cart/remove-item") }.all { it.second == "old-cart-token" })
        assertTrue(store.cart.value.items.isEmpty())
        assertTrue(store.cart.value.coupons.isEmpty())
        assertEquals("0", store.cart.value.totals.totalPrice)
        assertEquals(StoreCartLoadState.SUCCESS_EMPTY, store.state.value)
        assertNull(store.error.value)
        assertNull(store.couponError.value)
        assertNull(session.cartToken)
        assertNull(session.nonce)
        assertNull(session.cookieHeader)
        assertFalse(requests.isEmpty())
        client.close()
    }
}
