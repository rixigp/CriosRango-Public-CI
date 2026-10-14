package es.criosrango.shared

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/** Reuses the approved filter-button dimensions, border, typography and padding. */
@Composable
internal fun StoreRootFilterButton(onClick: () -> Unit, label: String = "Filtros") {
    OutlinedButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
        Text(label)
    }
}
