package es.criosrango.shared

import java.text.Normalizer

/** Android's NFD + combining-mark removal, matching the historical catalog filter contract. */
actual fun catalogUnicodeFold(value: String): String =
    Normalizer.normalize(value, Normalizer.Form.NFD).replace("\\p{M}+".toRegex(), "")
