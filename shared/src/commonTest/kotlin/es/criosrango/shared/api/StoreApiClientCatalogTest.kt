package es.criosrango.shared.api

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import es.criosrango.shared.model.countFor
import es.criosrango.shared.StoreOutletQuery
import es.criosrango.shared.storeOutletDataSource
import es.criosrango.shared.model.StoreProduct

class StoreApiClientCatalogTest {

    @Test
    fun outletDataSourceUsesFilterApiThenSingleOrderedHydrationBatch() = runTest {
        var storeRequestPath = ""
        var storeRequestParameters: io.ktor.http.Parameters? = null
        val storeClient = HttpClient(MockEngine { request ->
            storeRequestPath = request.url.encodedPath
            storeRequestParameters = request.url.parameters
            respond(
                """[{"id":11,"name":"Eleven"},{"id":10,"name":"Ten"}]""",
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        var filterParameters: io.ktor.http.Parameters? = null
        val filterHttp = HttpClient(MockEngine { request ->
            filterParameters = request.url.parameters
            respond(
                """{"schema_version":1,"page":1,"per_page":12,"total":2,"has_more":false,"product_ids":[10,11],"facets":{}}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val filterApi = StoreFilterApiClient(endpoint = "https://example.test/query", client = filterHttp)
        try {
            val source = storeOutletDataSource(StoreApiClient(client = storeClient), filterApi)
            val result = source.loadPage(
                StoreOutletQuery(
                    categoryId = 477,
                    originCategoryIds = listOf(287, 78, 78),
                    sizeIds = listOf(342),
                    colorIds = listOf(353),
                    brandIds = listOf(777),
                    sort = es.criosrango.shared.StoreOutletSort.PRICE_ASC
                ),
                page = 1,
                perPage = 12
            )

            assertEquals("outlet", filterParameters!!["scope"])
            assertEquals("477", filterParameters!!["outlet_category_id"])
            assertEquals("78,287", filterParameters!!["origin_category_ids"])
            assertEquals("342", filterParameters!!["size_ids"])
            assertEquals("353", filterParameters!!["color_ids"])
            assertEquals("777", filterParameters!!["brand_ids"])
            assertEquals("price_asc", filterParameters!!["sort"])
            assertEquals("/wp-json/wc/store/v1/products", storeRequestPath)
            assertEquals("10,11", storeRequestParameters!!["include"])
            assertEquals("include", storeRequestParameters!!["orderby"])
            assertEquals("2", storeRequestParameters!!["per_page"])
            assertEquals(listOf(10, 11), result.items.map { it.id })
            assertEquals(false, result.hasMore)
            assertEquals(2, result.totalItems)
        } finally {
            storeClient.close()
            filterApi.close()
        }
    }

    @Test
    fun outletDataSourceSkipsStoreHydrationWhenFilterApiReturnsNoIds() = runTest {
        var storeRequests = 0
        val storeClient = HttpClient(MockEngine {
            storeRequests++
            respond("[]", headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val filterHttp = HttpClient(MockEngine {
            respond(
                """{"schema_version":1,"page":1,"per_page":12,"total":0,"has_more":false,"product_ids":[],"facets":{}}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val filterApi = StoreFilterApiClient(endpoint = "https://example.test/query", client = filterHttp)
        try {
            val source = storeOutletDataSource(StoreApiClient(client = storeClient), filterApi)
            val result = source.loadPage(StoreOutletQuery(categoryId = 477), page = 1, perPage = 12)
            assertEquals(emptyList<StoreProduct>(), result.items)
            assertEquals(0, storeRequests)
            assertEquals(0, result.totalItems)
            assertEquals(false, result.hasMore)
        } finally {
            storeClient.close()
            filterApi.close()
        }
    }

    @Test
    fun outletDataSourceFailsClosedWhenStoreHydrationOmitsBackendId() = runTest {
        val storeClient = HttpClient(MockEngine {
            respond(
                """[{"id":10,"name":"Ten"}]""",
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val filterHttp = HttpClient(MockEngine {
            respond(
                """{"schema_version":1,"page":1,"per_page":12,"total":2,"has_more":false,"product_ids":[10,11],"facets":{}}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val filterApi = StoreFilterApiClient(endpoint = "https://example.test/query", client = filterHttp)
        try {
            val source = storeOutletDataSource(StoreApiClient(client = storeClient), filterApi)
            val error = runCatching {
                source.loadPage(StoreOutletQuery(categoryId = 477), page = 1, perPage = 12)
            }.exceptionOrNull()
            assertIs<StoreProductBatchConsistencyException>(error)
            assertTrue(error.message.orEmpty().contains("11"))
        } finally {
            storeClient.close()
            filterApi.close()
        }
    }

    @Test
    fun catalogQueriesUseSharedClientAndPreserveCatalogData() = runTest {
        val requests = mutableListOf<String>()
        val engine = MockEngine { request ->
            requests += request.url.toString()
            when {
                request.url.encodedPath.endsWith("/products/10") -> respond(
                    """
                    {
                      "id": 10,
                      "name": "Producto Outlet",
                      "type": "variable",
                      "prices": {"price":"1999","regular_price":"2499","sale_price":"1999","currency_symbol":"€","currency_minor_unit":2},
                      "extensions": {"criosrango_outlet":{"original_category_ids":[41,42]}},
                      "variations": [
                        {"id":11,"attributes":[{"name":"Talla","value":"M"}]}
                      ]
                    }
                    """.trimIndent(),
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )

                request.url.encodedPath.endsWith("/products/11") -> respond(
                    """
                    {
                      "id": 11,
                      "prices": {"price":"1999","regular_price":"2499","sale_price":"1999","currency_symbol":"€","currency_minor_unit":2},
                      "is_in_stock": true,
                      "is_purchasable": true,
                      "stock_status": "instock"
                    }
                    """.trimIndent(),
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )

                request.url.encodedPath.endsWith("/products/categories") -> respond(
                    """
                    [
                      {"id":445,"parent":0,"name":"Outlet","slug":"outlet","count":12}
                    ]
                    """.trimIndent(),
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )

                request.url.encodedPath == "/wp-json/criosrango/v1/outlet-availability" -> respond(
                    """
                    {
                      "schema_version": 1,
                      "version": 7,
                      "outlets": [
                        {
                          "outlet_category_id": 478,
                          "counts": [
                            {"category_id":80,"count":4},
                            {"category_id":313,"count":37}
                          ]
                        }
                      ]
                    }
                    """.trimIndent(),
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )

                else -> respond(
                    """
                    [
                      {
                        "id":1,
                        "name":"Vestido compartido",
                        "prices":{"price":"2999","regular_price":"3999","sale_price":"2999","currency_symbol":"€","currency_minor_unit":2},
                        "categories":[{"id":445,"parent":0,"name":"Outlet","slug":"outlet","count":12}],
                        "tags":[{"id":7,"name":"Marca","slug":"marca"}]
                      }
                    ]
                    """.trimIndent(),
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        }

        val client = HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    coerceInputValues = true
                })
            }
        }
        try {
            val api = StoreApiClient(client = client)

            val products = api.products(
                perPage = 24,
                page = 2,
                search = "vestido",
                category = 445,
                orderBy = "date",
                order = "desc",
                after = "2026-01-01T00:00:00Z",
                featured = true,
                tag = "marca"
            )

            assertEquals(1, products.size)
            assertEquals(1, products.single().id)
            assertEquals("Vestido compartido", products.single().name)
            assertEquals(listOf(445), products.single().categories.map { it.id })
            assertEquals("marca", products.single().tags.single().slug)

            val query = requests.first()
            assertTrue(query.contains("per_page=24"))
            assertTrue(query.contains("page=2"))
            assertTrue(query.contains("search=vestido"))
            assertTrue(query.contains("category=445"))
            assertTrue(query.contains("orderby=date"))
            assertTrue(query.contains("order=desc"))
            assertTrue(query.contains("after=2026-01-01T00%3A00%3A00Z"))
            assertTrue(query.contains("featured=true"))
            assertTrue(query.contains("tag=marca"))

            val categories = api.categories()
            assertEquals(445, categories.single().id)
            assertEquals("Outlet", categories.single().name)

            val availability = api.outletAvailability()
            assertEquals(1, availability.schemaVersion)
            assertEquals(7L, availability.version)
            assertEquals(478, availability.outlets.single().outletCategoryId)
            assertEquals(4, availability.countFor(478, 80))
            assertEquals(37, availability.countFor(478, 313))
            assertEquals(0, availability.countFor(478, 999))

            val product = api.productWithVariationAvailability(10)
            assertEquals(listOf(41, 42), product.originalCategoryIds)
            assertEquals(11, product.variations.single().id)
            assertEquals("1999", product.variations.single().prices.price)
            assertEquals(true, product.variations.single().isPurchasable)
        } finally {
            client.close()
        }
    }
}
