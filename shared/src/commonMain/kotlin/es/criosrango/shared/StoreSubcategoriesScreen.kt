package es.criosrango.shared
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import es.criosrango.shared.model.StoreCategory
/** Single normal-subcategory presentation owner; filters and routing are supplied by the host adapter. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun StoreSubcategoriesScreen(parent: StoreCategory, title: String = storeCategoryDisplayName(parent), categories: List<StoreCategory>, loading: Boolean,
    errorMessage: String? = null, activeFilterCount: Int = 0, activeFilterChips: List<StoreActiveFilterChipUi> = emptyList(),
    onBack: () -> Unit, onCategory: (StoreCategory) -> Unit, onFilterClick: () -> Unit,
    onRemoveFilter: (String) -> Unit = {}, onClearFilters: () -> Unit = {}, onRetry: () -> Unit = {}, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        StoreCatalogHeader(title = title, showBack = true, onBack = onBack,
            trailing = { OutlinedButton(onClick = onFilterClick, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                Text(if (activeFilterCount == 0) "Filtros" else "Filtros ($activeFilterCount)")
            } })
        if (activeFilterChips.isNotEmpty()) {
            FlowRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                activeFilterChips.forEach { filter -> FilterChip(selected = true, onClick = { onRemoveFilter(filter.id) }, label = { Text(filter.label) }) }
                TextButton(onClick = onClearFilters) { Text("Borrar filtros") }
            }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        when {
            errorMessage != null -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) { Text(errorMessage); TextButton(onClick = onRetry) { Text("Reintentar") } }
            }
            categories.isEmpty() && loading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                CircularProgressIndicator()
            }
            categories.isEmpty() && !loading -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) { Text("No hay subcategorías."); TextButton(onClick = onRetry) { Text("Reintentar") } }
            }
            else -> StoreSubcategoriesGrid(parent = parent, categories = categories, onCategory = onCategory, modifier = Modifier.weight(1f).fillMaxWidth())
        }
    }
}
