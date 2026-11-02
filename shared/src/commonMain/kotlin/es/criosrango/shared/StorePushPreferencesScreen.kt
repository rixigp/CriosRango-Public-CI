package es.criosrango.shared

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inventory
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun StorePushPreferencesScreen(
    newsEnabled: Boolean,
    orderUpdatesEnabled: Boolean,
    onNewsChanged: (Boolean) -> Unit,
    onOrderUpdatesChanged: (Boolean) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Text(StorePushPreferencesContract.TITLE, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Color(0xFF183B35))
        Spacer(Modifier.size(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFE6EBE8)),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(Modifier.fillMaxWidth()) {
                StorePushPreferenceRow(
                    icon = { Icon(Icons.Outlined.Notifications, contentDescription = null) },
                    title = StorePushPreferencesContract.NEWS_TITLE,
                    description = StorePushPreferencesContract.NEWS_DESCRIPTION,
                    checked = newsEnabled,
                    onCheckedChange = onNewsChanged
                )
                HorizontalDivider(Modifier.padding(start = 48.dp), color = Color(0xFFE8ECE9), thickness = 1.dp)
                StorePushPreferenceRow(
                    icon = { Icon(Icons.Outlined.Inventory, contentDescription = null) },
                    title = StorePushPreferencesContract.ORDER_UPDATES_TITLE,
                    description = StorePushPreferencesContract.ORDER_UPDATES_DESCRIPTION,
                    checked = orderUpdatesEnabled,
                    onCheckedChange = onOrderUpdatesChanged
                )
            }
        }
    }
}

@Composable
private fun StorePushPreferenceRow(
    icon: @Composable () -> Unit,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        androidx.compose.foundation.layout.Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides Color(0xFF183B35)) {
                icon()
            }
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = Color(0xFF24332E))
            Text(description, style = MaterialTheme.typography.bodySmall, color = Color(0xFF68746E))
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = StoreAccountColors.switchCheckedThumb,
                checkedTrackColor = StoreAccountColors.switchCheckedTrack,
                checkedBorderColor = StoreAccountColors.primary
            )
        )
    }
}
