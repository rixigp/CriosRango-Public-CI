package es.criosrango.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import es.criosrango.shared.api.StoreApiClient
import es.criosrango.shared.model.StoreCategory
import es.criosrango.shared.model.StoreGlobalBrand
import es.criosrango.shared.model.StoreProduct
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

const val STORE_SEARCH_DEBOUNCE_MS = 350L

data class StoreSearchState(
    val query: String = "",
    val products: List<StoreProduct> = emptyList(),
    val categories: List<StoreCategory> = emptyList(),
    val brands: List<StoreGlobalBrand> = emptyList(),
    val productSuggestions: List<StoreProduct> = emptyList(),
    val isLoading: Boolean = false,
    val hasSearched: Boolean = false,
    val errorMessage: String? = null
)

internal interface StoreSearchDataSource {
    suspend fun search(query: String): List<StoreProduct>
    suspend fun categories(): List<StoreCategory>
    suspend fun brands(): List<StoreGlobalBrand>
}

internal class StoreApiSearchDataSource(private val api: StoreApiClient) : StoreSearchDataSource {
    override suspend fun search(query: String): List<StoreProduct> {
        val normalized = normalizeStoreSearch(query)
        if (normalized.isBlank()) return emptyList()
        val tokens = normalized.split(" ").filter(String::isNotBlank).distinct()
        val responses = kotlinx.coroutines.coroutineScope {
            tokens.map { token -> async { api.products(perPage = 24, page = 1, search = token) } }.awaitAll()
        }
        return rankStoreSearchResults(responses.flatten(), normalized)
    }
    override suspend fun categories() = api.categories(perPage = 100)
    override suspend fun brands(): List<StoreGlobalBrand> {
        val all = mutableListOf<StoreGlobalBrand>()
        var page = 1
        while (page <= 10) {
            val batch = api.productBrands(perPage = 100, page = page, hideEmpty = true)
            all += batch
            if (batch.size < 100) break
            page++
        }
        return all.distinctBy { it.id }.filter { it.count > 0 }.sortedBy { normalizeStoreSearch(it.name) }
    }
}

class StoreSearchController internal constructor(
    private val scope: CoroutineScope,
    private val source: StoreSearchDataSource,
    private val debounceMillis: Long = STORE_SEARCH_DEBOUNCE_MS
) {
    val scrollState = androidx.compose.foundation.lazy.grid.LazyGridState()
    private val _state = MutableStateFlow(StoreSearchState())
    val state: StateFlow<StoreSearchState> = _state.asStateFlow()
    private var debounceJob: Job? = null
    private var requestJob: Job? = null
    private var generation = 0L
    private var suggestionsJob: Job? = null
    private var suggestionsLoaded = false
    private val requestMutex = Mutex()

    fun loadSuggestions() {
        if (suggestionsLoaded || suggestionsJob?.isActive == true) return
        suggestionsJob = scope.launch {
            val categories = runCatching { source.categories() }.getOrDefault(emptyList())
            val brands = runCatching { source.brands() }.getOrDefault(emptyList())
            _state.value = _state.value.copy(categories = categories, brands = brands)
            suggestionsLoaded = true
        }
    }

    fun onQueryChanged(query: String) {
        val version = ++generation
        debounceJob?.cancel()
        requestJob?.cancel()
        val clean = query.trim()
        if (clean.isBlank()) {
            _state.value = _state.value.copy(query = "", products = emptyList(), productSuggestions = emptyList(), isLoading = false, hasSearched = false, errorMessage = null)
            return
        }
        _state.value = _state.value.copy(query = query, products = emptyList(), productSuggestions = emptyList(), isLoading = true, hasSearched = false, errorMessage = null)
        debounceJob = scope.launch {
            delay(debounceMillis)
            if (version != generation) return@launch
            requestJob = launch {
                try {
                    val results = source.search(clean)
                    if (version == generation && normalizeStoreSearch(_state.value.query) == normalizeStoreSearch(clean)) {
                        _state.value = _state.value.copy(products = rankStoreSearchResults(results, clean), productSuggestions = storeSearchProductSuggestions(results, clean), isLoading = false, hasSearched = true, errorMessage = null)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    if (version == generation) _state.value = _state.value.copy(products = emptyList(), productSuggestions = emptyList(), isLoading = false, hasSearched = true, errorMessage = error.message ?: "No se ha podido buscar.")
                }
            }
        }
    }

    fun clear() = onQueryChanged("")
    fun retry() {
        val query = _state.value.query
        if (query.isNotBlank()) {
            generation++
            debounceJob?.cancel()
            requestJob?.cancel()
            val version = generation
            _state.value = _state.value.copy(isLoading = true, errorMessage = null, hasSearched = false, products = emptyList(), productSuggestions = emptyList())
            requestJob = scope.launch {
                try {
                    val results = source.search(query.trim())
                    if (version == generation) _state.value = _state.value.copy(products = rankStoreSearchResults(results, query), productSuggestions = storeSearchProductSuggestions(results, query), isLoading = false, hasSearched = true, errorMessage = null)
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Throwable) {
                    if (version == generation) _state.value = _state.value.copy(isLoading = false, hasSearched = true, errorMessage = error.message ?: "No se ha podido buscar.")
                }
            }
        }
    }
}

fun normalizeStoreSearch(value: String): String = value.lowercase()
    .replace("á","a").replace("à","a").replace("ä","a").replace("â","a")
    .replace("é","e").replace("è","e").replace("ë","e").replace("ê","e")
    .replace("í","i").replace("ì","i").replace("ï","i").replace("î","i")
    .replace("ó","o").replace("ò","o").replace("ö","o").replace("ô","o")
    .replace("ú","u").replace("ù","u").replace("ü","u").replace("û","u")
    .replace("ñ","n").replace("ç","c")
    .map { if (it.isLetterOrDigit()) it else ' ' }.joinToString("").trim().split(Regex("\\s+")).filter(String::isNotBlank).joinToString(" ")

fun storeSearchTokenMatches(token: String, text: String): Boolean {
    if (token.isBlank()) return true
    if (text.contains(token)) return true
    if (token.length < 4) return false
    val maxDistance = if (token.length >= 7) 2 else 1
    return text.split(" ").any { word -> word.isNotBlank() && kotlin.math.abs(word.length - token.length) <= maxDistance && storeSearchDistance(word, token) <= maxDistance }
}

fun storeSearchCategorySuggestions(categories: List<StoreCategory>, query: String): List<StoreCategory> {
    val tokens = normalizeStoreSearch(query).split(" ").filter(String::isNotBlank)
    if (tokens.isEmpty()) return emptyList()
    return categories.filter { category -> tokens.any { storeSearchTokenMatches(it, normalizeStoreSearch(category.name)) } }
        .distinctBy { normalizeStoreSearch(it.name) }.take(2)
}

fun storeSearchBrandSuggestions(brands: List<StoreGlobalBrand>, query: String): List<StoreGlobalBrand> {
    val tokens = normalizeStoreSearch(query).split(" ").filter(String::isNotBlank)
    if (tokens.isEmpty()) return emptyList()
    return brands.filter { brand -> tokens.any { storeSearchTokenMatches(it, normalizeStoreSearch(brand.name)) } }
        .distinctBy { it.id }.take(2)
}

/** Top three unique product matches, ordered by the same shared matching/ranking rules as results. */
fun storeSearchProductSuggestions(products: List<StoreProduct>, query: String): List<StoreProduct> =
    rankStoreSearchResults(products, query).take(3)

private fun storeSearchDistance(a: String, b: String): Int {
    if (a == b) return 0
    var previous = IntArray(b.length + 1) { it }
    for (i in a.indices) {
        val current = IntArray(b.length + 1); current[0] = i + 1
        for (j in b.indices) current[j + 1] = minOf(current[j] + 1, previous[j + 1] + 1, previous[j] + if (a[i] == b[j]) 0 else 1)
        previous = current
    }
    return previous[b.length]
}

fun storeSearchRanking(product: StoreProduct, query: String): Int {
    val normalizedQuery = normalizeStoreSearch(query)
    val name = normalizeStoreSearch(product.name)
    val tokens = normalizedQuery.split(" ").filter(String::isNotBlank)
    return when {
        name == normalizedQuery -> 1000
        name.startsWith(normalizedQuery) -> 800
        name.contains(normalizedQuery) -> 600
        else -> tokens.count { token -> name.split(" ").any { storeSearchTokenMatches(token, it) } } * 100
    }
}

fun rankStoreSearchResults(products: List<StoreProduct>, query: String): List<StoreProduct> {
    val normalized = normalizeStoreSearch(query)
    return products.distinctBy { it.id }
        .filter { product -> normalized.isNotBlank() && normalized.split(" ").filter(String::isNotBlank).all { token ->
            val text = normalizeStoreSearch(buildString {
                append(product.name).append(' ')
                product.categories.forEach { append(it.name).append(' ').append(it.slug).append(' ') }
                product.tags.forEach { append(it.name).append(' ').append(it.slug).append(' ') }
                product.attributes.forEach { attribute -> append(attribute.name).append(' '); attribute.terms.forEach { append(it.name).append(' ').append(it.slug).append(' ') } }
            })
            storeSearchTokenMatches(token, text)
        } }.sortedWith(compareByDescending<StoreProduct> { storeSearchRanking(it, normalized) }.thenBy { it.id })
}

@Composable
fun StoreSearchScreen(
    storeApi: StoreApiClient,
    controller: StoreSearchController,
    padding: PaddingValues,
    onProduct: (StoreProduct) -> Unit,
    onCategory: (StoreCategory) -> Unit,
    onBack: (() -> Unit)? = null
) {
    val state by controller.state.collectAsState()
    val gridState = controller.scrollState
    LaunchedEffect(controller) { controller.loadSuggestions() }
    Column(Modifier.fillMaxSize().padding(padding)) {
        OutlinedTextField(
            value = state.query,
            onValueChange = controller::onQueryChanged,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            placeholder = { Text("Buscar prendas, marcas...") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = { if (state.query.isNotEmpty()) IconButton(onClick = controller::clear) { Icon(Icons.Default.Close, contentDescription = "Limpiar") } },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { })
        )
        if (state.query.isBlank()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text("Busca tu próxima prenda favorita")
            }
        } else {
            if (state.categories.isNotEmpty() || state.brands.isNotEmpty() || state.productSuggestions.isNotEmpty()) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                    Text("Sugerencias", style = MaterialTheme.typography.titleSmall)
                    storeSearchCategorySuggestions(state.categories, state.query).forEach { category ->
                        Text("Categoría · ${category.name}", Modifier.fillMaxWidth().clickable { onCategory(category) }.padding(vertical = 5.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    storeSearchBrandSuggestions(state.brands, state.query).forEach { brand ->
                        Text("Marca · ${brand.name}", Modifier.fillMaxWidth().clickable { controller.onQueryChanged(brand.name) }.padding(vertical = 5.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    state.productSuggestions.forEach { product ->
                        Text("Producto · ${normalizeWooPlainText(product.name)}", Modifier.fillMaxWidth().clickable { onProduct(product) }.padding(vertical = 5.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            when {
                state.isLoading -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                state.errorMessage != null -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(state.errorMessage!!); TextButton(onClick = controller::retry) { Text("Reintentar") } } }
                state.hasSearched && state.products.isEmpty() -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { Text("No hemos encontrado productos") }
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    state = gridState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(state.products, key = { it.id }) { product ->
                        val price = product.prices.price.toLongOrNull() ?: 0L
                        val regular = product.prices.regularPrice.toLongOrNull() ?: 0L
                        val item = StoreProductItemUi(
                            id = product.id, nameText = normalizeWooPlainText(product.name), imageIdentity = product.images.firstOrNull()?.src,
                            displayPriceText = storeSearchPrice(price, product.prices.currencySymbol, product.prices.currencyMinorUnit),
                            regularPriceText = storeSearchPrice(regular, product.prices.currencySymbol, product.prices.currencyMinorUnit),
                            showPrice = price > 0L, isOnSale = product.onSale
                        )
                        StoreProductCard(item, onClick = { onProduct(product) }) { card, imageModifier ->
                            RemoteStoreImage(card.imageIdentity, card.nameText, imageModifier, androidx.compose.ui.layout.ContentScale.Crop)
                        }
                    }
                }
            }
        }
    }
}

private fun storeSearchPrice(value: Long, symbol: String, minorUnit: Int): String {
    val divisor = when (minorUnit) { 0 -> 1L; 1 -> 10L; 2 -> 100L; 3 -> 1000L; else -> 100L }
    val amount = value.toDouble() / divisor.toDouble()
    return amount.toString().let { raw ->
        val formatted = if (minorUnit == 0) amount.toLong().toString() else {
            val fixed = (amount * divisor).toLong()
            val whole = fixed / divisor
            val fraction = (fixed % divisor).toString().padStart(minorUnit, '0')
            "$whole,$fraction"
        }
        "$formatted $symbol"
    }
}
