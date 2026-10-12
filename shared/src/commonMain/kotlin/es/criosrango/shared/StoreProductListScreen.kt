package es.criosrango.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StoreProductListScreen(
    state: StoreProductListUiState,
    gridState: LazyGridState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onProductSelected: (Int) -> Unit,
    onSortSelected: (StoreProductSortOption) -> Unit,
    onFilterClick: () -> Unit,
    onRemoveFilter: (String) -> Unit,
    onClearFilters: () -> Unit,
    onRetryInitial: () -> Unit,
    onRetryAppend: () -> Unit,
    imageContent: @Composable BoxWithConstraintsScope.(StoreProductItemUi, Modifier) -> Unit
) {
    Column(modifier) {
        StoreCatalogHeader(
            title = state.title,
            showBack = true,
            onBack = onBack,
            trailing = {
                OutlinedButton(
                    onClick = onFilterClick,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(if (state.activeFilterCount == 0) "Filtros" else "Filtros (${state.activeFilterCount})")
                }
            }
        )

        if (state.activeFilterChips.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                state.activeFilterChips.forEach { filter ->
                    FilterChip(
                        selected = true,
                        onClick = { onRemoveFilter(filter.id) },
                        label = { Text(filter.label) },
                        trailingIcon = {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Eliminar ${filter.label}",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
                TextButton(
                    onClick = onClearFilters,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.heightIn(min = 32.dp)
                ) {
                    Text("Borrar filtros")
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProductListSortControl(
                selected = state.selectedSort,
                options = state.availableSortOptions,
                onSelected = onSortSelected
            )
        }

        when {
            state.isInitialLoading -> StoreProductGridSkeleton(Modifier.weight(1f))
            state.initialErrorMessage != null -> Box(
                Modifier.fillMaxWidth().weight(1f).padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.initialErrorMessage, color = Color.Gray)
                    TextButton(onClick = onRetryInitial) { Text("Reintentar") }
                }
            }
            state.isEmpty -> Box(
                Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(state.emptyMessage, color = Color.Gray)
            }
            else -> StoreProductGrid(
                state = state,
                gridState = gridState,
                modifier = Modifier.weight(1f),
                onProductSelected = onProductSelected,
                onRetryAppend = onRetryAppend,
                imageContent = imageContent
            )
        }
    }
}

@Composable
private fun ProductListSortControl(
    selected: StoreProductSortOption,
    options: List<StoreProductSortOption>,
    onSelected: (StoreProductSortOption) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Ordenar por", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
        Box {
            TextButton(onClick = { expanded = true }) {
                Text(text = "${selected.label}  ▾", color = MaterialTheme.colorScheme.onBackground)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = { expanded = false; onSelected(option) }
                    )
                }
            }
        }
    }
}

@Composable
fun StoreProductGrid(
    state: StoreProductListUiState,
    gridState: LazyGridState,
    modifier: Modifier = Modifier,
    onProductSelected: (Int) -> Unit,
    onRetryAppend: () -> Unit,
    imageContent: @Composable BoxWithConstraintsScope.(StoreProductItemUi, Modifier) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier,
        state = gridState,
        contentPadding = PaddingValues(start = 20.dp, top = 4.dp, end = 20.dp, bottom = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(state.items, key = { it.id }) { item ->
            StoreProductCard(
                item = item,
                onClick = { onProductSelected(item.id) },
                imageContent = imageContent
            )
        }
        if (state.isLoadingMore) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(Modifier.size(22.dp))
                }
            }
        } else if (state.appendErrorMessage != null && state.canLoadMore) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (state.appendErrorMessage.isNotBlank()) {
                        Text(state.appendErrorMessage, color = Color.Gray)
                    }
                    TextButton(onClick = onRetryAppend) { Text(state.appendRetryLabel) }
                }
            }
        }
    }
}

@Composable
fun StoreProductCard(
    item: StoreProductItemUi,
    onClick: () -> Unit,
    imageContent: @Composable BoxWithConstraintsScope.(StoreProductItemUi, Modifier) -> Unit
) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(.78f)) {
            imageContent(item, Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)))
            if (item.isOnSale) {
                Text(
                    "OFERTA",
                    Modifier.padding(8.dp)
                        .background(Color(0xFFD18162), RoundedCornerShape(6.dp))
                        .padding(horizontal = 7.dp, vertical = 4.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.TopStart) {
            Text(
                item.nameText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold
            )
        }
        if (item.showPrice) {
            Row(Modifier.fillMaxWidth().height(24.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(item.displayPriceText, color = Color(0xFF183B35), fontWeight = FontWeight.Bold)
                if (item.isOnSale) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        item.regularPriceText,
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall,
                        textDecoration = TextDecoration.LineThrough
                    )
                }
            }
        }
    }
}

@Composable
private fun StoreProductGridSkeleton(modifier: Modifier = Modifier) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier,
        contentPadding = PaddingValues(start = 20.dp, top = 4.dp, end = 20.dp, bottom = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(6) {
            Column(Modifier.fillMaxWidth()) {
                Box(
                    Modifier.fillMaxWidth().aspectRatio(.78f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFFE8E5DF))
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier.fillMaxWidth(.82f).height(14.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFE8E5DF))
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier.fillMaxWidth(.58f).height(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFE8E5DF))
                )
            }
        }
    }
}
