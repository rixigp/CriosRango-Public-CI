package es.criosrango.shared

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Compatibility adapter for screens outside the page-header migration scope. */
@Composable
fun StoreCatalogHeader(
    title: String,
    showBack: Boolean = false,
    onBack: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
    subtitle: String? = null,
    titleFontWeight: FontWeight = FontWeight.Normal,
    horizontalPadding: androidx.compose.ui.unit.Dp = 20.dp
) {
    StorePageHeader(
        title = title,
        onBack = if (showBack) onBack else null,
        trailing = trailing,
        subtitle = subtitle,
        titleFontWeight = titleFontWeight,
        horizontalPadding = horizontalPadding
    )
}
