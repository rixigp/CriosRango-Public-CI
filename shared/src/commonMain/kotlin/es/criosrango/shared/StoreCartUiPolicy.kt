package es.criosrango.shared

/** Shared visibility rule; platforms provide auth state and a native login callback. */
fun shouldShowCartLoginCta(isAuthenticated: Boolean, hasLoginAction: Boolean): Boolean =
    !isAuthenticated && hasLoginAction
