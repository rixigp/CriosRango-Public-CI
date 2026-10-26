package es.criosrango.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Home rendering and Home business data are shared; this enum is Outlet platform navigation state. */
internal enum class HomeOutletSeason {
    WINTER,
    SUMMER
}

internal fun outletSeasonKey(value: String): String =
    java.text.Normalizer
        .normalize(value.lowercase().trim(), java.text.Normalizer.Form.NFD)
        .replace("\\p{Mn}+".toRegex(), "")

internal fun categoryImageRes(category: ProductCategory): Int? {
    val key = java.text.Normalizer
        .normalize(category.name.lowercase(), java.text.Normalizer.Form.NFD)
        .replace("\\p{Mn}+".toRegex(), "")

    return when {
        key.contains("bautizo") -> R.drawable.category_bautizo
        key.contains("bebe") && key.contains("nina") -> R.drawable.category_bebe_nina
        key.contains("bebe") && key.contains("nino") -> R.drawable.category_bebe_nino
        key.contains("calzado") -> R.drawable.category_calzado
        key.contains("comunion") && key.contains("nina") -> R.drawable.category_comunion_nina
        key.contains("comunion") && key.contains("nino") -> R.drawable.category_comunion_nino
        key.contains("recien") -> R.drawable.category_recien_nacido
        key.trim() == "hombre" -> R.drawable.category_hombre
        key.trim() == "mujer" -> R.drawable.category_mujer
        key.trim() == "nina" -> R.drawable.category_nina
        key.trim() == "nino" -> R.drawable.category_nino
        else -> null
    }
}

internal fun approvedCategoryName(category: ProductCategory): String =
    when (category.name.lowercase().trim()) {
        "bebe niña", "bebé niña" -> "Bebé niña"
        "bebe niño", "bebé niño" -> "Bebé niño"
        else -> category.name
    }

/** Retained for the existing Android category-grid visual; Home itself uses StoreHomeScreen. */
@Composable
internal fun ApprovedCategoryCell(
    category: ProductCategory,
    onClick: () -> Unit,
    imageScale: Float = CatalogCategoryVisualTokens.imageScale,
    imageResOverride: Int? = null
) {
    val imageRes = imageResOverride ?: categoryImageRes(category)
    Column(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            contentAlignment = Alignment.Center
        ) {
            if (imageRes != null) {
                Image(
                    painter = painterResource(imageRes),
                    contentDescription = approvedCategoryName(category),
                    modifier = Modifier.fillMaxSize()
                        .padding(CatalogCategoryVisualTokens.imagePadding)
                        .scale(imageScale),
                    contentScale = ContentScale.Fit
                )
            } else {
                CategoryVisual(category, Modifier.fillMaxSize().padding(6.dp))
            }
        }
        Spacer(Modifier.height(CatalogCategoryVisualTokens.imageToLabelSpacing))
        Text(
            text = approvedCategoryName(category),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}
