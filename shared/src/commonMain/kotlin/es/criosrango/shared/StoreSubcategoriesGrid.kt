package es.criosrango.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import es.criosrango.shared.model.StoreCategory

/** Shared three-column subcategory grid; navigation remains callback-driven. */
@Composable
fun StoreSubcategoriesGrid(
    parent: StoreCategory,
    categories: List<StoreCategory>,
    onCategory: (StoreCategory) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 28.dp),
    columnSpacing: Dp = 12.dp,
    rowSpacing: Dp = 12.dp,
    imagePadding: Dp = 1.dp,
    imageScale: Float = 1.16f,
    imageToLabelSpacing: Dp = 6.dp
) {
    val rows = categories.distinctBy { it.id }.chunked(3)
    LazyColumn(modifier = modifier, contentPadding = contentPadding, verticalArrangement = Arrangement.spacedBy(0.dp)) {
        items(rows.size) { rowIndex ->
            val row = rows[rowIndex]
            Row(modifier = Modifier.fillMaxWidth().padding(top = if (rowIndex == 0) 0.dp else rowSpacing),
                horizontalArrangement = Arrangement.spacedBy(columnSpacing)) {
                repeat(3) { index ->
                    Box(modifier = Modifier.weight(1f)) {
                        if (index < row.size) {
                            StoreSubcategoryCard(parent, row[index], onClick = { onCategory(row[index]) },
                                imagePadding = imagePadding, imageScale = imageScale, imageToLabelSpacing = imageToLabelSpacing)
                        }
                    }
                }
            }
        }
    }
}
