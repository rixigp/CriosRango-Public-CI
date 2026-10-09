package es.criosrango.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import es.criosrango.shared.api.InMemoryStoreSessionStore
import es.criosrango.shared.api.StoreApiClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CouponInvalidationCheckoutPathIntegrationTest {
    private lateinit var context: Context
    private lateinit var database: CategoryProductCacheDatabase
    private lateinit var apiClient: StoreApiClient

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(
            context,
            CategoryProductCacheDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        if (::apiClient.isInitialized) apiClient.close()
        if (::database.isInitialized) database.close()
    }

    @Test
    fun couponInvalidation409RemainsTypedThroughTheRealAndroidCheckoutPath() {
        val removedCouponsJson = """{"bienvenida":{"code":"bienvenida","label":"Bienvenida","reason":"usage_limit_reached"}}"""
        val body = """{"code":"woocommerce_rest_cart_coupon_errors","message":"El cupón se ha eliminado del carrito.","data":{"removed_coupons":$removedCouponsJson,"cart":{"items":[],"coupons":[],"totals":{"total_price":"4613","total_discount":"0","total_shipping":"0"},"payment_methods":["cheque"],"shipping_rates":[],"items_count":0,"errors":[]}}}"""
        apiClient = StoreApiClient(
            "https://example.test/wp-json/wc/store/v1/",
            HttpClient(MockEngine {
                assertEquals("/wp-json/wc/store/v1/checkout", it.url.encodedPath)
                respond(
                    body,
                    HttpStatusCode.Conflict,
                    headersOf(HttpHeaders.ContentType to listOf(ContentType.Application.Json.toString()))
                )
            }) {
                expectSuccess = false
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            },
            InMemoryStoreSessionStore()
        )
        val preferences = context.getSharedPreferences("coupon-invalidation-integration-test", Context.MODE_PRIVATE)
        val session = StoreSession(preferences)
        val adapter = SharedCatalogStoreApiAdapter(apiClient, session)
        val categoryApi = CategoryCacheStoreApi(adapter, CategoryCatalogCache(database, startInitialSync = false))
        val repository = StoreRepository(categoryApi)
        val address = CustomerAddress(
            firstName = "Test",
            lastName = "User",
            email = "test@example.com",
            phone = "600000000",
            address1 = "Calle Test 1",
            postcode = "28001",
            city = "Madrid",
            state = "M",
            country = "ES"
        )

        val thrown = runCatching {
            runBlocking {
                repository.createCheckout(
                    CreateOrderRequest(
                        paymentMethod = "cheque",
                        billing_address = address,
                        shipping_address = address,
                        shippingRate = "local_pickup:6",
                        expectedTotal = "4613"
                    )
                )
            }
        }.exceptionOrNull()

        assertTrue("Expected CouponInvalidatedCheckoutException, got ${thrown?.javaClass?.name}: ${thrown?.message}", thrown is CouponInvalidatedCheckoutException)
        val exception = thrown as CouponInvalidatedCheckoutException
        assertEquals(409, exception.httpStatus)
        assertEquals("woocommerce_rest_cart_coupon_errors", exception.backendCode)
        assertEquals(listOf("Bienvenida"), exception.removedCouponNames)
        assertEquals(setOf("bienvenida"), exception.removedCouponCodes)
        assertEquals(1, exception.removedCoupons.size)
        assertEquals("Bienvenida", exception.removedCoupons["bienvenida"]?.let {
            (it as? kotlinx.serialization.json.JsonObject)?.get("label")?.toString()?.trim('"')
        })
        assertNotNull(exception.updatedCart)
        assertEquals("4613", exception.updatedCart?.totals?.totalPrice)
        assertTrue(exception.updatedCart?.coupons.isNullOrEmpty())
    }
}
