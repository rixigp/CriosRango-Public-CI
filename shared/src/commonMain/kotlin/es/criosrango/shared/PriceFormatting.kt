package es.criosrango.shared

fun formatStorePrice(value: String, minorUnit: Int, symbol: String): String {
    val negative = value.startsWith("-")
    val digits = value.removePrefix("-").trimStart('0').ifEmpty { "0" }
    val safeMinorUnit = minorUnit.coerceAtLeast(0)
    val normalized = if (safeMinorUnit == 0) {
        digits
    } else {
        digits.padStart(safeMinorUnit + 1, '0').let { padded ->
            val split = padded.length - safeMinorUnit
            padded.substring(0, split) + "," + padded.substring(split)
        }
    }
    return (if (negative && normalized != "0") "-" else "") + normalized + " " + symbol
}
