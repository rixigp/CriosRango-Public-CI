package es.criosrango.shared

import androidx.compose.ui.window.ComposeUIViewController
import es.criosrango.shared.account.AccountRepository
import es.criosrango.shared.api.StoreApiClient

fun MainViewController() = ComposeUIViewController {
    val storeSession = IosStoreSessionStore()
    val storeApi = StoreApiClient(session = storeSession)
    val cartStore = StoreCartStore(storeApi)
    val accountRepository = AccountRepository(
        tokenStore = IosAccountTokenStore(),
        claimOrderStore = IosClaimOrderStore()
    )
    val checkoutStore = StoreCheckoutStore(storeApi, cartStore, accountRepository)
    CriosRangoIOSRootScreen(
        storeApi = storeApi,
        accountRepository = accountRepository,
        cartStore = cartStore,
        checkoutStore = checkoutStore
    )
}
