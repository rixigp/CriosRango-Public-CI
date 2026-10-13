package es.criosrango.shared

import platform.Foundation.NSDiacriticInsensitiveSearch
import platform.Foundation.NSString

/** Foundation performs canonical Unicode diacritic folding on iOS. */
actual fun catalogUnicodeFold(value: String): String =
    NSString.create(string = value)
        .stringByFoldingWithOptions(NSDiacriticInsensitiveSearch, locale = null)
