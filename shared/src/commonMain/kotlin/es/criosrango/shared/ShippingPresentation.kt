package es.criosrango.shared

/**
 * Single UI-only shipping label resolver. It never changes the WooCommerce method/rate IDs,
 * selected state, price, or eligibility; those remain authoritative backend data.
 */
fun resolveShippingPresentationName(methodId: String, rateId: String, backendLabel: String): String {
    val label = backendLabel.trim()
    val normalized = label.lowercase()
        .replace('á', 'a').replace('é', 'e').replace('í', 'i')
        .replace('ó', 'o').replace('ú', 'u').replace('ü', 'u')
    val effectiveMethod = methodId.trim().ifBlank { rateId.substringBefore(':').trim() }

    // A rate's actual label is more specific than its shared method ID. Some WooCommerce
    // installations expose a Tarancón delivery rate under local_pickup, so check it first.
    if (normalized.contains("tarancon") &&
        (normalized.contains("envio") || normalized.contains("entrega") || normalized.contains("delivery"))
    ) return "Envío a Tarancón"

    if (normalized.contains("recogida") || normalized.contains("local pickup") ||
        normalized.contains("pickup") || effectiveMethod == "local_pickup"
    ) return "Recogida en tienda"

    if (normalized.contains("correos express")) return "Correos Express"

    return when (effectiveMethod) {
        "free_shipping" -> "Envío a domicilio"
        "flat_rate" -> if (label.isBlank()) "Envío a domicilio" else stripFreeShippingWording(label)
        else -> stripFreeShippingWording(label).ifBlank { "Envío" }
    }
}

private fun stripFreeShippingWording(label: String): String =
    label.replace(Regex("(?i)\\b(gratuito|gratuita|gratis|free\\s+shipping|free)\\b"), "")
        .replace(Regex("\\s+"), " ")
        .trim(' ', '-', '—', ':')
        .ifBlank { "Envío a domicilio" }
