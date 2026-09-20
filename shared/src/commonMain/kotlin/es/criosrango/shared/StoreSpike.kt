package es.criosrango.shared
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import es.criosrango.shared.api.StoreApiClient
import es.criosrango.shared.api.StoreSessionStore
import es.criosrango.shared.api.InMemoryStoreSessionStore
import es.criosrango.shared.model.StoreProduct
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

sealed interface SpikeState {
    data object Loading : SpikeState
    data class Content(val products: List<StoreProduct>, val categoryCount: Int) : SpikeState
    data class Empty(val message: String) : SpikeState
    data class Error(val message: String) : SpikeState
}

private fun assertion(name: String, condition: Boolean) {
    println("KMP_MODEL_ASSERTION_" + name + "=" + if (condition) "PASS" else "FAIL")
    check(condition) { "Model assertion failed: " + name }
}

private suspend fun fetchStore(session: StoreSessionStore = InMemoryStoreSessionStore()): Pair<List<StoreProduct>, Int> {
    val api = StoreApiClient(session = session)
    return try {
        println("KMP_RUNTIME_PRODUCTS_REQUEST endpoint=/products")
        val (productsRawJson, products) = api.productsWithRawJson(perPage = 12)
        println("KMP_RUNTIME_HTTP_STATUS_PRODUCTS status=200")
        println("KMP_RUNTIME_PRODUCTS_COUNT count=" + products.size)
        assertion("PRODUCTS_NON_EMPTY", products.isNotEmpty())
        val productJson = Json.parseToJsonElement(productsRawJson).jsonArray
        val productById = productJson.associateBy { it.jsonObject["id"]?.jsonPrimitive?.int ?: 0 }
        val product = products.firstOrNull { p -> p.id > 0 && p.name.isNotBlank() && p.permalink.isNotBlank() && p.images.any { it.src.isNotBlank() } && p.categories.isNotEmpty() && (p.shortDescription.isNotBlank() || p.description.isNotBlank()) && productById[p.id]?.jsonObject?.containsKey("prices") == true }
        assertion("PRODUCT_SELECTED", product != null)
        val selected = checkNotNull(product)
        val selectedJson = checkNotNull(productById[selected.id]?.jsonObject)
        assertion("PRODUCT_ID", selected.id > 0)
        assertion("PRODUCT_NAME", selected.name.isNotBlank())
        assertion("PRODUCT_PERMALINK", selected.permalink.isNotBlank())
        assertion("PRODUCT_PRICES", selectedJson.containsKey("prices"))
        assertion("PRODUCT_IMAGE_SRC", selected.images.any { it.src.isNotBlank() })
        assertion("PRODUCT_CATEGORIES", selected.categories.isNotEmpty())
        assertion("PRODUCT_DESCRIPTION", selected.shortDescription.isNotBlank() || selected.description.isNotBlank())
        val rawInStock = checkNotNull(selectedJson["is_in_stock"]?.jsonPrimitive?.boolean)
        val rawPurchasable = checkNotNull(selectedJson["is_purchasable"]?.jsonPrimitive?.boolean)
        assertion("PRODUCT_IS_IN_STOCK_MATCH", selected.isInStock == rawInStock)
        assertion("PRODUCT_PURCHASABLE_MATCH", selected.isPurchasable == rawPurchasable)
        println("KMP_MODEL_PRODUCT_ID=" + selected.id)
        println("KMP_MODEL_PRODUCT_NAME=" + selected.name)
        println("KMP_MODEL_PRODUCT_PERMALINK_PRESENT=true")
        println("KMP_MODEL_PRODUCT_PRICE_PRESENT=true")
        println("KMP_MODEL_PRODUCT_IMAGES=" + selected.images.size)
        println("KMP_MODEL_PRODUCT_IMAGE_SRC_PRESENT=true")
        println("KMP_MODEL_PRODUCT_CATEGORIES=" + selected.categories.size)
        println("KMP_MODEL_PRODUCT_IN_STOCK=" + selected.isInStock)
        println("KMP_MODEL_PRODUCT_PURCHASABLE=" + selected.isPurchasable)
        println("KMP_MODEL_PRODUCT_DESCRIPTION_PRESENT=true")
        val imageCount = products.sumOf { it.images.size }
        println("KMP_RUNTIME_PRODUCT_IMAGES_DESERIALIZED count=" + imageCount)

        println("KMP_RUNTIME_REQUEST_PRODUCT_DETAIL endpoint=/products/{id} id=" + selected.id)
        val selectedDetail = api.product(selected.id)
        println("KMP_RUNTIME_HTTP_STATUS_PRODUCT_DETAIL status=200")
        assertion("PRODUCT_DETAIL_ID_MATCH", selectedDetail.id == selected.id)
        assertion("PRODUCT_DETAIL_PRICE_PARSED", selectedDetail.prices.price.isNotBlank())
        assertion("PRODUCT_DETAIL_IMAGE_PARSED", selectedDetail.images.isEmpty() || selectedDetail.images.first().src.isNotBlank())
        println("KMP_MODEL_PRODUCT_DETAIL_PRICE=" + selectedDetail.prices.price)
        println("KMP_MODEL_PRODUCT_DETAIL_IMAGES=" + selectedDetail.images.size)
        println("KMP_MODEL_PRODUCT_DETAIL_STOCK=" + selectedDetail.isInStock)

        val variableProduct = products
            .asSequence()
            .plus(api.products(perPage = 100).asSequence())
            .distinctBy { it.id }
            .firstOrNull { it.variations.isNotEmpty() }
        assertion("VARIABLE_PRODUCT_SELECTED", variableProduct != null)
        val variable = checkNotNull(variableProduct)
        val variation = variable.variations.first()
        println("KMP_RUNTIME_PRODUCT_VARIATION_PARENT_ID=" + variable.id)
        println("KMP_RUNTIME_PRODUCT_VARIATION_ID=" + variation.id)

        println("KMP_RUNTIME_REQUEST_VARIATION_DETAIL endpoint=/products/{id} id=" + variation.id)
        val variationDetail = api.product(variation.id)
        println("KMP_RUNTIME_HTTP_STATUS_VARIATION_DETAIL status=200")
        assertion("VARIATION_DETAIL_ID_MATCH", variationDetail.id == variation.id)
        assertion("VARIATION_DETAIL_PRICE_PARSED", variationDetail.prices.price.isNotBlank())
        assertion("VARIATION_DETAIL_IMAGE_PARSED", variationDetail.images.isEmpty() || variationDetail.images.first().src.isNotBlank())
        println("KMP_MODEL_VARIATION_DETAIL_PRICE=" + variationDetail.prices.price)
        println("KMP_MODEL_VARIATION_DETAIL_IMAGES=" + variationDetail.images.size)
        println("KMP_MODEL_VARIATION_DETAIL_STOCK=" + variationDetail.isInStock)

        println("KMP_RUNTIME_REQUEST_PRODUCT_VARIATION_COMPLETION endpoint=/products/{id} id=" + variable.id)
        val completed = api.productWithVariationAvailability(variable.id)
        println("KMP_RUNTIME_HTTP_STATUS_PRODUCT_VARIATION_COMPLETION status=200")
        val completedVariation = completed.variations.firstOrNull { it.id == variation.id }
        assertion("VARIATION_COMPLETION_PRESENT", completedVariation != null)
        val completedSelected = checkNotNull(completedVariation)
        assertion("VARIATION_PRICES_MATCH", completedSelected.prices == variationDetail.prices)
        assertion("VARIATION_IMAGES_MATCH", completedSelected.images == variationDetail.images)
        assertion("VARIATION_IS_IN_STOCK_MATCH", completedSelected.isInStock == variationDetail.isInStock)
        assertion("VARIATION_PURCHASABLE_MATCH", completedSelected.isPurchasable == variationDetail.isPurchasable)
        assertion("VARIATION_BACKORDER_MATCH", completedSelected.isOnBackorder == variationDetail.isOnBackorder)
        assertion("VARIATION_LOW_STOCK_MATCH", completedSelected.lowStockRemaining == variationDetail.lowStockRemaining)
        assertion("VARIATION_STOCK_STATUS_MATCH", completedSelected.stockStatus == variationDetail.stockStatus)
        assertion("VARIATION_STOCK_QUANTITY_MATCH", completedSelected.stockQuantity == variationDetail.stockQuantity)
        assertion("VARIATION_MANAGE_STOCK_MATCH", completedSelected.manageStock == variationDetail.manageStock)
        assertion("VARIATION_QUANTITY_LIMITS_MATCH", completedSelected.quantityLimits == variationDetail.quantityLimits)
        assertion("VARIATION_ADD_TO_CART_MATCH", completedSelected.addToCart == variationDetail.addToCart)
        println("KMP_MODEL_VARIATION_COMPLETION_ID=" + completedSelected.id)
        println("KMP_MODEL_VARIATION_COMPLETION_PRICE_PRESENT=true")
        println("KMP_MODEL_VARIATION_COMPLETION_IMAGES=" + completedSelected.images.size)
        println("KMP_MODEL_VARIATION_COMPLETION_STOCK=" + completedSelected.isInStock)

        println("KMP_RUNTIME_REQUEST_OUTLET_ORIGIN endpoint=/products?category=Mujer%20invierno")
        val smokeCategories = api.categories(100)
        val mujerInvierno = smokeCategories.firstOrNull { it.name == "Mujer invierno" }
        assertion("OUTLET_MUJER_INVIERNO_CATEGORY_PRESENT", mujerInvierno != null)
        val jerseis = smokeCategories.firstOrNull { it.id == 461 }
        assertion("OUTLET_ORIGIN_CATEGORY_461_PRESENT", jerseis?.name == "Jerséis")
        assertion("OUTLET_ORIGIN_CATEGORY_461_PARENT", jerseis?.parent == 71)
        val outletListing = api.products(perPage = 100, category = checkNotNull(mujerInvierno).id)
        println("KMP_RUNTIME_HTTP_STATUS_OUTLET_LISTING status=200")
        val outletProduct50842 = outletListing.firstOrNull { it.id == 50842 }
        assertion("OUTLET_PRODUCT_50842_PRESENT", outletProduct50842 != null)
        val outletProduct = checkNotNull(outletProduct50842)
        assertion("OUTLET_PRODUCT_50842_ORIGIN_IDS", outletProduct.originalCategoryIds == listOf(461))
        println("KMP_MODEL_OUTLET_50842_CATEGORIES=" + outletProduct.categories.map { it.id })
        println("KMP_MODEL_OUTLET_50842_ORIGINAL_CATEGORY_IDS=" + outletProduct.originalCategoryIds)

        println("KMP_RUNTIME_REQUEST_CART endpoint=/cart")
        val firstCart = api.cart()
        println("KMP_RUNTIME_HTTP_STATUS_CART status=200")
        assertion("CART_MODEL_PARSED", firstCart.itemsCount >= 0 && firstCart.totals.totalPrice.isNotBlank())
        println("KMP_MODEL_CART_ITEMS=" + firstCart.items.size)
        println("KMP_MODEL_CART_ITEMS_COUNT=" + firstCart.itemsCount)
        println("KMP_MODEL_CART_TOTAL_PRICE=" + firstCart.totals.totalPrice)
        println("KMP_SESSION_CART_TOKEN_PRESENT=" + !session.cartToken.isNullOrBlank())
        println("KMP_SESSION_NONCE_PRESENT=" + !session.nonce.isNullOrBlank())
        println("KMP_SESSION_COOKIE_PRESENT=" + !session.cookieHeader.isNullOrBlank())
        val tokenAfterFirstCart = session.cartToken
        val nonceAfterFirstCart = session.nonce
        val cookieAfterFirstCart = session.cookieHeader

        println("KMP_RUNTIME_REQUEST_CART_SECOND endpoint=/cart")
        val secondCart = api.cart()
        println("KMP_RUNTIME_HTTP_STATUS_CART_SECOND status=200")
        assertion("CART_SECOND_MODEL_PARSED", secondCart.itemsCount >= 0)
        assertion("CART_TOKEN_RETAINED", session.cartToken?.isNotBlank() == true)
        assertion("NONCE_RETAINED", nonceAfterFirstCart == null || session.nonce?.isNotBlank() == true)
        assertion("COOKIE_RETAINED", cookieAfterFirstCart == null || session.cookieHeader?.isNotBlank() == true)
        println("KMP_SESSION_CART_TOKEN_RETAINED=" + (session.cartToken?.isNotBlank() == true))
        println("KMP_SESSION_NONCE_RETAINED=" + (nonceAfterFirstCart == null || session.nonce == nonceAfterFirstCart))
        println("KMP_SESSION_COOKIE_RETAINED=" + (cookieAfterFirstCart == null || session.cookieHeader == cookieAfterFirstCart))
        val (categoriesRawJson, categories) = api.categoriesWithRawJson(perPage = 100)
        println("KMP_RUNTIME_HTTP_STATUS_CATEGORIES status=200")
        println("KMP_RUNTIME_CATEGORIES_COUNT count=" + categories.size)
        assertion("CATEGORIES_NON_EMPTY", categories.isNotEmpty())
        val categoryJson = Json.parseToJsonElement(categoriesRawJson).jsonArray
        val categoryById = categoryJson.associateBy { it.jsonObject["id"]?.jsonPrimitive?.int ?: 0 }
        val category = categories.firstOrNull { c -> c.id > 0 && c.name.isNotBlank() && c.slug.isNotBlank() && categoryById[c.id]?.jsonObject?.containsKey("parent") == true && categoryById[c.id]?.jsonObject?.containsKey("count") == true }
        assertion("CATEGORY_SELECTED", category != null)
        val selectedCategory = checkNotNull(category)
        val selectedCategoryJson = checkNotNull(categoryById[selectedCategory.id]?.jsonObject)
        val rawParent = checkNotNull(selectedCategoryJson["parent"]?.jsonPrimitive?.int)
        val rawCount = checkNotNull(selectedCategoryJson["count"]?.jsonPrimitive?.int)
        assertion("CATEGORY_ID", selectedCategory.id > 0)
        assertion("CATEGORY_NAME", selectedCategory.name.isNotBlank())
        assertion("CATEGORY_SLUG", selectedCategory.slug.isNotBlank())
        assertion("CATEGORY_PARENT_MATCH", selectedCategory.parent == rawParent)
        assertion("CATEGORY_COUNT_MATCH", selectedCategory.count == rawCount)
        println("KMP_MODEL_CATEGORY_ID=" + selectedCategory.id)
        println("KMP_MODEL_CATEGORY_NAME=" + selectedCategory.name)
        println("KMP_MODEL_CATEGORY_SLUG_PRESENT=true")
        println("KMP_MODEL_CATEGORY_PARENT=" + selectedCategory.parent)
        println("KMP_MODEL_CATEGORY_COUNT=" + selectedCategory.count)
        return products to categories.size
    } catch (error: Throwable) {
        println("KMP_RUNTIME_STORE_ERROR message=${error.message}")
        throw error
    } finally {
        api.close()
    }
}

@Composable
fun CriosRangoSpikeScreen(session: StoreSessionStore = InMemoryStoreSessionStore()) {
    var state by remember { mutableStateOf<SpikeState>(SpikeState.Loading) }
    var reloadKey by remember { mutableStateOf(0) }
    LaunchedEffect(reloadKey) {
        state = runCatching { fetchStore(session) }.fold(
            onSuccess = { (products, categories) ->
                if (products.isEmpty()) SpikeState.Empty("No encontramos productos")
                else SpikeState.Content(products, categories)
            },
            onFailure = { error -> SpikeState.Error(error.message ?: "No se pudo cargar la tienda") }
        )
    }
    MaterialTheme {
        when (val current = state) {
            SpikeState.Loading -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(); Text("Cargando catálogo compartido", modifier = Modifier.padding(top = 12.dp))
            }
            is SpikeState.Content -> LazyColumn(
                modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { Text("Críos&Rango · KMP Store API", style = MaterialTheme.typography.headlineSmall) }
                item { Text("${current.products.size} productos · ${current.categoryCount} categorías") }
                items(current.products, key = { it.id }) { product -> Text(product.name, style = MaterialTheme.typography.bodyLarge) }
            }
            is SpikeState.Empty -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(current.message, style = MaterialTheme.typography.titleLarge)
            }
            is SpikeState.Error -> Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(current.message)
                Button(onClick = { state = SpikeState.Loading; reloadKey++ }, modifier = Modifier.padding(top = 16.dp)) { Text("Reintentar") }
            }
        }
    }
}
