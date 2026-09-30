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
import androidx.compose.ui.unit.dp
import es.criosrango.shared.api.StoreApiClient
import es.criosrango.shared.model.OutletAvailability
import es.criosrango.shared.model.StoreCategory
import es.criosrango.shared.model.StoreProduct
import es.criosrango.shared.model.countFor
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

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
            storeApi.products(perPage = perPage, page = page, orderBy = "date", order = "desc")
        },
        queryKey = "novedades:$audienceKey",
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
internal fun IosSearchScreen(storeApi: StoreApiClient,padding: PaddingValues,cartStore: StoreCartStore,onProduct:(StoreProduct)->Unit,onBack:()->Unit) {
    var query by remember { mutableStateOf("") }; var submitted by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(padding)) {
        Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically){TextButton(onClick=onBack){Text("Atrás")};Text("Buscar",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)}
        OutlinedTextField(query,{query=it},singleLine=true,label={Text("Buscar productos")},modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp),trailingIcon={TextButton(onClick={submitted=query.trim()}){Text("Buscar")}})
        if(submitted.isBlank()) IosStoreEmpty("Introduce un término de búsqueda.") else
            IosPagedProductScreen("",PaddingValues(),cartStore,onProduct,{}, {p,n->storeApi.products(p,n,search=submitted)},"search:$submitted")
    }
}

@Composable
internal fun IosBrandsScreen(storeApi:StoreApiClient,padding:PaddingValues,cartStore:StoreCartStore,onProduct:(StoreProduct)->Unit,onBack:()->Unit){
    var selected by remember { mutableStateOf<StoreBrand?>(null) }
    if(selected==null) Column(Modifier.fillMaxSize().padding(padding)){
        Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically){TextButton(onClick=onBack){Text("Atrás")};Text("Marcas",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)}
        LazyVerticalGrid(columns=GridCells.Fixed(2),contentPadding=PaddingValues(16.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            items(storeBrands,key={it.slug}){brand->OutlinedButton(onClick={selected=brand},modifier=Modifier.fillMaxWidth()){Text(brand.name)}}
        }
    } else IosPagedProductScreen(selected!!.name,padding,cartStore,onProduct,{selected=null},{p,n->storeApi.products(p,n,tag=selected!!.slug)},"brand:"+selected!!.slug)
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
    headerContent: (@Composable ColumnScope.() -> Unit)? = null
) {
    val scope=rememberCoroutineScope(); val paginator=remember(queryKey){CatalogPaginatorStore<StoreProduct>(scope){it.id}}; val state by paginator.state.collectAsState(); val grid=rememberLazyGridState()
    LaunchedEffect(queryKey){paginator.start(queryKey){p,n->val items=load(p,n);CatalogPage(items,items.size>=n)}}
    LaunchedEffect(grid,state.items.size,state.hasMore){snapshotFlow{grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index?:-1}.collect{last->if(state.hasMore&&!state.isInitialLoading&&!state.isAppending&&last>=state.items.size-CatalogPaginator.PREFETCH_DISTANCE)paginator.loadNext{p,n->val items=load(p,n);CatalogPage(items,items.size>=n)}}}
    Column(Modifier.fillMaxSize().padding(padding)){
        if(title.isNotBlank())Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically){TextButton(onClick=onBack){Text("Atrás")};Text(title,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)}
        when{
            state.isInitialLoading->IosStoreLoading()
            state.initialError!=null->IosStoreError(state.initialError!!.message?:"No se ha podido cargar."){paginator.start(queryKey){p,n->val items=load(p,n);CatalogPage(items,items.size>=n)}}
            state.items.isEmpty()->IosStoreEmpty(emptyMessage,onEmptyAction,emptyActionLabel)
            else->Column { headerContent?.invoke(); LazyVerticalGrid(columns=GridCells.Fixed(2),state=grid,contentPadding=PaddingValues(12.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
                items(transform(state.items),key={it.id}){IosProductCard(it,onProduct,cartStore)}
                if(state.isAppending)item(span={GridItemSpan(maxLineSpan)}){IosStoreLoading()}
                state.appendError?.let{e->item(span={GridItemSpan(maxLineSpan)}){IosStoreError(e.message?:"Error"){paginator.loadNext{p,n->val items=load(p,n);CatalogPage(items,items.size>=n)}}}}
            }
        }
    }
}