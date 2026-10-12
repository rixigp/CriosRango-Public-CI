package es.criosrango.shared
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
/** The single filter-dialog presentation for the normal catalog on Android and iOS. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StoreCatalogFilterDialog(
    sizes: List<String>, colors: List<String>, brands: List<String>,
    selectedSizes: Set<String>, selectedColors: Set<String>, selectedBrands: Set<String>,
    enabledSizes: Set<String>, enabledColors: Set<String>, enabledBrands: Set<String>,
    showBrands: Boolean = true,
    onSizesChanged: (Set<String>) -> Unit, onColorsChanged: (Set<String>) -> Unit,
    onBrandsChanged: (Set<String>) -> Unit, onApply: () -> Unit,
    onClear: () -> Unit, onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filtros") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 2.dp)) {
                StoreCatalogFilterChoices("Talla", sizes, selectedSizes, enabledSizes, onSizesChanged)
                Spacer(Modifier.height(20.dp))
                StoreCatalogFilterChoices("Color", colors, selectedColors, enabledColors, onColorsChanged)
                if (showBrands && brands.isNotEmpty()) {
                    Spacer(Modifier.height(20.dp))
                    StoreCatalogFilterChoices("Marca", brands, selectedBrands, enabledBrands, onBrandsChanged)
                }
            }
        },
        confirmButton = { TextButton(onClick = onApply) { Text("Aplicar") } },
        dismissButton = { TextButton(onClick = onClear) { Text("Limpiar") } }
    )
}
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StoreCatalogFilterChoices(title: String, values: List<String>, selected: Set<String>, enabledValues: Set<String>, onChange: (Set<String>) -> Unit) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    val orderedValues = if (title.equals("Talla", ignoreCase = true) || title.equals("Tallas", ignoreCase = true)) {
        values.sortedWith(compareBy({ storeCatalogFilterSizeSortKey(it) }, { it }))
    } else values
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        orderedValues.forEach { value ->
            FilterChip(selected = value in selected, enabled = value in selected || value in enabledValues,
                onClick = { onChange(if (value in selected) selected - value else selected + value) },
                leadingIcon = {
                    if (title.equals("Color", ignoreCase = true)) {
                        productColorSwatchMapping(value)?.let { swatch ->
                            Box(Modifier.size(18.dp).background(swatch, RoundedCornerShape(50)))
                        }
                    }
                },
                label = { Text(value) })
        }
    }
}

/** Mirrors the established Android catalog size-option ordering without changing its rules. */
internal fun storeCatalogFilterSizeSortKey(value: String): Int {
    val clean = value.uppercase().trim().replace(" ", "")
    return when {
        clean.endsWith("M") -> clean.removeSuffix("M").toIntOrNull() ?: 999
        clean.endsWith("A") -> (clean.removeSuffix("A").toIntOrNull() ?: 99) * 12
        clean == "XXS" -> 1000
        clean == "XS" -> 1001
        clean == "S" -> 1002
        clean == "M" -> 1003
        clean == "L" -> 1004
        clean == "XL" -> 1005
        clean == "XXL" -> 1006
        else -> 2000
    }
}
