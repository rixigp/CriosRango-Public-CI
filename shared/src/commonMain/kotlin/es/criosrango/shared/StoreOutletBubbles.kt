package es.criosrango.shared

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Single Android-baseline shared presentation for Outlet category bubbles. */
@Composable
fun StoreOutletBubbles(
    bubbles: List<OutletBubbleDefinition>,
    selectedKey: String?,
    onSelected: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutletBubble(label = "Todas", selected = selectedKey == null, onClick = { onSelected(null) })
        bubbles.forEach { bubble ->
            OutletBubble(
                label = outletBubbleDisplayLabel(bubble.label),
                selected = selectedKey == bubble.key,
                onClick = { onSelected(bubble.key) }
            )
        }
    }
}

@Composable
private fun OutletBubble(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.height(34.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(17.dp),
        color = if (selected) Color(0xFF163B35) else Color(0xFFF1EDEF)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp).then(Modifier),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = if (selected) Color.White else Color(0xFF3F3A3D),
                fontSize = 13.sp,
                lineHeight = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Clip
            )
        }
    }
}
