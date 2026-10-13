package es.criosrango.shared

import platform.Foundation.NSString

/** Foundation canonical Unicode decomposition; mark removal is shared in common code. */
actual fun catalogCanonicalDecompose(value: String): String =
    (value as NSString).decomposedStringWithCanonicalMapping
