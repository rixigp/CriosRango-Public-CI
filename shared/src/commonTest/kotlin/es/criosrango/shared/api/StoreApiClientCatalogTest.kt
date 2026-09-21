package es.criosrango.shared.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StoreApiClientCatalogTest {
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

        val client = HttpClient(engine)
        try {
            val api = StoreApiClient(client = client)

            val products = 
                api.products(
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
            }

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
