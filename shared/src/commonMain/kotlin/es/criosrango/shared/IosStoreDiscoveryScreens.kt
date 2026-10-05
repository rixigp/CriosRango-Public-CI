package es.criosrango.shared

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import es.criosrango.shared.api.StoreApiClient
import es.criosrango.shared.model.OutletAvailability
import es.criosrango.shared.model.StoreCategory
import es.criosrango.shared.model.StoreProduct
import es.criosrango.shared.model.countFor
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

internal fun String.normalizeForIosSearch(): String =
    lowercase()
        .replace(Regex("[áàäâã]"), "a")
        .replace(Regex("[éèëê]"), "e")
        .replace(Regex("[íìïî]"), "i")
        .replace(Regex("[óòöôõ]"), "o")
        .replace(Regex("[úùüû]"), "u")
        .replace('ñ', 'n')
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

internal fun iosSearchDistance(a: String, b: String): Int {
    if (a == b) return 0
    if (a.isEmpty()) return b.length
    if (b.isEmpty()) return a.length
    var previous = IntArray(b.length + 1) { it }
    for (i in a.indices) {
        val current = IntArray(b.length + 1)
        current[0] = i + 1
        for (j in b.indices) {
            val cost = if (a[i] == b[j]) 0 else 1
            current[j + 1] = minOf(current[j] + 1, previous[j + 1] + 1, previous[j] + cost)
        }
        previous = current
    }
    return previous[b.length]
}

internal fun iosSearchTokenMatches(token: String, text: String): Boolean {
    if (token.isBlank()) return true
    if (text.contains(token)) return true
    if (token.length < 4) return false
    val maxDistance = if (token.length >= 7) 2 else 1
    return text.split(" ").any { word ->
        word.isNotBlank() &&
            kotlin.math.abs(word.length - token.length) <= maxDistance &&
            iosSearchDistance(word, token) <= maxDistance
    }
}

internal fun iosProductSearchText(product: StoreProduct): String =
    buildString {
        append(product.name).append(' ')
        product.categories.forEach {
            append(it.name).append(' ')
            append(it.slug).append(' ')
        }
        product.tags.forEach {
            append(it.name).append(' ')
            append(it.slug).append(' ')
        }
        product.attributes.forEach { attribute ->
            append(attribute.name).append(' ')
            attribute.taxonomy?.let { append(it).append(' ') }
            attribute.terms.forEach {
                append(it.name).append(' ')
                append(it.slug).append(' ')
            }
        }
    }.normalizeForIosSearch()

internal fun iosProductMatchesSearch(product: StoreProduct, normalizedQuery: String): Boolean =
    normalizedQuery.isNotBlank() &&
        normalizedQuery.split(" ").filter { it.isNotBlank() }.all {
            iosSearchTokenMatches(it, iosProductSearchText(product))
        }

internal fun iosSearchRanking(product: StoreProduct, normalizedQuery: String): Int {
    val name = product.name.normalizeForIosSearch()
    val tokens = normalizedQuery.split(" ").filter { it.isNotBlank() }
    return when {
        name == normalizedQuery -> 1000
        name.startsWith(normalizedQuery) -> 800
        name.contains(normalizedQuery) -> 600
        else -> tokens.count { name.contains(it) } * 100
    }
}

internal enum class IosNovedadesAudience(val key: String, val label: String) {
    ALL("all", "Todas"),
    GIRL("girl", "Niña"),
    BOY("boy", "Niño"),
    BABY("baby", "Bebé"),
    WOMAN("woman", "Mujer"),
    MAN("man", "Hombre")
}

internal enum class IosNovedadesSort(val key: String, val label: String) {
    RECENT("recent", "Más recientes"),
    PRICE_ASC("price_asc", "Precio: menor a mayor"),
    PRICE_DESC("price_desc", "Precio: mayor a menor"),
    NAME_ASC("name_asc", "Nombre A-Z")
}

internal fun novedadesKey(value: String): String =
    value.lowercase().trim()
        .replace('á', 'a').replace('é', 'e').replace('í', 'i')
        .replace('ó', 'o').replace('ú', 'u').replace('ü', 'u').replace('ñ', 'n')

internal fun novedadesAudienceMatches(
    product: StoreProduct,
    audience: IosNovedadesAudience,
    allCategories: List<StoreCategory>
): Boolean {
    if (audience == IosNovedadesAudience.ALL) return true
    val byId = allCategories.associateBy { it.id }
    val names = mutableSetOf<String>()
    product.categories.forEach { productCategory ->
        names += novedadesKey(productCategory.name)
        var currentId = productCategory.id
        val visited = mutableSetOf<Int>()
        while (currentId != 0 && visited.add(currentId)) {
            val category = byId[currentId] ?: break
            names += novedadesKey(category.name)
            currentId = category.parent
        }
    }
    return when (audience) {
        IosNovedadesAudience.ALL -> true
        IosNovedadesAudience.GIRL -> "nina" in names
        IosNovedadesAudience.BOY -> "nino" in names
        IosNovedadesAudience.BABY -> "bebe nina" in names || "bebe nino" in names || "recien nacido" in names
        IosNovedadesAudience.WOMAN -> "mujer" in names
        IosNovedadesAudience.MAN -> "hombre" in names
    }
}

internal fun sortNovedadesProducts(
    products: List<StoreProduct>,
    sort: IosNovedadesSort
): List<StoreProduct> = when (sort) {
    IosNovedadesSort.RECENT -> products
    IosNovedadesSort.PRICE_ASC -> products.sortedBy { it.prices.price.toLongOrNull() ?: Long.MAX_VALUE }
    IosNovedadesSort.PRICE_DESC -> products.sortedByDescending { it.prices.price.toLongOrNull() ?: Long.MIN_VALUE }
    IosNovedadesSort.NAME_ASC -> products.sortedBy { it.name.lowercase() }
}

@Composable
internal fun IosNovedadesScreen(
    storeApi: StoreApiClient,
    padding: PaddingValues,
    cartStore: StoreCartStore,
    onProduct: (StoreProduct) -> Unit,
    onBack: () -> Unit
) {
    var audienceKey by rememberSaveable { mutableStateOf(IosNovedadesAudience.ALL.key) }
    var sortKey by rememberSaveable { mutableStateOf(IosNovedadesSort.RECENT.key) }
    val audience = IosNovedadesAudience.values().firstOrNull { it.key == audienceKey } ?: IosNovedadesAudience.ALL
    val sort = IosNovedadesSort.values().firstOrNull { it.key == sortKey } ?: IosNovedadesSort.RECENT
    var categories by remember { mutableStateOf(emptyList<StoreCategory>()) }

    LaunchedEffect(Unit) {
        categories = runCatching { storeApi.categories(perPage = 100) }.getOrDefault(emptyList())
    }

    IosPagedProductScreen(
        title = "Novedades",
        padding = padding,
        cartStore = cartStore,
        onProduct = onProduct,
        onBack = onBack,
        load = { page, perPage ->
            val orderBy = when (sort) {
                IosNovedadesSort.RECENT -> "date"
                IosNovedadesSort.PRICE_ASC, IosNovedadesSort.PRICE_DESC -> "price"
                IosNovedadesSort.NAME_ASC -> "title"
            }
            val order = when (sort) {
                IosNovedadesSort.PRICE_DESC -> "desc"
                else -> "asc"
            }
            storeApi.products(perPage = perPage, page = page, orderBy = orderBy, order = if (sort == IosNovedadesSort.RECENT) "desc" else order)
        },
        queryKey = "novedades:$audienceKey:$sortKey",
        transform = { products ->
            sortNovedadesProducts(
                products.filter { novedadesAudienceMatches(it, audience, categories) },
                sort
            )
        },
        emptyMessage = if (audience == IosNovedadesAudience.ALL) "No hay novedades." else "No hay productos con estos filtros.",
        emptyActionLabel = if (audience == IosNovedadesAudience.ALL) null else "Borrar filtros",
        onEmptyAction = if (audience == IosNovedadesAudience.ALL) null else { { audienceKey = IosNovedadesAudience.ALL.key } },
        headerContent = {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                items(IosNovedadesAudience.values(), key = { it.key }) { option ->
                    FilterChip(
                        selected = audience == option,
                        onClick = { audienceKey = option.key },
                        label = { Text(option.label) }
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (audience != IosNovedadesAudience.ALL) {
                    TextButton(onClick = { audienceKey = IosNovedadesAudience.ALL.key }) {
                        Text("Borrar filtros")
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                }
                Box {
                    var expanded by remember { mutableStateOf(false) }
                    TextButton(onClick = { expanded = true }) {
                        Text("Ordenar: ${sort.label} ▾")
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        IosNovedadesSort.values().forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    expanded = false
                                    sortKey = option.key
                                }
                            )
                        }
                    }
                }
            }
        }
    )
}

@Composable
internal fun IosSearchScreen(
    storeApi: StoreApiClient,
    padding: PaddingValues,
    cartStore: StoreCartStore,
    onProduct: (StoreProduct) -> Unit,
    onCategory: (StoreCategory) -> Unit,
    onBack: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf("") }
    var products by remember { mutableStateOf(emptyList<StoreProduct>()) }
    var categories by remember { mutableStateOf(emptyList<StoreCategory>()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var retryAttempt by remember { mutableStateOf(0) }
    val normalizedQuery = submitted.normalizeForIosSearch()
    val gridState = rememberLazyGridState()

    LaunchedEffect(Unit) {
        categories = runCatching { storeApi.categories(perPage = 100) }.getOrDefault(emptyList())
    }

    LaunchedEffect(normalizedQuery, retryAttempt) {
        if (normalizedQuery.isBlank()) {
            products = emptyList()
            loading = false
            error = null
            return@LaunchedEffect
        }
        loading = true
        error = null
        runCatching {
            val tokens = normalizedQuery.split(" ").filter { it.length >= 2 }.distinct()
            coroutineScope {
                if (tokens.size <= 1) {
                    storeApi.products(perPage = 24, page = 1, search = submitted.trim())
                } else {
                    tokens.map { token ->
                        async { storeApi.products(perPage = 24, page = 1, search = token) }
                    }.awaitAll().flatten().distinctBy { it.id }
                }
            }
        }.onSuccess {
            products = it
            loading = false
        }.onFailure {
            products = emptyList()
            error = it.message ?: "No se ha podido buscar."
            loading = false
        }
    }

    val displayedProducts = remember(products, normalizedQuery) {
        products
            .distinctBy { it.id }
            .filter { iosProductMatchesSearch(it, normalizedQuery) }
            .sortedByDescending { iosSearchRanking(it, normalizedQuery) }
    }
    val categorySuggestions = remember(categories, normalizedQuery) {
        if (normalizedQuery.length < 2) emptyList()
        else {
            val tokens = normalizedQuery.split(" ").filter { it.isNotBlank() }
            categories.filter { category ->
                val text = category.name.normalizeForIosSearch()
                tokens.any { iosSearchTokenMatches(it, text) }
            }.distinctBy { it.name.normalizeForIosSearch() }.take(2)
        }
    }
    val brandSuggestions = remember(normalizedQuery) {
        if (normalizedQuery.length < 2) emptyList()
        else {
            val tokens = normalizedQuery.split(" ").filter { it.isNotBlank() }
            storeBrands.filter { brand ->
                val text = brand.name.normalizeForIosSearch()
                tokens.any { iosSearchTokenMatches(it, text) }
            }.take(2)
        }
    }

    Column(Modifier.fillMaxSize().padding(padding)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Atrás") }
            Text("Buscar", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        OutlinedTextField(
            query,
            { query = it },
            singleLine = true,
            label = { Text("Buscar productos") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            trailingIcon = { TextButton(onClick = { submitted = query.trim() }) { Text("Buscar") } }
        )
        if (submitted.isBlank()) {
            IosStoreEmpty("Introduce un término de búsqueda.")
        } else {
            if (categorySuggestions.isNotEmpty() || brandSuggestions.isNotEmpty() || displayedProducts.isNotEmpty()) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Text("Sugerencias", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    categorySuggestions.forEach { category ->
                        TextButton(
                            modifier = Modifier.height(30.dp),
                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                            onClick = { onCategory(category) }
                        ) { Text("Categoría · ${category.name}") }
                    }
                    brandSuggestions.forEach { brand ->
                        TextButton(
                            modifier = Modifier.height(30.dp),
                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                            onClick = { query = brand.name; submitted = brand.name }
                        ) { Text("Marca · ${brand.name}") }
                    }
                    displayedProducts.take(2).forEach { product ->
                        TextButton(
                            modifier = Modifier.height(30.dp),
                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                            onClick = { onProduct(product) }
                        ) { Text(product.name) }
                    }
                }
            }
            when {
                loading -> IosStoreLoading()
                error != null -> IosStoreError(error!!) { retryAttempt++ }
                displayedProducts.isEmpty() -> IosStoreEmpty("No hemos encontrado productos")
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    state = gridState,
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(displayedProducts, key = { it.id }) {
                        IosProductCard(it, onProduct, cartStore)
                    }
                }
            }
        }
    }
}

@Composable
internal fun IosBrandsScreen(storeApi:StoreApiClient,padding:PaddingValues,cartStore:StoreCartStore,onProduct:(StoreProduct)->Unit,onBack:()->Unit){
    var selected by remember { mutableStateOf<StoreBrand?>(null) }
    if(selected==null) Column(Modifier.fillMaxSize().padding(padding)){
        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=4.dp),verticalAlignment=Alignment.CenterVertically){TextButton(onClick=onBack,contentPadding=PaddingValues(horizontal=8.dp,vertical=4.dp)){Text("Atrás")};Text("Nuestras marcas",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis)}
        LazyVerticalGrid(columns=GridCells.Fixed(2),contentPadding=PaddingValues(start=16.dp,top=4.dp,end=16.dp,bottom=28.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            items(storeBrands,key={it.slug}){brand->OutlinedButton(onClick={selected=brand},modifier=Modifier.fillMaxWidth()){Text(brand.name)}}
        }
    } else IosPagedProductScreen(selected!!.name,padding,cartStore,onProduct,{selected=null},{p,n->storeApi.products(p,n,tag=selected!!.slug)},"brand:"+selected!!.slug,enableProductControls=true)
}

@Composable
private fun IosDiscoveryFilterChoices(
    title: String,
    values: List<String>,
    selected: Set<String>,
    onChange: (Set<String>) -> Unit
) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(6.dp))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        values.forEach { value ->
            FilterChip(
                selected = value in selected,
                onClick = { onChange(if (value in selected) selected - value else selected + value) },
                label = { Text(value) }
            )
        }
    }
}

@Composable
internal fun IosOutletScreen(storeApi:StoreApiClient,padding:PaddingValues,cartStore:StoreCartStore,categories:List<StoreCategory>,onProduct:(StoreProduct)->Unit,onBack:()->Unit){
    val outlet=categories.firstOrNull{it.parent==0&&it.name.contains("outlet",true)}
    if(outlet==null){IosStoreEmpty("Outlet no disponible.");return}
    val bubbles=remember(outlet.id){fixedOutletBubbles(outlet.id)}; var selected by remember(outlet.id){mutableStateOf<String?>(null)}
    var availability by remember(outlet.id){mutableStateOf<OutletAvailability?>(null)}
    var availabilityError by remember(outlet.id){mutableStateOf<String?>(null)}
    LaunchedEffect(outlet.id){runCatching{storeApi.outletAvailability()}.onSuccess{availability=it}.onFailure{availabilityError=it.message?:"No se ha podido cargar la disponibilidad."}}
    val visible=if(availability==null)bubbles else bubbles.filter{b->b.categoryIds.any{availability!!.countFor(outlet.id,it)>0}}
    LaunchedEffect(visible,selected){if(selected!=null&&visible.none{it.key==selected})selected=null}
    Column(Modifier.fillMaxSize().padding(padding)){
        Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically){TextButton(onClick=onBack){Text("Atrás")};Text("Outlet",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)}
        LazyRow(contentPadding=PaddingValues(horizontal=12.dp),horizontalArrangement=Arrangement.spacedBy(7.dp)){
            item{FilterChip(selected==null,{selected=null},label={Text("Todas")})}
            items(visible,key={it.key}){b->FilterChip(selected==b.key,{selected=b.key},label={Text(outletBubbleDisplayLabel(b.label))})}
        }
        availabilityError?.let{Text(it,modifier=Modifier.padding(12.dp))}
        val selectedBubble = selected?.let { key -> visible.firstOrNull { it.key == key } }
        val queryKey = "outlet:" + outlet.id + ":" + (selectedBubble?.key ?: "all")
        IosPagedProductScreen("",PaddingValues(),cartStore,onProduct,{}, { p,n ->
            if (selectedBubble == null) storeApi.products(p,n,category=outlet.id)
            else coroutineScope {
                selectedBubble.categoryIds.map { categoryId -> async { storeApi.products(p,n,category=categoryId) } }.awaitAll().flatten().distinctBy { it.id }
            }
        },queryKey)
    }
}

@Composable
private fun IosPagedProductScreen(
    title: String,
    padding: PaddingValues,
    cartStore: StoreCartStore,
    onProduct: (StoreProduct) -> Unit,
    onBack: () -> Unit,
    load: suspend (Int, Int) -> List<StoreProduct>,
    queryKey: String,
    transform: (List<StoreProduct>) -> List<StoreProduct> = { it },
    emptyMessage: String = "No hay productos.",
    emptyActionLabel: String? = null,
    onEmptyAction: (() -> Unit)? = null,
    headerContent: (@Composable ColumnScope.() -> Unit)? = null,
    enableProductControls: Boolean = false
) {
    val scope=rememberCoroutineScope(); val paginator=remember(queryKey){CatalogPaginatorStore<StoreProduct>(scope, pageSize = if (queryKey.startsWith("novedades:")) 24 else CatalogPaginator.PAGE_SIZE) { it.id }}; val state by paginator.state.collectAsState(); val grid=rememberLazyGridState()
    var sortMode by rememberSaveable(queryKey) { mutableStateOf(IosCatalogSortModeState.RECENT) }
    var filtersOpen by rememberSaveable(queryKey) { mutableStateOf(false) }
    var selectedSizes by rememberSaveable(queryKey) { mutableStateOf(setOf<String>()) }
    var selectedColors by rememberSaveable(queryKey) { mutableStateOf(setOf<String>()) }
    var selectedBrands by rememberSaveable(queryKey) { mutableStateOf(setOf<String>()) }
    val rawItems = state.items
    fun productValues(product: StoreProduct, attribute: String): Set<String> =
        product.attributes.filter { it.name.equals(attribute, true) }.flatMap { it.terms.map { term -> term.name } }.filter { it.isNotBlank() }.toSet()
    fun matches(product: StoreProduct): Boolean {
        val sizes = productValues(product, "Tallas")
        val colors = productValues(product, "Color")
        val brands = product.tags.map { it.name }.filter { it.isNotBlank() }.toSet()
        return (selectedSizes.isEmpty() || sizes.any { it in selectedSizes }) &&
            (selectedColors.isEmpty() || colors.any { it in selectedColors }) &&
            (selectedBrands.isEmpty() || brands.any { it in selectedBrands })
    }
    val filteredItems = rawItems.filter(::matches)
    val displayedItems = when (sortMode) {
        IosCatalogSortModeState.RECENT -> filteredItems
        IosCatalogSortModeState.PRICE_ASC -> filteredItems.sortedBy { it.prices.price.toLongOrNull() ?: Long.MAX_VALUE }
        IosCatalogSortModeState.PRICE_DESC -> filteredItems.sortedByDescending { it.prices.price.toLongOrNull() ?: Long.MIN_VALUE }
        IosCatalogSortModeState.NAME_ASC -> filteredItems.sortedBy { it.name.lowercase() }
    }
    val availableSizes = rawItems.flatMap { productValues(it, "Tallas") }.distinct().sorted()
    val availableColors = rawItems.flatMap { productValues(it, "Color") }.distinct().sorted()
    val availableBrands = rawItems.flatMap { it.tags.map { tag -> tag.name } }.filter { it.isNotBlank() }.distinct().sorted()
    val activeFilterCount = selectedSizes.size + selectedColors.size + selectedBrands.size
    LaunchedEffect(queryKey){paginator.start(queryKey){p,n->val items=load(p,n);CatalogPage(items,items.size>=n)}}
    LaunchedEffect(grid,state.items.size,state.hasMore){snapshotFlow{grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index?:-1}.collect{last->if(state.hasMore&&!state.isInitialLoading&&!state.isAppending&&last>=state.items.size-CatalogPaginator.PREFETCH_DISTANCE)paginator.loadNext{p,n->val items=load(p,n);CatalogPage(items,items.size>=n)}}}
    Column(Modifier.fillMaxSize().padding(padding)){
        if(title.isNotBlank())Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically){TextButton(onClick=onBack){Text("Atrás")};Text(title,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis)}
        if (enableProductControls && state.items.isNotEmpty()) {
            if (activeFilterCount > 0) {
                FlowRow(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    selectedSizes.sorted().forEach { value -> FilterChip(true, { selectedSizes = selectedSizes - value }, label = { Text("Talla " + value) }, trailingIcon = { Text("×") }) }
                    selectedColors.sorted().forEach { value -> FilterChip(true, { selectedColors = selectedColors - value }, label = { Text(value) }, trailingIcon = { Text("×") }) }
                    selectedBrands.sorted().forEach { value -> FilterChip(true, { selectedBrands = selectedBrands - value }, label = { Text(value) }, trailingIcon = { Text("×") }) }
                    TextButton(onClick = { selectedSizes = emptySet(); selectedColors = emptySet(); selectedBrands = emptySet() }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) { Text("Borrar filtros") }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Ordenar por", style = MaterialTheme.typography.bodyMedium)
                Box {
                    var expanded by remember(queryKey) { mutableStateOf(false) }
                    TextButton(onClick = { expanded = true }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                        Text("${when (sortMode) {
    IosCatalogSortModeState.RECENT -> "Más recientes"
    IosCatalogSortModeState.PRICE_ASC -> "Precio: menor a mayor"
    IosCatalogSortModeState.PRICE_DESC -> "Precio: mayor a menor"
    IosCatalogSortModeState.NAME_ASC -> "Nombre A-Z"
}}  ▾")
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        IosCatalogSortModeState.values().forEach { option ->
                            DropdownMenuItem(
                                text = { Text(when(option) {
                                    IosCatalogSortModeState.RECENT -> "Más recientes"
                                    IosCatalogSortModeState.PRICE_ASC -> "Precio: menor a mayor"
                                    IosCatalogSortModeState.PRICE_DESC -> "Precio: mayor a menor"
                                    IosCatalogSortModeState.NAME_ASC -> "Nombre A-Z"
                                }) },
                                onClick = { expanded = false; sortMode = option }
                            )
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = { filtersOpen = true }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                    Text(if (activeFilterCount == 0) "Filtros" else "Filtros (" + activeFilterCount + ")")
                }
            }
        }
        if (enableProductControls && filtersOpen) {
            AlertDialog(
                onDismissRequest = { filtersOpen = false },
                title = { Text("Filtros") },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        IosDiscoveryFilterChoices("Talla", availableSizes, selectedSizes) { selectedSizes = it }
                        Spacer(Modifier.height(16.dp))
                        IosDiscoveryFilterChoices("Color", availableColors, selectedColors) { selectedColors = it }
                        if (availableBrands.isNotEmpty()) {
                            Spacer(Modifier.height(16.dp))
                            IosDiscoveryFilterChoices("Marca", availableBrands, selectedBrands) { selectedBrands = it }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { filtersOpen = false }) { Text("Aplicar") } },
                dismissButton = { TextButton(onClick = { selectedSizes = emptySet(); selectedColors = emptySet(); selectedBrands = emptySet() }) { Text("Limpiar") } }
            )
        }

        when{
            state.isInitialLoading->IosStoreLoading()
            state.initialError!=null->IosStoreError(state.initialError!!.message?:"No se ha podido cargar."){paginator.start(queryKey){p,n->val items=load(p,n);CatalogPage(items,items.size>=n)}}
            state.items.isEmpty()->IosStoreEmpty(emptyMessage,onEmptyAction,emptyActionLabel)
            else->Column { headerContent?.invoke(this); LazyVerticalGrid(columns=GridCells.Fixed(2),state=grid,contentPadding=PaddingValues(start=12.dp,top=6.dp,end=12.dp,bottom=24.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                items(transform(displayedItems),key={it.id}){IosProductCard(it,onProduct,cartStore)}
                if(state.isAppending)item(span={GridItemSpan(maxLineSpan)}){IosStoreLoading()}
                state.appendError?.let{e->item(span={GridItemSpan(maxLineSpan)}){IosStoreError(e.message?:"Error"){paginator.loadNext{p,n->val items=load(p,n);CatalogPage(items,items.size>=n)}}}}
            }
            }
        }
    }
}