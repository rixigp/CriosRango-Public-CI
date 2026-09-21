package es.criosrango.shared

import androidx.compose.ui.window.ComposeUIViewController
import es.criosrango.shared.account.AccountRepository

fun MainViewController() = ComposeUIViewController {
    CriosRangoIOSAccountScreen(AccountRepository(IosAccountTokenStore()))
}
