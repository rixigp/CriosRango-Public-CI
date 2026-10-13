package es.criosrango.shared

/** Normalizes catalog filter values using the platform's canonical Unicode folding primitive. */
fun storeCatalogFilterKey(value: String): String =
    catalogUnicodeFold(value.lowercase().trim()).replace(Regex("\\s+"), " ").trim()

/** Removes canonical combining marks without changing other characters. */
expect fun catalogUnicodeFold(value: String): String
