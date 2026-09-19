package es.criosrango.shared

import es.criosrango.shared.api.StoreSessionStore
import platform.Foundation.NSUserDefaults

class IosStoreSessionStore : StoreSessionStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    override var cartToken: String?
        get() = defaults.stringForKey("woo_cart_token")
        set(value) {
            if (value == null) defaults.removeObjectForKey("woo_cart_token")
            else defaults.setObject(value, forKey = "woo_cart_token")
        }

    override var nonce: String?
        get() = defaults.stringForKey("woo_nonce")
        set(value) {
            if (value == null) defaults.removeObjectForKey("woo_nonce")
            else defaults.setObject(value, forKey = "woo_nonce")
        }

    override var cookieHeader: String?
        get() = defaults.stringForKey("woo_cookie_header")
        set(value) {
            if (value == null) defaults.removeObjectForKey("woo_cookie_header")
            else defaults.setObject(value, forKey = "woo_cookie_header")
        }
}
