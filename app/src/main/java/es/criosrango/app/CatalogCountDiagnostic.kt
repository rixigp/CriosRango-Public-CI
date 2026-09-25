package es.criosrango.app

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

internal class CatalogCountDiagnosticRunner(
    private val session: StoreSession
) {
    private val gson = Gson()

    suspend fun run(): String = withContext(Dispatchers.IO) {
        val client = OkHttpClient.Builder()
            .cookieJar(CookieJar.NO_COOKIES)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder().apply {
                    session.cartToken?.takeIf { it.isNotBlank() }?.let { header("Cart-Token", it) }
                    session.nonce?.takeIf { it.isNotBlank() }?.let { header("Nonce", it) }
                    session.cookieHeader?.takeIf { it.isNotBlank() }?.let { header("Cookie", it) }
                }.build()
                chain.proceed(request)
            }
            .build()

        try {
            val categories = loadAllCategories(client)
                .filter { it.count > 0 || it.id == 445 || it.parent == 445 }
                .toMutableList()

            listOf(
                ProductCategory(446, 445, "Hombre invierno", "hombre-invierno-outlet", 0),
                ProductCategory(475, 445, "Hombre verano", "hombre-verano-outlet", 0)
            ).forEach { synthetic ->
                if (categories.none { it.id == synthetic.id }) categories += synthetic
            }

            val products = loadAllProducts(client).distinctBy { it.id }
            val byId = categories.associateBy { it.id }

            fun belongsToCategory(product: StoreProduct, categoryId: Int): Boolean {
                return product.categories.any { assigned ->
                    var currentId = assigned.id
                    val visited = mutableSetOf<Int>()
                    while (currentId != 0 && visited.add(currentId)) {
                        if (currentId == categoryId) return true
                        currentId = byId[currentId]?.parent ?: 0
                    }
                    false
                }
            }

            val rows = categories
                .distinctBy { it.id }
                .sortedWith(compareBy<ProductCategory>({ hierarchyDepth(it.id, byId) }, { it.parent }, { it.name.lowercase() }))
                .map { category ->
                    CatalogCountRow(
                        categoryId = category.id,
                        categoryName = category.name,
                        parentId = category.parent,
                        parentName = byId[category.parent]?.name,
                        productCount = products.count { belongsToCategory(it, category.id) }
                    )
                }

            buildString {
                appendLine("CATALOG CATEGORY COUNT DIAGNOSTIC")
                appendLine("READ_ONLY=true")
                appendLine("PRODUCT_ENDPOINT=\${STORE_API_BASE_URL}products")
                appendLine("CATEGORY_ENDPOINT=\${STORE_API_BASE_URL}products/categories")
                appendLine("PRODUCT_PAGE_SIZE=100")
                appendLine("CATEGORY_PAGE_SIZE=100")
                appendLine("COUNT_RULE=Store API products received by the app; all pages; distinct product ID; category hierarchy includes descendants.")
                appendLine("VISIBILITY_RULE=No client-side visibility defaults added; Store API response is authoritative for published/catalog-visible products.")
                appendLine("OUTLET_ORIGIN_RULE=original_category_ids are not counted as normal category membership; they are used only by the existing Outlet-origin UI.")
                appendLine()
                appendLine("PRODUCT_PAGES_COMPLETE=true")
                appendLine("TOTAL_DISTINCT_PRODUCTS=\${products.size}")
                appendLine("TOTAL_CATEGORIES=\${rows.size}")
                appendLine()
                appendLine("[")
                rows.forEachIndexed { index, row ->
                    appendLine(
                        "  {\"category_id\":\${row.categoryId},\"category_name\":\${json(row.categoryName)},\"parent_id\":\${row.parentId},\"parent_name\":\${row.parentName?.let(::json) ?: "null"},\"product_count\":\${row.productCount}}" +
                            if (index == rows.lastIndex) "" else ","
                    )
                }
                appendLine("]")
                appendLine()
                appendLine("{\"total_categories\":\${rows.size},\"total_distinct_products\":\${products.size}}")
            }.trim()
        } finally {
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }
    }

    private suspend fun loadAllProducts(client: OkHttpClient): List<StoreProduct> {
        val result = mutableListOf<StoreProduct>()
        var page = 1
        while (true) {
            val response = request(client, "products?per_page=100&page=\$page")
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("Products page \$page HTTP \${response.code}: \$body")
            }
            val batch = gson.fromJson<List<StoreProduct>>(
                body,
                object : TypeToken<List<StoreProduct>>() {}.type
            ).orEmpty()
            if (batch.isEmpty()) break
            result += batch
            val totalPages = response.header("X-WP-TotalPages")?.toIntOrNull()
            if (totalPages != null && page >= totalPages) break
            if (totalPages == null && batch.size < 100) break
            page++
        }
        return result
    }

    private suspend fun loadAllCategories(client: OkHttpClient): List<ProductCategory> {
        val result = mutableListOf<ProductCategory>()
        var page = 1
        while (true) {
            val response = request(client, "products/categories?per_page=100&page=\$page")
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("Categories page \$page HTTP \${response.code}: \$body")
            }
            val batch = gson.fromJson<List<ProductCategory>>(
                body,
                object : TypeToken<List<ProductCategory>>() {}.type
            ).orEmpty()
            if (batch.isEmpty()) break
            result += batch
            val totalPages = response.header("X-WP-TotalPages")?.toIntOrNull()
            if (totalPages != null && page >= totalPages) break
            if (totalPages == null && batch.size < 100) break
            page++
        }
        return result
    }

    private fun request(client: OkHttpClient, relativePath: String) =
        client.newCall(
            Request.Builder()
                .url(STORE_API_BASE_URL + relativePath)
                .get()
                .build()
        ).execute()

    private fun hierarchyDepth(categoryId: Int, categories: Map<Int, ProductCategory>): Int {
        var depth = 0
        var current = categories[categoryId]?.parent ?: 0
        val visited = mutableSetOf<Int>()
        while (current != 0 && visited.add(current)) {
            depth++
            current = categories[current]?.parent ?: 0
        }
        return depth
    }

    private fun json(value: String): String = gson.toJson(value)

    private data class CatalogCountRow(
        val categoryId: Int,
        val categoryName: String,
        val parentId: Int,
        val parentName: String?,
        val productCount: Int
    )
}
