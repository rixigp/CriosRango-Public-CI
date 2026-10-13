package es.criosrango.shared

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Shared shell owns chrome and forwards Scaffold insets; each body keeps its historical padding semantics. */
@Composable
fun StoreGlobalShell(
    selectedTab: StoreMainTab,
    cartQuantity: Int,
    onTabSelected: (StoreMainTab) -> Unit,
    topBar: @Composable () -> Unit,
    bottomNavigationModifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = topBar,
        bottomBar = {
            StoreBottomNavigation(
                selectedTab = selectedTab,
                cartQuantity = cartQuantity,
                onTabSelected = onTabSelected,
                modifier = bottomNavigationModifier
            )
        }
    ) { paddingValues ->
        content(paddingValues)
    }
}
