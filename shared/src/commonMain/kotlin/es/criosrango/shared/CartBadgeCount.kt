package es.criosrango.shared

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Badge
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Number of units represented by a cart snapshot, independent of backend items_count. */
fun cartUnitCount(quantities: Iterable<Int>): Int =
    quantities.sumOf { it.coerceAtLeast(0) }

/** Compact label shared by every cart badge. */
fun cartBadgeText(quantity: Int): String =
    if (quantity > 99) "99+" else quantity.coerceAtLeast(0).toString()


/** Shared WEB-matched green cart badge used by the top bar and bottom navigation. */
@Composable
internal fun StoreCartBadge(
    quantity: Int,
    size: Dp,
    fontSize: TextUnit,
    fontWeight: FontWeight = FontWeight.Bold,
    modifier: Modifier = Modifier
) {
    if (quantity <= 0) return
    Badge(
        modifier = modifier.height(size).widthIn(min = size),
        containerColor = Color(0xFF355F4B),
        contentColor = Color.White
    ) {
        Text(
            text = cartBadgeText(quantity),
            maxLines = 1,
            fontSize = fontSize,
            lineHeight = size.value.sp,
            fontWeight = fontWeight
        )
    }
}
