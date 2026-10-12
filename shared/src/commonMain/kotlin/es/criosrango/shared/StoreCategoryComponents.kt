package es.criosrango.shared

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import es.criosrango.shared.generated.resources.Res
import es.criosrango.shared.generated.resources.category_bautizo
import es.criosrango.shared.generated.resources.category_bebe_nina
import es.criosrango.shared.generated.resources.category_bebe_nino
import es.criosrango.shared.generated.resources.category_calzado
import es.criosrango.shared.generated.resources.category_comunion_nina
import es.criosrango.shared.generated.resources.category_comunion_nino
import es.criosrango.shared.generated.resources.category_hombre
import es.criosrango.shared.generated.resources.category_mujer
import es.criosrango.shared.generated.resources.category_nina
import es.criosrango.shared.generated.resources.category_nino
import es.criosrango.shared.generated.resources.category_recien_nacido
import es.criosrango.shared.model.StoreCategory
import org.jetbrains.compose.resources.painterResource

internal fun normalizedRootCategoryName(value: String): String =
    value.lowercase().trim()
        .replace('á', 'a').replace('é', 'e').replace('í', 'i')
        .replace('ó', 'o').replace('ú', 'u').replace('ü', 'u')
        .replace('ñ', 'n')

/** Canonical shared display name used by the root card and catalog page header. */
internal fun storeCategoryDisplayName(category: StoreCategory): String =
    when (normalizedRootCategoryName(category.name)) {
        "bebe nina" -> "Bebé niña"
        "bebe nino" -> "Bebé niño"
        else -> category.name
    }

private fun rootCategoryResource(category: StoreCategory) = when {
    normalizedRootCategoryName(category.name).contains("bautizo") -> Res.drawable.category_bautizo
    normalizedRootCategoryName(category.name).contains("bebe") &&
        normalizedRootCategoryName(category.name).contains("nina") -> Res.drawable.category_bebe_nina
    normalizedRootCategoryName(category.name).contains("bebe") &&
        normalizedRootCategoryName(category.name).contains("nino") -> Res.drawable.category_bebe_nino
    normalizedRootCategoryName(category.name).contains("calzado") -> Res.drawable.category_calzado
    normalizedRootCategoryName(category.name).contains("comunion") &&
        normalizedRootCategoryName(category.name).contains("nina") -> Res.drawable.category_comunion_nina
    normalizedRootCategoryName(category.name).contains("comunion") &&
        normalizedRootCategoryName(category.name).contains("nino") -> Res.drawable.category_comunion_nino
    normalizedRootCategoryName(category.name).contains("recien") -> Res.drawable.category_recien_nacido
    normalizedRootCategoryName(category.name) == "hombre" -> Res.drawable.category_hombre
    normalizedRootCategoryName(category.name) == "mujer" -> Res.drawable.category_mujer
    normalizedRootCategoryName(category.name) == "nina" -> Res.drawable.category_nina
    normalizedRootCategoryName(category.name) == "nino" -> Res.drawable.category_nino
    else -> null
}

/** Shared root-category card. Android and iOS render this exact composable. */
@Composable
fun StoreCategoryCard(
    category: StoreCategory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    imagePadding: Dp = 6.dp,
    imageToLabelSpacing: Dp = 8.dp,
    imageScale: Float = 1f
) {
    val image = rootCategoryResource(category)
    Column(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            contentAlignment = Alignment.Center
        ) {
            if (image != null) {
                Image(
                    painter = painterResource(image),
                    contentDescription = category.name,
                    modifier = Modifier.fillMaxSize().padding(imagePadding).scale(imageScale),
                    contentScale = ContentScale.Fit
                )
            } else {
                Text(
                    text = category.name.take(1),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
        Spacer(Modifier.size(width = 0.dp, height = imageToLabelSpacing))
        Text(
            text = storeCategoryDisplayName(category),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Shared three-column root-category grid. Navigation remains callback-driven on both hosts. */
@Composable
fun StoreCategoriesGrid(
    categories: List<StoreCategory>,
    onCategory: (StoreCategory) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 0.dp),
    columnSpacing: Dp = 12.dp,
    rowSpacing: Dp = 16.dp,
    imagePadding: Dp = 6.dp,
    imageToLabelSpacing: Dp = 8.dp
) {
    val visible = categories.filter {
        it.parent == 0 && !it.name.equals("Outlet", ignoreCase = true) &&
            !it.slug.equals("outlet", ignoreCase = true)
    }.distinctBy { it.id }
    val rows = visible.chunked(3)
    LazyColumn(
        modifier = modifier,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        items(rows.size) { rowIndex ->
            val row = rows[rowIndex]
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = if (rowIndex == 0) 0.dp else rowSpacing),
                horizontalArrangement = Arrangement.spacedBy(columnSpacing)
            ) {
                repeat(3) { index ->
                    Box(modifier = Modifier.weight(1f)) {
                        if (index < row.size) {
                            StoreCategoryCard(
                                category = row[index],
                                onClick = { onCategory(row[index]) },
                                imagePadding = imagePadding,
                                imageToLabelSpacing = imageToLabelSpacing
                            )
                        }
                    }
                }
            }
        }
    }
}
