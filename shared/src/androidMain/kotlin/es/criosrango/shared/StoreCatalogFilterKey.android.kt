package es.criosrango.shared

import java.text.Normalizer

/** Android canonical Unicode decomposition; mark removal is shared in common code. */
actual fun catalogCanonicalDecompose(value: String): String =
    Normalizer.normalize(value, Normalizer.Form.NFD)
