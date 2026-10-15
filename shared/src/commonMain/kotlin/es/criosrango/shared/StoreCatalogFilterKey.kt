package es.criosrango.shared

import kotlin.text.CharCategory

/** Normalizes catalog filter values using shared canonical decomposition and mark removal. */
fun storeCatalogFilterKey(value: String): String {
    val decomposed = catalogCanonicalDecompose(value.lowercase().trim())
    val withoutMarks = decomposed.filterNot {
        it.category == CharCategory.NON_SPACING_MARK ||
            it.category == CharCategory.COMBINING_SPACING_MARK ||
            it.category == CharCategory.ENCLOSING_MARK
    }
    return withoutMarks.replace(Regex("\\s+"), " ").trim()
}

/** Applies canonical Unicode decomposition; combining marks are removed in common code. */
expect fun catalogCanonicalDecompose(value: String): String

/**
 * Decodes HTML character references in user-facing metadata labels only.
 * Unknown named references are preserved; numeric references support decimal and hexadecimal forms.
 */
fun decodeFilterMetadataHtmlEntities(value: String): String {
    val namedEntities = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'",
        "nbsp" to "\u00A0", "iexcl" to "¡", "cent" to "¢", "pound" to "£", "curren" to "¤",
        "yen" to "¥", "brvbar" to "¦", "sect" to "§", "uml" to "¨", "copy" to "©",
        "ordf" to "ª", "laquo" to "«", "not" to "¬", "shy" to "\u00AD", "reg" to "®",
        "macr" to "¯", "deg" to "°", "plusmn" to "±", "sup2" to "²", "sup3" to "³",
        "acute" to "´", "micro" to "µ", "para" to "¶", "middot" to "·", "cedil" to "¸",
        "sup1" to "¹", "ordm" to "º", "raquo" to "»", "frac14" to "¼", "frac12" to "½",
        "frac34" to "¾", "iquest" to "¿", "Agrave" to "À", "Aacute" to "Á", "Acirc" to "Â",
        "Atilde" to "Ã", "Auml" to "Ä", "Aring" to "Å", "AElig" to "Æ", "Ccedil" to "Ç",
        "Egrave" to "È", "Eacute" to "É", "Ecirc" to "Ê", "Euml" to "Ë", "Igrave" to "Ì",
        "Iacute" to "Í", "Icirc" to "Î", "Iuml" to "Ï", "ETH" to "Ð", "Ntilde" to "Ñ",
        "Ograve" to "Ò", "Oacute" to "Ó", "Ocirc" to "Ô", "Otilde" to "Õ", "Ouml" to "Ö",
        "times" to "×", "Oslash" to "Ø", "Ugrave" to "Ù", "Uacute" to "Ú", "Ucirc" to "Û",
        "Uuml" to "Ü", "Yacute" to "Ý", "THORN" to "Þ", "szlig" to "ß", "agrave" to "à",
        "aacute" to "á", "acirc" to "â", "atilde" to "ã", "auml" to "ä", "aring" to "å",
        "aelig" to "æ", "ccedil" to "ç", "egrave" to "è", "eacute" to "é", "ecirc" to "ê",
        "euml" to "ë", "igrave" to "ì", "iacute" to "í", "icirc" to "î", "iuml" to "ï",
        "eth" to "ð", "ntilde" to "ñ", "ograve" to "ò", "oacute" to "ó", "ocirc" to "ô",
        "otilde" to "õ", "ouml" to "ö", "divide" to "÷", "oslash" to "ø", "ugrave" to "ù",
        "uacute" to "ú", "ucirc" to "û", "uuml" to "ü", "yacute" to "ý", "thorn" to "þ",
        "yuml" to "ÿ", "OElig" to "Œ", "oelig" to "œ", "Scaron" to "Š", "scaron" to "š",
        "Yuml" to "Ÿ", "fnof" to "ƒ", "circ" to "ˆ", "tilde" to "˜", "ensp" to "\u2002",
        "emsp" to "\u2003", "thinsp" to "\u2009", "ndash" to "–", "mdash" to "—",
        "lsquo" to "‘", "rsquo" to "’", "sbquo" to "‚", "ldquo" to "“", "rdquo" to "”",
        "bdquo" to "„", "dagger" to "†", "Dagger" to "‡", "bull" to "•", "hellip" to "…",
        "permil" to "‰", "prime" to "′", "Prime" to "″", "lsaquo" to "‹", "rsaquo" to "›",
        "oline" to "‾", "frasl" to "⁄", "euro" to "€", "trade" to "™", "larr" to "←",
        "uarr" to "↑", "rarr" to "→", "darr" to "↓", "harr" to "↔", "minus" to "−",
        "ne" to "≠", "le" to "≤", "ge" to "≥", "infin" to "∞", "times" to "×",
        "plus" to "+", "check" to "✓"
    )
    val reference = Regex("&(#(?:[xX][0-9a-fA-F]+|[0-9]+)|[A-Za-z][A-Za-z0-9]+);")
    return reference.replace(value) { match ->
        val token = match.groupValues[1]
        if (token.startsWith("#")) {
            val hex = token.startsWith("#x", ignoreCase = true)
            val digits = token.substring(if (hex) 2 else 1)
            val codePoint = digits.toLongOrNull(if (hex) 16 else 10)
            codePoint?.takeIf { it in 1L..0x10FFFFL && it !in 0xD800L..0xDFFFL }
                ?.let { htmlCodePointToString(it.toInt()) } ?: match.value
        } else {
            namedEntities[token] ?: match.value
        }
    }
}

/** Decodes references and normalizes display-only whitespace without changing canonical IDs or slugs. */
fun normalizeFilterMetadataDisplayName(value: String): String =
    decodeFilterMetadataHtmlEntities(value).replace(Regex("\\s+"), " ").trim()

/** Removes exact duplicate display labels while preserving first-seen order and case distinctions. */
fun distinctFilterMetadataDisplayNames(values: List<String>): List<String> =
    values.map(::normalizeFilterMetadataDisplayName).distinct()

/** Filters already-loaded brand labels locally; this helper never performs API work. */
fun filterBrandDisplayNames(values: List<String>, query: String): List<String> {
    val normalizedQuery = storeCatalogFilterKey(query)
    return distinctFilterMetadataDisplayNames(values)
        .filter { normalizedQuery.isBlank() || storeCatalogFilterKey(it).contains(normalizedQuery) }
        .sortedWith(compareBy({ storeCatalogFilterKey(it) }, { it }))
}

private fun htmlCodePointToString(codePoint: Int): String {
    if (codePoint <= 0xFFFF) return codePoint.toChar().toString()
    val adjusted = codePoint - 0x10000
    return charArrayOf(
        (0xD800 + (adjusted shr 10)).toChar(),
        (0xDC00 + (adjusted and 0x3FF)).toChar()
    ).concatToString()
}
