package es.criosrango.shared

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import es.criosrango.shared.api.StoreApiClient
import es.criosrango.shared.model.StoreProduct
import kotlinx.coroutines.flow.collect

@Composable
fun StoreNovedadesScreen(
    storeApi:StoreApiClient,
    padding:PaddingValues,
    cartStore:StoreCartStore?=null,
    onProduct:(StoreProduct)->Unit,
    onBack:()->Unit
){
    val scope=rememberCoroutineScope()
    val controller=remember(storeApi){StoreNovedadesController(scope,StoreApiNovedadesDataSource(storeApi))}
    val state by controller.state.collectAsState()
    val grid=rememberLazyGridState()
    var sortExpanded by remember{mutableStateOf(false)}
    LaunchedEffect(controller){controller.start()}
    LaunchedEffect(controller,state.queryIdentity,state.paging.items.size,state.paging.hasMore,state.paging.isInitialLoading){
        snapshotFlow{grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index?:-1}.collect{last->
            if(state.paging.hasMore&&!state.paging.isInitialLoading&&!state.paging.isAppending&&state.paging.currentPage>0&&last>=state.paging.items.size-CatalogPaginator.PREFETCH_DISTANCE)
                controller.loadNextPage(state.queryIdentity)
        }
    }
    Column(Modifier.fillMaxSize().padding(padding)){
        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=4.dp),verticalAlignment=Alignment.CenterVertically){
            TextButton(onClick=onBack){Text("Atrás")}
            Column(Modifier.weight(1f)){
                Text("Novedades",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                Text("Últimas incorporaciones",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick={controller.refresh()}){Text("Actualizar")}
        }
        LazyRow(contentPadding=PaddingValues(horizontal=12.dp),horizontalArrangement=Arrangement.spacedBy(7.dp)){
            items(StoreNovedadesAudience.values().toList(),key={it.key}){option->
                FilterChip(selected=state.selectedAudience==option,onClick={controller.selectAudience(option)},label={Text(option.label)})
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=4.dp),verticalAlignment=Alignment.CenterVertically){
            Text((state.paging.totalItems?:state.paging.items.size).toString()+" productos",modifier=Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Box{
                TextButton(onClick={sortExpanded=true}){Text("Ordenar: "+state.selectedSort.label+" ▾")}
                DropdownMenu(expanded=sortExpanded,onDismissRequest={sortExpanded=false}){
                    StoreNovedadesSort.values().forEach{option->DropdownMenuItem(text={Text(option.label)},onClick={sortExpanded=false;controller.selectSort(option)})}
                }
            }
        }
        when{
            state.paging.isInitialLoading->IosStoreLoading()
            state.paging.initialError!=null->IosStoreError(state.paging.initialError?.message?:"No se han podido cargar las novedades."){controller.retry()}
            state.paging.items.isEmpty()->IosStoreEmpty(if(state.selectedAudience==StoreNovedadesAudience.ALL)"No hay novedades." else "No hay productos con estos filtros.",if(state.selectedAudience==StoreNovedadesAudience.ALL)null else ({controller.selectAudience(StoreNovedadesAudience.ALL)}),if(state.selectedAudience==StoreNovedadesAudience.ALL)null else "Borrar filtros")
            else->LazyVerticalGrid(columns=GridCells.Fixed(2),state=grid,contentPadding=PaddingValues(start=12.dp,top=4.dp,end=12.dp,bottom=28.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                items(state.paging.items,key={it.id}){IosProductCard(it,onProduct,cartStore)}
                if(state.paging.isAppending)item(span={GridItemSpan(maxLineSpan)}){IosStoreLoading()}
                state.paging.appendError?.let{error->item(span={GridItemSpan(maxLineSpan)}){IosStoreError(error.message?:"No se han podido cargar más productos."){controller.loadNextPage(state.queryIdentity)}}}
            }
        }
    }
}
