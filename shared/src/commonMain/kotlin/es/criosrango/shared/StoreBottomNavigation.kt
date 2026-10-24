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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category as FilledCategory
import androidx.compose.material.icons.filled.Home as FilledHome
import androidx.compose.material.icons.filled.LocalOffer as FilledLocalOffer
import androidx.compose.material.icons.filled.Person as FilledPerson
import androidx.compose.material.icons.filled.ShoppingBag as FilledShoppingBag
import androidx.compose.material.icons.outlined.Category as OutlinedCategory
import androidx.compose.material.icons.outlined.Home as OutlinedHome
import androidx.compose.material.icons.outlined.LocalOffer as OutlinedLocalOffer
import androidx.compose.material.icons.outlined.Person as OutlinedPerson
import androidx.compose.material.icons.outlined.ShoppingBag as OutlinedShoppingBag
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.navigationBarsPadding

enum class StoreMainTab { HOME, CATEGORIES, OUTLET, CART, ACCOUNT }

/** Shared visual owner reproducing the approved Android bottom-navigation baseline. */
@Composable
fun StoreBottomNavigation(
    selectedTab: StoreMainTab,
    cartQuantity: Int,
    onTabSelected: (StoreMainTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = Modifier.fillMaxWidth().then(modifier),
        color = MaterialTheme.colorScheme.background,
        shadowElevation = 3.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp),
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
                val tint = if (selected) Color.Black else Color(0xFF777277)
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
                                StoreMainTab.HOME -> if (selected) Icons.Default.FilledHome else Icons.Outlined.OutlinedHome
                                StoreMainTab.CATEGORIES -> if (selected) Icons.Default.FilledCategory else Icons.Outlined.OutlinedCategory
                                StoreMainTab.OUTLET -> if (selected) Icons.Default.FilledLocalOffer else Icons.Outlined.OutlinedLocalOffer
                                StoreMainTab.CART -> if (selected) Icons.Default.FilledShoppingBag else Icons.Outlined.OutlinedShoppingBag
                                StoreMainTab.ACCOUNT -> if (selected) Icons.Default.FilledPerson else Icons.Outlined.OutlinedPerson
                            },
                            contentDescription = label,
                            tint = tint,
                            modifier = Modifier.size(24.dp)
                        )
                        if (tab == StoreMainTab.CART && cartQuantity > 0) {
                            Badge(
                                modifier = Modifier.align(Alignment.TopEnd)
                                    .offset(x = 5.dp, y = (-4).dp)
                            ) {
                                Text(cartBadgeText(cartQuantity), maxLines = 1)
                            }
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = tint,
                        maxLines = 1,
                        overflow = TextOverflow.Clip
                    )
                }
                }
            }
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}
