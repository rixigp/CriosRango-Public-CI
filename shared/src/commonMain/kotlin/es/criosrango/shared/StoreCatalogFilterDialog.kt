package es.criosrango.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private val FILTER_SELECTED_CONTAINER = androidx.compose.ui.graphics.Color(0xFF183B35)
private val FILTER_SELECTED_CONTENT = androidx.compose.ui.graphics.Color.White

/** The single filter-dialog presentation for the normal catalog on Android and iOS. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StoreCatalogFilterDialog(
    sizes: List<String>, colors: List<String>, brands: List<String>,
    selectedSizes: Set<String>, selectedColors: Set<String>, selectedBrands: Set<String>,
    enabledSizes: Set<String>, enabledColors: Set<String>, enabledBrands: Set<String>,
    showBrands: Boolean = true,
    onDimensionSelected: (String) -> Unit = {},
    onSizesChanged: (Set<String>) -> Unit, onColorsChanged: (Set<String>) -> Unit,
    onBrandsChanged: (Set<String>) -> Unit, onApply: () -> Unit,
    onClear: () -> Unit, onDismiss: () -> Unit, onCancel: (() -> Unit)? = null
) {
    var activeDimension by remember { mutableStateOf("Talla") }
    var brandQuery by remember { mutableStateOf("") }
    val showBrandTab = showBrands
    val activeTab = if (activeDimension == "Marca" && !showBrandTab) "Talla" else activeDimension
    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = FILTER_SELECTED_CONTAINER,
        selectedLabelColor = FILTER_SELECTED_CONTENT,
        selectedLeadingIconColor = FILTER_SELECTED_CONTENT,
        selectedTrailingIconColor = FILTER_SELECTED_CONTENT
    )
    val sizeLabels = distinctFilterMetadataDisplayNames(sizes)
        .sortedWith(compareBy({ storeCatalogFilterSizeSortKey(it) }, { it }))
    val colorLabels = distinctFilterMetadataDisplayNames(colors)
        .sortedWith(compareBy({ storeCatalogFilterKey(it) }, { it }))
    val brandLabels = filterBrandDisplayNames(brands, brandQuery)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filtros") },
        text = {
            Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = activeTab == "Talla",
                        onClick = { activeDimension = "Talla"; onDimensionSelected("sizes") },
                        modifier = Modifier.weight(1f),
                        colors = chipColors,
                        label = {
                            Text(
                                dimensionLabel("Talla", selectedSizes.size),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                    FilterChip(
                        selected = activeTab == "Color",
                        onClick = { activeDimension = "Color"; onDimensionSelected("colors") },
                        modifier = Modifier.weight(1f),
                        colors = chipColors,
                        label = {
                            Text(
                                dimensionLabel("Color", selectedColors.size),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                    if (showBrandTab) {
                        FilterChip(
                            selected = activeTab == "Marca",
                            onClick = { activeDimension = "Marca"; onDimensionSelected("brands") },
                            modifier = Modifier.weight(1f),
                            colors = chipColors,
                            label = {
                                Text(
                                    dimensionLabel("Marca", selectedBrands.size),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 2.dp)
                    ) {
                        when (activeTab) {
                            "Talla" -> StoreCatalogFilterChoices(
                                title = "Talla",
                                values = sizeLabels,
                                selected = selectedSizes,
                                enabledValues = enabledSizes,
                                onChange = onSizesChanged,
                                chipColors = chipColors
                            )
                            "Color" -> StoreCatalogFilterChoices(
                                title = "Color",
                                values = colorLabels,
                                selected = selectedColors,
                                enabledValues = enabledColors,
                                onChange = onColorsChanged,
                                chipColors = chipColors
                            )
                            else -> {
                                OutlinedTextField(
                                    value = brandQuery,
                                    onValueChange = { brandQuery = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = { Text("Buscar marca...") },
                                    singleLine = true
                                )
                                Spacer(Modifier.height(8.dp))
                                StoreCatalogFilterChoices(
                                    title = "Marca",
                                    values = brandLabels,
                                    selected = selectedBrands,
                                    enabledValues = enabledBrands,
                                    onChange = onBrandsChanged,
                                    chipColors = chipColors
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onApply, colors = ButtonDefaults.textButtonColors(contentColor = FILTER_SELECTED_CONTAINER)) { Text("Aplicar") } },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onClear, colors = ButtonDefaults.textButtonColors(contentColor = FILTER_SELECTED_CONTAINER)) { Text("Limpiar") }
                if (onCancel != null) TextButton(onClick = onCancel, colors = ButtonDefaults.textButtonColors(contentColor = FILTER_SELECTED_CONTAINER)) { Text("Cancelar") }
            }
        }
    )
}

private fun dimensionLabel(title: String, selectedCount: Int): String =
    if (selectedCount > 0) "$title ($selectedCount)" else title

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StoreCatalogFilterChoices(
    title: String,
    values: List<String>,
    selected: Set<String>,
    enabledValues: Set<String>,
    onChange: (Set<String>) -> Unit,
    chipColors: androidx.compose.material3.SelectableChipColors
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        values.forEach { value ->
            FilterChip(
                selected = value in selected,
                enabled = value in selected || value in enabledValues,
                onClick = { onChange(if (value in selected) selected - value else selected + value) },
                colors = chipColors,
                leadingIcon = {
                    if (title.equals("Color", ignoreCase = true)) {
                        productColorSwatchMapping(value)?.let { swatch ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .background(swatch, RoundedCornerShape(50))
                                )
                                if (value in selected) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = FILTER_SELECTED_CONTENT
                                    )
                                }
                            }
                        }
                    } else if (value in selected) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                },
                label = { Text(value) }
            )
        }
    }
}

/** Accent-insensitive matching used by the legacy Outlet filters on Android. */
fun outletCatalogFilterKey(value: String): String {
    val unaccented = buildString {
        value.lowercase().trim().forEach { character ->
            when (character) {
                'à', 'á', 'â', 'ã', 'ä', 'å', 'ā', 'ă', 'ą' -> append('a')
                'ç', 'ć', 'ĉ', 'ċ', 'č' -> append('c')
                'ď' -> append('d')
                'è', 'é', 'ê', 'ë', 'ē', 'ĕ', 'ė', 'ę', 'ě' -> append('e')
                'ĝ', 'ğ', 'ġ', 'ģ' -> append('g')
                'ĥ' -> append('h')
                'ì', 'í', 'î', 'ï', 'ĩ', 'ī', 'ĭ', 'į' -> append('i')
                'ĵ' -> append('j')
                'ķ' -> append('k')
                'ĺ', 'ļ', 'ľ' -> append('l')
                'ñ', 'ń', 'ņ', 'ň' -> append('n')
                'ò', 'ó', 'ô', 'õ', 'ö', 'ō', 'ŏ', 'ő' -> append('o')
                'ŕ', 'ŗ', 'ř' -> append('r')
                'ś', 'ŝ', 'ş', 'š' -> append('s')
                'ţ', 'ť' -> append('t')
                'ù', 'ú', 'û', 'ü', 'ũ', 'ū', 'ŭ', 'ů', 'ű', 'ų' -> append('u')
                'ŵ' -> append('w')
                'ý', 'ÿ', 'ŷ' -> append('y')
                'ź', 'ż', 'ž' -> append('z')
                in '\u0300'..'\u036F' -> Unit
                else -> append(character)
            }
        }
    }
    return unaccented.replace(Regex("\\s+"), " ")
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
