package es.criosrango.shared

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import es.criosrango.shared.api.StoreApiClient
import es.criosrango.shared.model.StoreProduct

@Composable
fun StoreNovedadesScreen(storeApi:StoreApiClient,padding:PaddingValues,cartStore:StoreCartStore?=null,onProduct:(StoreProduct)->Unit,onBack:()->Unit) {
    val scope=rememberCoroutineScope()
    val controller=remember(storeApi){StoreNovedadesController(scope,StoreApiNovedadesDataSource(storeApi))}
    val state by controller.state.collectAsState()
    var sortExpanded by remember{mutableStateOf(false)}
    LaunchedEffect(controller){controller.start()}
    Column(Modifier.fillMaxSize().padding(padding)) {
        Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=2.dp).padding(bottom=2.dp),verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,contentDescription="Volver")}
            Column(Modifier.weight(1f)) {
                Text("Novedades",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                Text("Últimas incorporaciones",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedButton(onClick=controller::openFilters) { Text(if (state.activeFilterCount == 0) "Filtros" else "Filtros (${state.activeFilterCount})") }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=12.dp,vertical=4.dp),horizontalArrangement=Arrangement.spacedBy(7.dp)) {
            StoreNovedadesAudience.values().forEach { option ->
                FilterChip(selected=state.selectedAudience==option,onClick={controller.selectAudience(option)},label={Text(option.label)},
                    colors=FilterChipDefaults.filterChipColors(selectedContainerColor=Color(0xFF183B35),selectedLabelColor=Color(0xFFFFFFFF),selectedLeadingIconColor=Color(0xFFFFFFFF),selectedTrailingIconColor=Color(0xFFFFFFFF)))
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=4.dp),verticalAlignment=Alignment.CenterVertically) {
            Text("${state.paging.items.size} productos",modifier=Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Box {
                TextButton(onClick={sortExpanded=true}){Text("Ordenar: ${state.selectedSort.label} ▾")}
                DropdownMenu(expanded=sortExpanded,onDismissRequest={sortExpanded=false}) {
                    StoreNovedadesSort.values().forEach { option -> DropdownMenuItem(text={Text(option.label)},onClick={sortExpanded=false;controller.selectSort(option)}) }
                }
            }
        }
        when {
            state.paging.isInitialLoading -> IosStoreLoading()
            state.paging.initialError!=null -> IosStoreError(state.paging.initialError?.message?:"No se han podido cargar las novedades."){controller.retry()}
            state.isFiltering -> IosStoreLoading()
            state.filterError!=null -> IosStoreError(state.filterError?.message?:"No se han podido aplicar los filtros."){controller.retryFilters()}
            state.paging.items.isEmpty() -> IosStoreEmpty(
                if(state.selectedAudience==StoreNovedadesAudience.ALL && state.activeFilterCount==0) "No hay novedades." else "No hay productos con estos filtros.",
                if(state.selectedAudience==StoreNovedadesAudience.ALL && state.activeFilterCount==0) null else ({controller.selectAudience(StoreNovedadesAudience.ALL);controller.clearFilters()}),
                if(state.selectedAudience==StoreNovedadesAudience.ALL && state.activeFilterCount==0) null else "Borrar filtros")
            else -> LazyVerticalGrid(columns=GridCells.Fixed(2),contentPadding=PaddingValues(start=12.dp,top=4.dp,end=12.dp,bottom=28.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                items(state.paging.items,key={it.id}){IosProductCard(it,onProduct,cartStore)}
            }
        }
    }
    if(state.filterDialogVisible) {
        val options=(state.filterOptionsState as? StoreFilterOptionsState.Ready)?.options?:StoreFilterOptions(emptyList(),emptyList(),emptyList())
        val status=when(val o=state.filterOptionsState){StoreFilterOptionsState.Loading->"Cargando filtros…";is StoreFilterOptionsState.Error->o.message;else->null}
        StoreCatalogFilterDialog(
            sizes=options.sizes.map{it.name},colors=options.colors.map{it.name},brands=options.brands.map{it.name},
            selectedSizes=state.draftSizes,selectedColors=state.draftColors,selectedBrands=state.draftBrands,
            enabledSizes=state.enabledSizes,enabledColors=state.enabledColors,enabledBrands=state.enabledBrands,
            showBrands=true,statusMessage=status,onRetry=if(state.filterOptionsState is StoreFilterOptionsState.Error) controller::retryFilterOptions else null,
            onDimensionSelected=controller::selectFilterDimension,onSizesChanged=controller::changeDraftSizes,onColorsChanged=controller::changeDraftColors,onBrandsChanged=controller::changeDraftBrands,
            onApply=controller::applyFilters,onClear=controller::clearDraftFilters,onDismiss=controller::cancelFilters,onCancel=controller::cancelFilters)
    }
}
