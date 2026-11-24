package es.criosrango.shared

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import es.criosrango.shared.model.ProductBrand
import es.criosrango.shared.model.StoreGlobalBrand
import es.criosrango.shared.model.StoreProduct

enum class StoreProductDetailOptionKind { Default, Size, Color }

data class StoreProductDetailUiState(
    val title: String,
    val displayPrice: String,
    val regularPrice: String,
    val showRegularPrice: Boolean,
    val isOnSale: Boolean,
    val attributeGroups: List<StoreProductDetailAttributeGroupUi>,
    val quantity: Int,
    val quantityMinusEnabled: Boolean,
    val quantityPlusEnabled: Boolean,
    val primaryActionLabel: String,
    val primaryActionEnabled: Boolean,
    val shareEnabled: Boolean = true,
    val reserveEnabled: Boolean = false,
    val longDescriptionText: String,
    val brandText: String? = null,
    val brandLogoUrl: String? = null
)

data class StoreProductDetailAttributeGroupUi(
    val label: String,
    val options: List<StoreProductDetailOptionUi>
)

data class StoreProductDetailOptionUi(
    val value: String,
    val selected: Boolean,
    val enabled: Boolean,
    val incompatible: Boolean = false,
    val strikeThrough: Boolean = false,
    val kind: StoreProductDetailOptionKind = StoreProductDetailOptionKind.Default,
    val swatchColor: Color? = null
)


data class StoreProductBrandUi(val name: String, val logoUrl: String?)

fun resolveStoreProductBrand(
    productBrands: List<ProductBrand>,
    brands: List<StoreGlobalBrand>
): StoreProductBrandUi? {
    val productBrand = productBrands.firstOrNull { it.name.isNotBlank() } ?: return null
    val resolved = brands.firstOrNull { brand ->
        (productBrand.id > 0 && brand.id == productBrand.id) ||
            (productBrand.slug.isNotBlank() && brand.slug.equals(productBrand.slug, ignoreCase = true)) ||
            brand.name.equals(productBrand.name, ignoreCase = true)
    }
    val name = resolved?.name?.takeIf { it.isNotBlank() } ?: productBrand.name.trim()
    return StoreProductBrandUi(name, resolved?.imageUrl?.takeIf { it.isNotBlank() })
}

fun storeProductLongDescription(product: StoreProduct): String = product.description

fun shouldRenderStoreProductDescription(longDescription: String): Boolean =
    longDescription.any { !it.isWhitespace() }

data class StoreProductDescriptionAccordionState(val isExpanded: Boolean = false)

fun toggleStoreProductDescription(state: StoreProductDescriptionAccordionState): StoreProductDescriptionAccordionState =
    state.copy(isExpanded = !state.isExpanded)

fun visibleStoreProductDescription(
    longDescription: String,
    state: StoreProductDescriptionAccordionState
): String? = longDescription.takeIf { state.isExpanded && shouldRenderStoreProductDescription(it) }

fun storeProductDisplayTitle(productTitle: String, brandName: String?): String {
    val title = productTitle.trim()
    val brand = brandName?.trim()?.takeIf { it.isNotBlank() } ?: return title
    if (!title.endsWith(brand, ignoreCase = true)) return title
    val prefix = title.dropLast(brand.length).trimEnd()
    val separator = prefix.lastOrNull() ?: return title
    if (separator !in listOf('-', '–', '—', '|')) return title
    return prefix.dropLast(1).trimEnd().ifBlank { title }
}

