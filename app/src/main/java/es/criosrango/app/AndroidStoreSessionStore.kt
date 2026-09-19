package es.criosrango.app

import android.content.SharedPreferences
import es.criosrango.shared.api.StoreSessionStore

class AndroidStoreSessionStore(
    private val preferences: SharedPreferences
) : StoreSessionStore {
    private val session = StoreSession(preferences)

    override var cartToken: String?
        get() = session.cartToken
        set(value) { if (value != null) preferences.edit().putString("woo_cart_token", value).apply() }

    override var nonce: String?
        get() = session.nonce
        set(value) { if (value != null) preferences.edit().putString("woo_nonce", value).apply() }

    override var cookieHeader: String?
        get() = session.cookieHeader
        set(value) { if (value != null) preferences.edit().putString("woo_cookie_header", value).apply() }
}
