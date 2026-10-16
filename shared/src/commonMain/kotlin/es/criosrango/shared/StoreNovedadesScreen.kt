package es.criosrango.shared

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import es.criosrango.shared.api.StoreApiClient
import es.criosrango.shared.model.StoreProduct

internal fun shouldShowNovedadesInitialLoading(state: StoreNovedadesState): Boolean =
    state.paging.isInitialLoading && state.paging.items.isEmpty()

internal fun shouldShowNovedadesCountAndSort(state: StoreNovedadesState): Boolean =
    !shouldShowNovedadesInitialLoading(state)

@Composable
fun StoreNovedadesScreen(storeApi:StoreApiClient,padding:PaddingValues,cartStore:StoreCartStore?=null,onProduct:(StoreProduct)->Unit,onBack:()->Unit) {
    val scope=rememberCoroutineScope()
    val controller=remember(storeApi){StoreNovedadesController(scope,StoreApiNovedadesDataSource(storeApi))}
    val state by controller.state.collectAsState()
    var sortExpanded by remember{mutableStateOf(false)}
    LaunchedEffect(controller){controller.start()}
    Column(Modifier.fillMaxSize().padding(padding)) {
        StoreCatalogHeader(
            title = "Novedades",
            subtitle = "Últimas incorporaciones",
            titleFontWeight = FontWeight.Bold,
            showBack = true,
            onBack = onBack,
        )
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=12.dp,vertical=4.dp),horizontalArrangement=Arrangement.spacedBy(7.dp)) {
            StoreNovedadesAudience.values().forEach { option ->
                FilterChip(selected=state.selectedAudience==option,onClick={controller.selectAudience(option)},label={Text(option.label)},
                    colors=FilterChipDefaults.filterChipColors(selectedContainerColor=Color(0xFF183B35),selectedLabelColor=Color(0xFFFFFFFF),selectedLeadingIconColor=Color(0xFFFFFFFF),selectedTrailingIconColor=Color(0xFFFFFFFF)))
            }
        }
        if (shouldShowNovedadesCountAndSort(state)) {
            Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=4.dp),verticalAlignment=Alignment.CenterVertically) {
                Text("${state.paging.items.size} productos",modifier=Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Box {
                    TextButton(onClick={sortExpanded=true}){Text("Ordenar: ${state.selectedSort.label} ▾")}
                    DropdownMenu(expanded=sortExpanded,onDismissRequest={sortExpanded=false}) {
                        StoreNovedadesSort.values().forEach { option -> DropdownMenuItem(text={Text(option.label)},onClick={sortExpanded=false;controller.selectSort(option)}) }
                    }
                }
            }
        }
        when {
            shouldShowNovedadesInitialLoading(state) -> StoreProductGridSkeleton(Modifier.weight(1f))
            state.paging.items.isEmpty() && state.paging.initialError!=null -> IosStoreError(state.paging.initialError?.message?:"No se han podido cargar las novedades."){controller.retry()}
            state.paging.items.isEmpty() -> IosStoreEmpty(
                if(state.selectedAudience==StoreNovedadesAudience.ALL) "No hay novedades." else "No hay productos en esta categoría.",
                null,
                null)
            else -> LazyVerticalGrid(columns=GridCells.Fixed(2),contentPadding=PaddingValues(start=12.dp,top=4.dp,end=12.dp,bottom=28.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                items(state.paging.items,key={it.id}){IosProductCard(it,onProduct,cartStore)}
            }
        }
    }
}
