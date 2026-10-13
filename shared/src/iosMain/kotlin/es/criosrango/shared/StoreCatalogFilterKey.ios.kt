package es.criosrango.shared

import platform.Foundation.NSString
import platform.Foundation.decomposedStringWithCanonicalMapping

/** Foundation canonical Unicode decomposition; mark removal is shared in common code. */
actual fun catalogCanonicalDecompose(value: String): String =
    (value as NSString).decomposedStringWithCanonicalMapping()
