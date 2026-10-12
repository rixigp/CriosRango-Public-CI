package es.criosrango.shared

import androidx.compose.ui.graphics.Color

/**
 * Shared visual mapping for visible WooCommerce color term names.
 *
 * Keep the rule order and values aligned with the historical Android mapping.
 */
fun productColorSwatchMapping(value: String): Color? {
    val key = normalizeProductColorName(value)
    return when {
        key.startsWith("#") && key.length == 7 -> runCatching {
            val rgb = key.substring(1).toLong(16).toInt()
            Color(0xFF000000.toInt() or rgb)
        }.getOrNull()
        "azul pavo" in key -> Color(0xFF315D66)
        "marino" in key -> Color(0xFF1F2D4D)
        "azul" in key -> Color(0xFF527DA8)
        "celeste" in key -> Color(0xFF9BCBE3)
        "turquesa" in key -> Color(0xFF3AAFA9)
        "verde botella" in key -> Color(0xFF274E3C)
        "verde" in key -> Color(0xFF668A63)
        "menta" in key -> Color(0xFFA8D5BA)
        "rojo" in key -> Color(0xFFB94A48)
        "granate" in key || "burdeos" in key -> Color(0xFF743A46)
        "rosa" in key -> Color(0xFFE6A7B8)
        "fucsia" in key -> Color(0xFFC74779)
        "naranja" in key -> Color(0xFFD9824B)
        "amarillo" in key -> Color(0xFFE4C34A)
        "mostaza" in key -> Color(0xFFC89B3C)
        "violeta" in key || "morado" in key -> Color(0xFF80658D)
        "lila" in key -> Color(0xFFB6A0C9)
        "beige" in key || "arena" in key -> Color(0xFFD8C6A5)
        "camel" in key -> Color(0xFFB9855C)
        "marron" in key || "chocolate" in key -> Color(0xFF765344)
        "crudo" in key -> Color(0xFFE9E1D1)
        "berenjena" in key -> Color(0xFF5B3A5A)
        "caldero" in key -> Color(0xFFB5653D)
        "chicle" in key -> Color(0xFFF3A6C8)
        "coral" in key -> Color(0xFFF08080)
        "kaki" in key -> Color(0xFF8A8F5A)
        "oro" in key -> Color(0xFFD4AF37)
        "pistacho" in key -> Color(0xFFA8C686)
        "salmon" in key -> Color(0xFFFA8072)
        "blanco" in key -> Color.White
        "negro" in key -> Color(0xFF222222)
        "gris" in key -> Color(0xFF8A8A8A)
        else -> null
    }
}

/**
 * commonMain-safe equivalent of the Android mapping's trim/lowercase/NFD
 * diacritic removal for Latin characters. Combining marks are discarded so
 * both precomposed and decomposed accented names normalize consistently.
 */
private fun normalizeProductColorName(value: String): String = buildString {
    value.lowercase().trim().forEach { character ->
        when (character) {
            'à', 'á', 'â', 'ã', 'ä', 'å', 'ā', 'ă', 'ą' -> append('a')
            'ç', 'ć', 'ĉ', 'ċ', 'č' -> append('c')
            'ď' -> append('d')
            'è', 'é', 'ê', 'ë', 'ē', 'ĕ', 'ė', 'ę', 'ě' -> append('e')
            'ĝ', 'ğ', 'ġ', 'ģ' -> append('g')
            'ĥ' -> append('h')
            'ì', 'í', 'î', 'ï', 'ĩ', 'ī', 'ĭ', 'į' -> append('i')
            'ĵ' -> append('j')
            'ķ' -> append('k')
            'ĺ', 'ļ', 'ľ' -> append('l')
            'ñ', 'ń', 'ņ', 'ň' -> append('n')
            'ò', 'ó', 'ô', 'õ', 'ö', 'ō', 'ŏ', 'ő' -> append('o')
            'ŕ', 'ŗ', 'ř' -> append('r')
            'ś', 'ŝ', 'ş', 'š' -> append('s')
            'ţ', 'ť' -> append('t')
            'ù', 'ú', 'û', 'ü', 'ũ', 'ū', 'ŭ', 'ů', 'ű', 'ų' -> append('u')
            'ŵ' -> append('w')
            'ý', 'ÿ', 'ŷ' -> append('y')
            'ź', 'ż', 'ž' -> append('z')
            in '\u0300'..'\u036F' -> Unit
            else -> append(character)
        }
    }
}
