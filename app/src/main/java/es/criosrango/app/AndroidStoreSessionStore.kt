package es.criosrango.app

import es.criosrango.shared.api.StoreSessionStore

class AndroidStoreSessionStore(
    private val session: StoreSession
) : StoreSessionStore {

    override var cartToken: String?
        get() = session.cartToken
        set(value) { session.cartToken = value }

    override var nonce: String?
        get() = session.nonce
        set(value) { session.nonce = value }

    override var cookieHeader: String?
        get() = session.cookieHeader
        set(value) { session.cookieHeader = value }
}
