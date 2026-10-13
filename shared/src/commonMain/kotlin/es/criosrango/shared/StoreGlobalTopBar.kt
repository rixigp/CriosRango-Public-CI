package es.criosrango.shared

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import es.criosrango.shared.generated.resources.Res
import es.criosrango.shared.generated.resources.home_logo
import org.jetbrains.compose.resources.painterResource

/**
 * Shared visual owner for the app-shell top bar.
 * Navigation remains host-owned through the callbacks supplied by each platform.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreGlobalTopBar(
    cartQuantity: Int,
    onSearch: () -> Unit,
    onCart: () -> Unit
) = TopAppBar(
    title = {},
    navigationIcon = {
        Image(
            painter = painterResource(Res.drawable.home_logo),
            contentDescription = "Críos & Rango",
            modifier = Modifier.size(34.dp),
            contentScale = ContentScale.Fit
        )
    },
    actions = {
        IconButton(onClick = onSearch) {
            Icon(Icons.Outlined.Search, contentDescription = "Buscar")
        }
        Box(
            modifier = Modifier
                .width(56.dp)
                .fillMaxHeight()
        ) {
            BadgedBox(
                modifier = Modifier
                    .size(48.dp)
                    .align(Alignment.CenterStart),
                badge = {
                    if (cartQuantity > 0) {
                        Badge { Text(cartBadgeText(cartQuantity), maxLines = 1) }
                    }
                }
            ) {
                IconButton(onClick = onCart) {
                    Icon(Icons.Outlined.ShoppingBag, contentDescription = "Carrito")
                }
            }
        }
    },
    colors = TopAppBarDefaults.topAppBarColors(
        containerColor = Color.Transparent,
        scrolledContainerColor = Color.Transparent
    )
)
