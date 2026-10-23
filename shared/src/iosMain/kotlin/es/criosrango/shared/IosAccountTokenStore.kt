package es.criosrango.shared

import es.criosrango.shared.account.AccountTokenStore
import platform.Foundation.NSUserDefaults

class IosAccountTokenStore : AccountTokenStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    override fun load(): String? = defaults.stringForKey("criosrango_account_token")

    override fun save(token: String): Boolean {
        if (token.isBlank()) return false
        defaults.setObject(token, forKey = "criosrango_account_token")
        val saved = load() == token
        if (saved) IosPushPreferenceSyncRuntime.sessionChanged()
        return saved
    }

    override fun clear() {
        // logout() synchronously snapshots the current APNs token + account A before this is removed.
        IosPushPreferenceSyncRuntime.logout()
        defaults.removeObjectForKey("criosrango_account_token")
    }
}
