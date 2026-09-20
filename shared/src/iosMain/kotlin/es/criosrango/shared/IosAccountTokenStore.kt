package es.criosrango.shared

import es.criosrango.shared.account.AccountTokenStore
import platform.Foundation.NSUserDefaults

class IosAccountTokenStore : AccountTokenStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    override fun load(): String? =
        defaults.stringForKey("criosrango_account_token")

    override fun save(token: String): Boolean {
        if (token.isBlank()) return false
        defaults.setObject(token, forKey = "criosrango_account_token")
        return load() == token
    }

    override fun clear() {
        defaults.removeObjectForKey("criosrango_account_token")
    }
}
