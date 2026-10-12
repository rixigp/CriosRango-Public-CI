package es.criosrango.shared

private val wooPlainMarkupRegex = Regex("<[^>]*>")
private val wooBreakTagRegex = Regex("<\\s*br\\s*/?\\s*>", RegexOption.IGNORE_CASE)
private val wooParagraphTagRegex = Regex("<\\s*/?\\s*p\\b[^>]*>", RegexOption.IGNORE_CASE)
private val wooAnyMarkupRegex = Regex("<[^>]*>")
private val wooEntityRegex = Regex("&(#x[0-9a-fA-F]+|#X[0-9a-fA-F]+|#\\d+|[A-Za-z][A-Za-z0-9]+);")

/** Normalizes WooCommerce labels without interpreting decoded entities a second time. */
fun normalizeWooPlainText(raw: String): String {
    val withUnicodeEscapes = decodeWooUnicodeEscapes(raw)
    val withoutMarkup = wooPlainMarkupRegex.replace(withUnicodeEscapes, "")
    return decodeWooEntitiesOnce(withoutMarkup).trim()
}

/** Converts WooCommerce HTML descriptions to plain text, preserving line and paragraph breaks. */
fun normalizeWooDescriptionText(raw: String): String {
    val withUnicodeEscapes = decodeWooUnicodeEscapes(raw)
    val withBreaks = wooBreakTagRegex.replace(withUnicodeEscapes, "\n")
    val withParagraphBreaks = wooParagraphTagRegex.replace(withBreaks, "\n")
    val withoutMarkup = wooAnyMarkupRegex.replace(withParagraphBreaks, "")
    val decoded = decodeWooEntitiesOnce(withoutMarkup)
    return decoded
        .replace(Regex("[ \\t]*\\n[ \\t]*"), "\n")
        .replace(Regex("\\n{2,}"), "\n")
        .trim()
}

private fun decodeWooUnicodeEscapes(raw: String): String {
    val result = StringBuilder(raw.length)
    var index = 0
    while (index < raw.length) {
        if (raw[index] == '\\' && index + 5 < raw.length && raw[index + 1] == 'u') {
            val hex = raw.substring(index + 2, index + 6)
            val codeUnit = hex.toIntOrNull(16)
            if (codeUnit != null) {
                result.append(codeUnit.toChar())
                index += 6
                continue
            }
        }
        result.append(raw[index])
        index++
    }
    return result.toString()
}

private fun decodeWooEntitiesOnce(raw: String): String =
    wooEntityRegex.replace(raw) { match ->
        val entity = match.value
        val body = match.groupValues[1]
        when (body) {
            "amp" -> "&"
            "nbsp" -> "\u00A0"
            "quot" -> "\""
            "apos" -> "'"
            "lt" -> "<"
            "gt" -> ">"
            "ndash" -> "–"
            "mdash" -> "—"
            "rsquo" -> "’"
            "lsquo" -> "‘"
            "rdquo" -> "”"
            "ldquo" -> "“"
            "hellip" -> "…"
            "copy" -> "©"
            "reg" -> "®"
            else -> decodeWooNumericEntity(body) ?: entity
        }
    }

private fun decodeWooNumericEntity(body: String): String? {
    val codePoint = when {
        body.startsWith("#x") -> body.substring(2).toLongOrNull(16)
        body.startsWith("#X") -> body.substring(2).toLongOrNull(16)
        body.startsWith("#") -> body.substring(1).toLongOrNull(10)
        else -> null
    } ?: return null
    if (codePoint !in 0L..0x10FFFFL || codePoint in 0xD800L..0xDFFFL) return null
    if (codePoint <= 0xFFFFL) return codePoint.toInt().toChar().toString()
    val adjusted = codePoint - 0x10000L
    val high = (0xD800L + (adjusted shr 10)).toInt().toChar()
    val low = (0xDC00L + (adjusted and 0x3FFL)).toInt().toChar()
    return "$high$low"
}
