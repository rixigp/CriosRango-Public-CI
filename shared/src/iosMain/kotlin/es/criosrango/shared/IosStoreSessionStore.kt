package es.criosrango.shared

import es.criosrango.shared.api.StoreSessionStore
import platform.Foundation.NSUserDefaults

class IosStoreSessionStore : StoreSessionStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    override var cartToken: String?
        get() = IosSecureKeyValueStore.read("woo_cart_token", defaults)
        set(value) {
            if (value == null) IosSecureKeyValueStore.delete("woo_cart_token", defaults)
            else IosSecureKeyValueStore.write("woo_cart_token", value, defaults)
        }

    override var nonce: String?
        get() = IosSecureKeyValueStore.read("woo_nonce", defaults)
        set(value) {
            if (value == null) IosSecureKeyValueStore.delete("woo_nonce", defaults)
            else IosSecureKeyValueStore.write("woo_nonce", value, defaults)
        }

    override var cookieHeader: String?
        get() = IosSecureKeyValueStore.read("woo_cookie_header", defaults)
        set(value) {
            if (value == null) IosSecureKeyValueStore.delete("woo_cookie_header", defaults)
            else IosSecureKeyValueStore.write("woo_cookie_header", value, defaults)
        }
}
