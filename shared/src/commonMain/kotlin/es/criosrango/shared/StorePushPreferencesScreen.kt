package es.criosrango.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun StorePushPreferencesScreen(
    newsEnabled: Boolean,
    orderUpdatesEnabled: Boolean,
    onNewsChanged: (Boolean) -> Unit,
    onOrderUpdatesChanged: (Boolean) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Text(StorePushPreferencesContract.TITLE, style = MaterialTheme.typography.titleMedium)
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text(StorePushPreferencesContract.NEWS_TITLE)
                Text(StorePushPreferencesContract.NEWS_DESCRIPTION, style = MaterialTheme.typography.bodySmall)
            }
            Switch(
                checked = newsEnabled,
                onCheckedChange = onNewsChanged,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = StoreAccountColors.switchCheckedThumb,
                    checkedTrackColor = StoreAccountColors.switchCheckedTrack,
                    checkedBorderColor = StoreAccountColors.primary
                )
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text(StorePushPreferencesContract.ORDER_UPDATES_TITLE)
                Text(StorePushPreferencesContract.ORDER_UPDATES_DESCRIPTION, style = MaterialTheme.typography.bodySmall)
            }
            Switch(
                checked = orderUpdatesEnabled,
                onCheckedChange = onOrderUpdatesChanged,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = StoreAccountColors.switchCheckedThumb,
                    checkedTrackColor = StoreAccountColors.switchCheckedTrack,
                    checkedBorderColor = StoreAccountColors.primary
                )
            )
        }
    }
}
