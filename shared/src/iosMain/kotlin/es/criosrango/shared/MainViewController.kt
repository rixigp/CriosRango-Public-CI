package es.criosrango.shared

import androidx.compose.ui.window.ComposeUIViewController

fun MainViewController() = ComposeUIViewController {
    CriosRangoIOSAccountScreen(AccountRepository(IosAccountTokenStore()))
}
