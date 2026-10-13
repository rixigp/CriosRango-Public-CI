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
