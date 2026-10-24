package es.criosrango.shared

import es.criosrango.shared.model.StoreCartLine
import es.criosrango.shared.api.InMemoryStoreSessionStore
import es.criosrango.shared.api.StoreSessionStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import es.criosrango.shared.api.StoreApiClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class StoreCartStoreTest {
    @Test
    fun sessionStoreKeepsCartCredentialsAcrossReads() {
        val session = InMemoryStoreSessionStore()
        session.cartToken = "cart-token"
        session.nonce = "nonce"
        session.cookieHeader = "wordpress_logged_in=x"
        assertEquals("cart-token", session.cartToken)
        assertEquals("nonce", session.nonce)
        assertEquals("wordpress_logged_in=x", session.cookieHeader)
    }

    @Test
    fun clearInvalidatesAllPersistedStoreApiCredentials() {
        val session = InMemoryStoreSessionStore(
            cartToken = "old-cart-token",
            nonce = "old-nonce",
            cookieHeader = "wp_woocommerce_session=old"
        )

        session.clear()

        assertNull(session.cartToken)
        assertNull(session.nonce)
        assertNull(session.cookieHeader)
    }

    @Test
    fun emptySessionStartsWithoutCartCredentials() {
        val session: StoreSessionStore = InMemoryStoreSessionStore()
        assertNull(session.cartToken)
        assertNull(session.nonce)
        assertNull(session.cookieHeader)
    }

    @Test
    fun initialUnknownCartLoadsAndEmptySuccessBecomesKnownEmpty() = runTest {
        val store = StoreCartStore(apiWithResponses(
            HttpStatusCode.OK to """{"items":[]}"""
        ), this)

        assertEquals(StoreCartLoadState.LOADING, store.state.value)
        store.refresh()
        store.state.first { it == StoreCartLoadState.SUCCESS_EMPTY }

        assertEquals(StoreCartLoadState.SUCCESS_EMPTY, store.state.value)
        assertTrue(store.cart.value.items.isEmpty())
        assertNull(store.error.value)
    }

    @Test
    fun initialPopulatedSuccessBecomesKnownPopulated() = runTest {
        val store = StoreCartStore(apiWithResponses(
            HttpStatusCode.OK to populatedCartJson
        ), this)

        store.refresh()
        store.state.first { it == StoreCartLoadState.SUCCESS_ITEMS }

        assertEquals(StoreCartLoadState.SUCCESS_ITEMS, store.state.value)
        assertEquals("line-1", store.cart.value.items.single().key)
    }

    @Test
    fun initialFetchErrorEndsInErrorAndRetryCanResolveEmpty() = runTest {
        val store = StoreCartStore(apiWithResponses(
            HttpStatusCode.ServiceUnavailable to """{"code":"offline","message":"offline"}""",
            HttpStatusCode.OK to """{"items":[]}"""
        ), this)

        store.refresh()
        store.state.first { it == StoreCartLoadState.ERROR }
        assertEquals(StoreCartLoadState.ERROR, store.state.value)
        assertNotNull(store.error.value)

        store.refresh()
        store.state.first { it == StoreCartLoadState.SUCCESS_EMPTY }
        assertEquals(StoreCartLoadState.SUCCESS_EMPTY, store.state.value)
        assertNull(store.error.value)
    }

    @Test
    fun knownEmptyReentryDoesNotShowLoadingAndPreservesEmptyOnRefreshError() = runTest {
        val refreshStarted = CompletableDeferred<Unit>()
        val finishRefresh = CompletableDeferred<Unit>()
        var requests = 0
        val api = StoreApiClient(client = HttpClient(MockEngine {
            if (requests++ == 0) {
                respond("""{"items":[]}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            } else {
                refreshStarted.complete(Unit)
                finishRefresh.await()
                respond("""{"code":"offline","message":"offline"}""", HttpStatusCode.ServiceUnavailable, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }))
        val store = StoreCartStore(api, this)
        store.refresh()
        store.state.first { it == StoreCartLoadState.SUCCESS_EMPTY }
        assertEquals(StoreCartLoadState.SUCCESS_EMPTY, store.state.value)

        store.refresh()
        refreshStarted.await()
        assertTrue(refreshStarted.isCompleted)
        assertEquals(StoreCartLoadState.SUCCESS_EMPTY, store.state.value)
        assertTrue(store.cart.value.items.isEmpty())

        finishRefresh.complete(Unit)
        store.error.first { it != null }
        assertEquals(StoreCartLoadState.SUCCESS_EMPTY, store.state.value)
        assertNotNull(store.error.value)
    }

    @Test
    fun knownPopulatedReentryKeepsSnapshotVisibleUntilRefreshCompletes() = runTest {
        val refreshStarted = CompletableDeferred<Unit>()
        val finishRefresh = CompletableDeferred<Unit>()
        var requests = 0
        val api = StoreApiClient(client = HttpClient(MockEngine {
            if (requests++ == 0) {
                respond(populatedCartJson, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            } else {
                refreshStarted.complete(Unit)
                finishRefresh.await()
                respond("""{"items":[]}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }))
        val store = StoreCartStore(api, this)
        store.refresh()
        store.state.first { it == StoreCartLoadState.SUCCESS_ITEMS }
        assertEquals(StoreCartLoadState.SUCCESS_ITEMS, store.state.value)

        store.refresh()
        refreshStarted.await()
        assertTrue(refreshStarted.isCompleted)
        assertEquals(StoreCartLoadState.SUCCESS_ITEMS, store.state.value)
        assertEquals("line-1", store.cart.value.items.single().key)

        finishRefresh.complete(Unit)
        store.state.first { it == StoreCartLoadState.SUCCESS_EMPTY }
        assertEquals(StoreCartLoadState.SUCCESS_EMPTY, store.state.value)
    }

    @Test
    fun removingLastLineProducesKnownEmpty() = runTest {
        val store = StoreCartStore(apiWithResponses(
            HttpStatusCode.OK to populatedCartJson,
            HttpStatusCode.OK to """{"items":[]}"""
        ), this)
        store.refresh()
        store.state.first { it == StoreCartLoadState.SUCCESS_ITEMS }
        assertEquals(StoreCartLoadState.SUCCESS_ITEMS, store.state.value)

        store.remove(StoreCartLine(key = "line-1", id = 1, name = "Camisa", quantity = 1))
        store.state.first { it == StoreCartLoadState.SUCCESS_EMPTY }

        assertEquals(StoreCartLoadState.SUCCESS_EMPTY, store.state.value)
        assertTrue(store.cart.value.items.isEmpty())
    }

    @Test
    fun appliesValidCouponOnlyWhenServerReturnsItAndUsesServerTotals() = runTest {
        val store = StoreCartStore(apiWithResponses(
            HttpStatusCode.OK to cartWithCoupon("SAVE10", "429", "38571")
        ), this)
        assertTrue(store.applyCouponAwait("save10"))
        assertEquals("SAVE10", store.cart.value.coupons.single().code)
        assertEquals("429", store.cart.value.totals.totalDiscount)
        assertEquals("38571", store.cart.value.totals.totalPrice)
        assertNull(store.couponError.value)
    }

    @Test
    fun validNewCouponReplacesPreviousOnlyWhenServerConfirmsNewCoupon() = runTest {
        val store = StoreCartStore(apiWithResponses(
            HttpStatusCode.OK to cartWithCoupon("WELCOME10", "429", "38571"),
            HttpStatusCode.OK to cartWithCoupon("BF20", "858", "38142")
        ), this)
        store.refreshAwait()
        assertTrue(store.applyCouponAwait("BF20"))
        assertEquals(listOf("BF20"), store.cart.value.coupons.map { it.code })
        assertEquals("858", store.cart.value.totals.totalDiscount)
        assertEquals("38142", store.cart.value.totals.totalPrice)
        assertNull(store.couponError.value)
    }

    @Test
    fun rejectedNewCouponKeepsLastConfirmedWelcomeCouponAndExposesFriendlyError() = runTest {
        val store = StoreCartStore(apiWithResponses(
            HttpStatusCode.OK to cartWithCoupon("WELCOME10", "429", "38571"),
            HttpStatusCode.BadRequest to """{"code":"woocommerce_rest_invalid_coupon","message":"Rejected"}"""
        ), this)
        store.refreshAwait()
        assertTrue(!store.applyCouponAwait("BF20"))
        assertEquals(listOf("WELCOME10"), store.cart.value.coupons.map { it.code })
        assertEquals("429", store.cart.value.totals.totalDiscount)
        assertEquals("38571", store.cart.value.totals.totalPrice)
        assertTrue(store.couponError.value.orEmpty().contains("no se puede aplicar"))
        assertTrue(store.couponError.value.orEmpty().contains("conservar o recuperar"))
    }

    @Test
    fun successfulHttpResponseWithWooErrorsDoesNotReplaceConfirmedCart() = runTest {
        val store = StoreCartStore(apiWithResponses(
            HttpStatusCode.OK to cartWithCoupon("WELCOME10", "429", "38571"),
            HttpStatusCode.OK to """{"items":[{"key":"line-1","id":1,"name":"Camisa","quantity":1}],"coupons":[{"code":"BF20","label":"BF20","totals":{"total_discount":"858","total_discount_tax":"0"}}],"totals":{"total_items":"39000","total_discount":"858","total_price":"38142"},"errors":[{"code":"woocommerce_rest_invalid_coupon","message":"Rejected"}]}"""
        ), this)
        store.refreshAwait()
        assertTrue(!store.applyCouponAwait("BF20"))
        assertEquals(listOf("WELCOME10"), store.cart.value.coupons.map { it.code })
        assertEquals("429", store.cart.value.totals.totalDiscount)
        assertNotNull(store.couponError.value)
    }

    @Test
    fun removeCouponResponseWithWooErrorsKeepsConfirmedCart() = runTest {
        val store = StoreCartStore(apiWithResponses(
            HttpStatusCode.OK to cartWithCoupon("WELCOME10", "429", "38571"),
            HttpStatusCode.OK to """{"items":[{"key":"line-1","id":1,"name":"Camisa","quantity":1}],"coupons":[],"totals":{"total_items":"39000","total_discount":"0","total_price":"39000"},"errors":[{"code":"woocommerce_rest_invalid_coupon","message":"Rejected"}]}"""
        ), this)
        store.refreshAwait()
        store.removeCoupon("WELCOME10")
        store.couponError.first { it != null }
        assertEquals(listOf("WELCOME10"), store.cart.value.coupons.map { it.code })
        assertEquals("429", store.cart.value.totals.totalDiscount)
        assertNotNull(store.couponError.value)
    }

    @Test
    fun successfulHttpResponseWithoutRequestedCouponIsNotReportedAsApplied() = runTest {
        val store = StoreCartStore(apiWithResponses(
            HttpStatusCode.OK to cartWithCoupon("WELCOME10", "429", "38571"),
            HttpStatusCode.OK to cartWithCoupon("WELCOME10", "429", "38571")
        ), this)
        store.refreshAwait()
        assertTrue(!store.applyCouponAwait("BF20"))
        assertEquals(listOf("WELCOME10"), store.cart.value.coupons.map { it.code })
        assertNotNull(store.couponError.value)
    }

    @Test
    fun concurrentCouponResponsesAreSerializedAndCannotOverwriteNewerCart() = runTest {
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        var applyCount = 0
        val api = StoreApiClient(client = HttpClient(MockEngine { request ->
            if (request.url.encodedPath.endsWith("/cart/apply-coupon")) {
                if (applyCount++ == 0) {
                    firstStarted.complete(Unit)
                    releaseFirst.await()
                    respond(cartWithCoupon("FIRST", "100", "39900"), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                } else {
                    respond(cartWithCoupon("SECOND", "200", "39800"), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                }
            } else {
                respond(emptyCartJson, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }))
        val store = StoreCartStore(api, this)
        val first = async { store.applyCouponAwait("FIRST") }
        firstStarted.await()
        assertTrue(firstStarted.isCompleted)
        val second = async { store.applyCouponAwait("SECOND") }
        runCurrent()
        releaseFirst.complete(Unit)
        assertTrue(first.await())
        assertTrue(second.await())
        assertEquals(listOf("SECOND"), store.cart.value.coupons.map { it.code })
        assertEquals("39800", store.cart.value.totals.totalPrice)
    }

    private val emptyCartJson: String
        get() = """{"items":[],"coupons":[],"totals":{"total_items":"39000","total_items_tax":"0","total_discount":"0","total_discount_tax":"0","total_price":"39000","total_price_tax":"0","currency_symbol":"€","currency_minor_unit":2}}"""

    private fun cartWithCoupon(code: String, discount: String, total: String): String =
        """{"items":[{"key":"line-1","id":1,"name":"Camisa","quantity":1}],"coupons":[{"code":"$code","label":"$code","totals":{"total_discount":"$discount","total_discount_tax":"0"}}],"totals":{"total_items":"39000","total_items_tax":"0","total_discount":"$discount","total_discount_tax":"0","total_price":"$total","total_price_tax":"0","currency_symbol":"€","currency_minor_unit":2}}"""
    private fun apiWithResponses(vararg responses: Pair<HttpStatusCode, String>): StoreApiClient {
        var index = 0
        val client = HttpClient(MockEngine {
            val response = responses[index.coerceAtMost(responses.lastIndex)]
            if (index < responses.lastIndex) index++
            respond(
                content = response.second,
                status = response.first,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        })
        return StoreApiClient(client = client)
    }

    private companion object {
        const val populatedCartJson = """{"items":[{"key":"line-1","id":1,"name":"Camisa","quantity":1}]}"""
    }

}
