package es.criosrango.shared

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

enum class StoreMainTab { HOME, CATEGORIES, OUTLET, CART, ACCOUNT }

/** Shared visual owner for the five main tabs and the cart quantity badge. */
@Composable
fun StoreBottomNavigation(
    selectedTab: StoreMainTab,
    cartQuantity: Int,
    onTabSelected: (StoreMainTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
        shadowElevation = 3.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(68.dp).then(modifier),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(
                StoreMainTab.HOME to "Inicio",
                StoreMainTab.CATEGORIES to "Categorías",
                StoreMainTab.OUTLET to "Outlet",
                StoreMainTab.CART to "Carrito",
                StoreMainTab.ACCOUNT to "Cuenta"
            ).forEach { (tab, label) ->
                val selected = tab == selectedTab
                val tint = if (selected) Color(0xFF183B35) else Color(0xFF777277)
                Column(
                    modifier = Modifier.weight(1f).fillMaxSize()
                        .clickable { onTabSelected(tab) }
                        .semantics { role = Role.Tab; contentDescription = label },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box {
                        Icon(
                            imageVector = when (tab) {
                                StoreMainTab.HOME -> StoreHomeIcon
                                StoreMainTab.CATEGORIES -> StoreCategoriesIcon
                                StoreMainTab.OUTLET -> StoreOutletIcon
                                StoreMainTab.CART -> StoreShoppingBagIcon
                                StoreMainTab.ACCOUNT -> StoreAccountIcon
                            },
                            contentDescription = label,
                            tint = tint,
                            modifier = Modifier.size(22.dp)
                        )
                        if (tab == StoreMainTab.CART && cartQuantity > 0) {
                            Badge(modifier = Modifier.align(Alignment.TopEnd)) {
                                Text(cartBadgeText(cartQuantity), maxLines = 1)
                            }
                        }
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = tint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private fun storeBuildNavigationIcon(name: String, content: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = content
        )
    }.build()

private val StoreShoppingBagIcon = storeBuildNavigationIcon("StoreShoppingBagIcon") {
    moveTo(5f, 8.5f); lineTo(19f, 8.5f); lineTo(20f, 21f); lineTo(4f, 21f); close()
    moveTo(8.5f, 8.5f); lineTo(8.5f, 6.5f)
    curveTo(8.5f, 2.5f, 15.5f, 2.5f, 15.5f, 6.5f)
    lineTo(15.5f, 8.5f)
}
private val StoreHomeIcon = storeBuildNavigationIcon("StoreHomeIcon") {
    moveTo(3f, 10.5f); lineTo(12f, 3f); lineTo(21f, 10.5f)
    moveTo(5.5f, 9f); lineTo(5.5f, 20.5f); lineTo(18.5f, 20.5f); lineTo(18.5f, 9f)
    moveTo(9.5f, 20.5f); lineTo(9.5f, 14.5f); lineTo(14.5f, 14.5f); lineTo(14.5f, 20.5f)
}
private val StoreCategoriesIcon = storeBuildNavigationIcon("StoreCategoriesIcon") {
    moveTo(4f, 4f); lineTo(10f, 4f); lineTo(10f, 10f); lineTo(4f, 10f); close()
    moveTo(14f, 4f); lineTo(20f, 4f); lineTo(20f, 10f); lineTo(14f, 10f); close()
    moveTo(4f, 14f); lineTo(10f, 14f); lineTo(10f, 20f); lineTo(4f, 20f); close()
    moveTo(14f, 14f); lineTo(20f, 14f); lineTo(20f, 20f); lineTo(14f, 20f); close()
}
private val StoreOutletIcon = storeBuildNavigationIcon("StoreOutletIcon") {
    moveTo(3f, 11f); lineTo(11f, 3f); lineTo(21f, 13f); lineTo(13f, 21f); close()
    moveTo(8.5f, 8.5f); lineTo(8.5f, 8.5f)
    moveTo(12f, 7f); lineTo(10f, 11f); lineTo(14f, 11f); lineTo(12f, 15f)
}
private val StoreAccountIcon = storeBuildNavigationIcon("StoreAccountIcon") {
    moveTo(12f, 12f); curveTo(14.2f, 12f, 16f, 10.2f, 16f, 8f)
    curveTo(16f, 5.8f, 14.2f, 4f, 12f, 4f)
    curveTo(9.8f, 4f, 8f, 5.8f, 8f, 8f)
    curveTo(8f, 10.2f, 9.8f, 12f, 12f, 12f)
    moveTo(4f, 20f); curveTo(4f, 16.7f, 7.6f, 14.5f, 12f, 14.5f)
    curveTo(16.4f, 14.5f, 20f, 16.7f, 20f, 20f)
}
