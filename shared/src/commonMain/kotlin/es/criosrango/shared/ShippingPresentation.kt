package es.criosrango.shared

import es.criosrango.shared.model.StoreShippingDestination
import es.criosrango.shared.model.StoreShippingPackage

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

/**
 * Restores Android's historical Tarancón eligibility filter at the shared Store API boundary.
 * Only Correos Express is hidden for Tarancón (Cuenca / CP 16400); other destinations and
 * Tarancón's free delivery / store-pickup options remain unchanged.
 */
fun filterStoreShippingPackagesForDestination(
    packages: List<StoreShippingPackage>,
    fallbackDestination: StoreShippingDestination? = null
): List<StoreShippingPackage> = packages.map { pack ->
    val destination = pack.destination ?: fallbackDestination
    if (!isTarancónDestination(destination)) return@map pack
    pack.copy(rates = pack.rates.filterNot { rate ->
        listOf(rate.name, rate.methodId, rate.rateId)
            .joinToString(" ")
            .normalizedShippingKey()
            .contains("correos express")
    })
}

private fun isTarancónDestination(destination: StoreShippingDestination?): Boolean {
    if (destination == null) return false
    val city = destination.city.normalizedShippingKey()
    val postcode = destination.postcode.trim()
    val province = destination.state.normalizedShippingKey()
    return city.contains("tarancon") && (postcode.startsWith("1640") ||
        province.contains("cuenca") || province == "cu")
}

private fun String.normalizedShippingKey(): String = lowercase()
    .replace('á', 'a').replace('é', 'e').replace('í', 'i')
    .replace('ó', 'o').replace('ú', 'u').replace('ü', 'u')
