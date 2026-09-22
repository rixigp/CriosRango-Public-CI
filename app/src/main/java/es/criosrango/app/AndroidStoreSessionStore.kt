package es.criosrango.app

import es.criosrango.shared.api.StoreSessionStore

class AndroidStoreSessionStore(
    private val session: StoreSession
) : StoreSessionStore {

    override var cartToken: String?
        get() = session.cartToken
        set(value) { if (value != null) session.setCartToken(value) }

    override var nonce: String?
        get() = session.nonce
        set(value) { if (value != null) session.setNonce(value) }

    override var cookieHeader: String?
        get() = session.cookieHeader
        set(value) { if (value != null) session.setCookieHeader(value) }
}
