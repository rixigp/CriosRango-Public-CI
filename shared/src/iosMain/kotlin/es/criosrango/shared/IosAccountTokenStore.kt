package es.criosrango.shared

import es.criosrango.shared.account.AccountTokenStore
import platform.Foundation.NSUserDefaults

class IosAccountTokenStore : AccountTokenStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    override fun load(): String? = IosSecureKeyValueStore.read(TOKEN_KEY, defaults)

    override fun save(token: String): Boolean {
        if (token.isBlank()) return false
        val saved = IosSecureKeyValueStore.write(TOKEN_KEY, token, defaults)
        if (saved) IosPushPreferenceSyncRuntime.sessionChanged()
        return saved
    }

    override fun clear() {
        // logout() synchronously snapshots the current APNs token + account A before this is removed.
        IosPushPreferenceSyncRuntime.logout()
        IosSecureKeyValueStore.delete(TOKEN_KEY, defaults)
    }

    private companion object {
        const val TOKEN_KEY = "criosrango_account_token"
    }
}
