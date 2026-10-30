package es.criosrango.shared

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity

/** Shared shell owns chrome and forwards Scaffold insets; each body keeps its historical padding semantics. */
@Composable
fun StoreGlobalShell(
    selectedTab: StoreMainTab,
    cartQuantity: Int,
    onTabSelected: (StoreMainTab) -> Unit,
    topBar: @Composable () -> Unit,
    bottomNavigationModifier: Modifier = Modifier,
    hideBottomBarWhenImeVisible: Boolean = false,
    content: @Composable (PaddingValues) -> Unit
) {
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = topBar,
        bottomBar = {
            if (!hideBottomBarWhenImeVisible || !imeVisible) {
                StoreBottomNavigation(
                    selectedTab = selectedTab,
                    cartQuantity = cartQuantity,
                    onTabSelected = onTabSelected,
                    modifier = bottomNavigationModifier
                )
            }
        }
    ) { paddingValues ->
        content(paddingValues)
    }
}
