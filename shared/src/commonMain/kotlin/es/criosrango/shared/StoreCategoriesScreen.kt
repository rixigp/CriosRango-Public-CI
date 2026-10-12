package es.criosrango.shared
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import es.criosrango.shared.model.StoreCategory
/** Single root-category presentation owner. Data loading and navigation remain host-owned. */
@Composable
fun StoreCategoriesScreen(categories: List<StoreCategory>, loading: Boolean, errorMessage: String? = null,
    onBack: () -> Unit, onCategory: (StoreCategory) -> Unit, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        StoreCatalogHeader(title = "Categorías", showBack = true, onBack = onBack)
        when {
            errorMessage != null -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(errorMessage); TextButton(onClick = onRetry) { Text("Reintentar") } }
            }
            categories.none { it.parent == 0 && !it.name.equals("Outlet", true) && !it.slug.equals("outlet", true) } && loading ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            categories.none { it.parent == 0 && !it.name.equals("Outlet", true) && !it.slug.equals("outlet", true) } ->
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("No hay categorías."); TextButton(onClick = onRetry) { Text("Reintentar") } }
                }
            else -> Column(Modifier.fillMaxSize()) {
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                StoreCategoriesGrid(
                    categories = categories,
                    onCategory = onCategory,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 0.dp),
                    columnSpacing = 12.dp,
                    rowSpacing = 12.dp,
                    imagePadding = 1.dp,
                    imageToLabelSpacing = 6.dp
                )
            }
        }
    }
}
