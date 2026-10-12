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
    val descriptionText: String,
    val brandText: String? = null,
    val compositionText: String? = null
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
