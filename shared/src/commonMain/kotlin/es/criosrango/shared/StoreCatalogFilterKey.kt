package es.criosrango.shared

/** Catalog filter normalization shared by Android and iOS, matching Android's NFD/mark-removal semantics. */
fun storeCatalogFilterKey(value: String): String {
    val unaccented = buildString {
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
    return unaccented.replace(Regex("\\s+"), " ")
}
