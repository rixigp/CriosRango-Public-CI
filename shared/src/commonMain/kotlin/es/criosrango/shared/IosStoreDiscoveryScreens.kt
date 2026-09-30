package es.criosrango.shared

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import es.criosrango.shared.api.StoreApiClient
import es.criosrango.shared.model.OutletAvailability
import es.criosrango.shared.model.StoreCategory
import es.criosrango.shared.model.StoreProduct
import es.criosrango.shared.model.countFor

@Composable
internal fun IosNovedadesScreen(storeApi: StoreApiClient,padding: PaddingValues,cartStore: StoreCartStore,onProduct:(StoreProduct)->Unit,onBack:()->Unit) =
    IosPagedProductScreen("Novedades",padding,cartStore,onProduct,onBack,{p,n->storeApi.products(p,n,orderBy="date",order="desc")},"novedades")

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
        IosPagedProductScreen("",PaddingValues(),cartStore,onProduct,{}, {p,n->storeApi.products(p,n,category=outlet.id)},"outlet:"+outlet.id)
    }
}

@Composable
private fun IosPagedProductScreen(title:String,padding:PaddingValues,cartStore:StoreCartStore,onProduct:(StoreProduct)->Unit,onBack:()->Unit,load:suspend(Int,Int)->List<StoreProduct>,queryKey:String){
    val scope=rememberCoroutineScope(); val paginator=remember(queryKey){CatalogPaginatorStore<StoreProduct>(scope){it.id}}; val state by paginator.state.collectAsState(); val grid=rememberLazyGridState()
    LaunchedEffect(queryKey){paginator.start(queryKey){p,n->val items=load(p,n);CatalogPage(items,items.size>=n)}}
    LaunchedEffect(grid,state.items.size,state.hasMore){snapshotFlow{grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index?:-1}.collect{last->if(state.hasMore&&!state.isInitialLoading&&!state.isAppending&&last>=state.items.size-CatalogPaginator.PREFETCH_DISTANCE)paginator.loadNext{p,n->val items=load(p,n);CatalogPage(items,items.size>=n)}}}
    Column(Modifier.fillMaxSize().padding(padding)){
        if(title.isNotBlank())Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically){TextButton(onClick=onBack){Text("Atrás")};Text(title,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)}
        when{
            state.isInitialLoading->IosStoreLoading()
            state.initialError!=null->IosStoreError(state.initialError!!.message?:"No se ha podido cargar."){paginator.start(queryKey){p,n->val items=load(p,n);CatalogPage(items,items.size>=n)}}
            state.items.isEmpty()->IosStoreEmpty("No hay productos.")
            else->LazyVerticalGrid(columns=GridCells.Fixed(2),state=grid,contentPadding=PaddingValues(12.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
                items(state.items,key={it.id}){IosProductCard(it,onProduct,cartStore)}
                if(state.isAppending)item(span={GridItemSpan(maxLineSpan)}){IosStoreLoading()}
                state.appendError?.let{e->item(span={GridItemSpan(maxLineSpan)}){IosStoreError(e.message?:"Error"){paginator.loadNext{p,n->val items=load(p,n);CatalogPage(items,items.size>=n)}}}}
            }
        }
    }
}