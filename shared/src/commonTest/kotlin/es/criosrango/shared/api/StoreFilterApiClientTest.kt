package es.criosrango.shared.api
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
class StoreFilterApiClientTest {
 @Test fun query_preserves_scope_canonical_ids_pagination_sort_and_facet() = runTest {
  var requestPath = ""; var query = emptyMap<String, String>()
  val http = HttpClient(MockEngine { request ->
   requestPath = request.url.encodedPath; query = request.url.parameters.names().associateWith { request.url.parameters[it] ?: "" }
   respond("""{"schema_version":1,"page":2,"per_page":12,"total":25,"has_more":true,"product_ids":[39291,46553],"facets":{}}""", HttpStatusCode.OK, headersOf("Content-Type","application/json"))
  }) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
  val client = StoreFilterApiClient(endpoint = "https://example.test/query", client = http)
  val response = client.query("outlet", listOf(342), listOf(353), listOf(777), 2, 12, "price_asc", "sizes")
  assertEquals("/query", requestPath); assertEquals("outlet", query["scope"]); assertEquals("342", query["size_ids"])
  assertEquals("353", query["color_ids"]); assertEquals("777", query["brand_ids"]); assertEquals("2", query["page"])
  assertEquals("12", query["per_page"]); assertEquals("price_asc", query["sort"]); assertEquals("sizes", query["facet"])
  assertEquals(listOf(39291,46553), response.productIds); assertEquals(25,response.total); assertEquals(true,response.hasMore)
  client.close()
 }
}
