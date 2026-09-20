package es.criosrango.app

import es.criosrango.shared.account.AccountOrderItem
import es.criosrango.shared.account.AccountOrderSummary
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.Normalizer
import java.util.Locale

private const val CRIOSRANGO_WHATSAPP_NUMBER = "34633246788"

@Composable
fun AccountOrderDetailScreenV2(
    padding: PaddingValues,
    order: AccountOrderSummary,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    var showReturnProductPicker by remember(order.id) { mutableStateOf(false) }

    val date = order.dateCreated?.take(10)?.split("-")?.let {
        if (it.size == 3) "${it[2]}-${it[1]}-${it[0]}" else order.dateCreated.take(10)
    }.orEmpty()

    val province = SPANISH_PROVINCES
        .firstOrNull { it.code.equals(order.shippingAddress.state, true) }
        ?.name ?: order.shippingAddress.state

    val city = if (order.shippingAddress.city.equals("tarancon", true)) "Tarancón" else order.shippingAddress.city

    val shippingAmount = order.shippingTotal.toBigDecimalOrZero()
    val totalAmount = order.total.toBigDecimalOrZero()
    val productsWithTax = totalAmount.subtract(shippingAmount).max(java.math.BigDecimal.ZERO).toPlainString()

    Column(
        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp)
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
        }

        Text("Pedido #${order.number}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        OrderStatusBadge(status = order.status, label = order.statusLabel)

        if (date.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text("Fecha: $date")
        }

        Spacer(Modifier.height(24.dp))

        DetailCard("Productos") {
            order.items.forEach { item ->
                Text("${item.quantity} × ${item.name.cleanWooText()}")
                val vars = item.variations
                    .filter { it.name.isNotBlank() && it.value.isNotBlank() }
                    .joinToString(" · ") { "${if (it.name.equals("Tallas", true)) "Talla" else it.name}: ${it.value.cleanWooText()}" }
                if (vars.isNotBlank()) Text(vars, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(10.dp))
            }
        }

        DetailCard("Pago") {
            Text(if (order.paymentMethodTitle.contains("bizum", true)) "Pago con Bizum" else "Pago con tarjeta")
        }

        val a = order.shippingAddress
        if (a.address1.isNotBlank() || a.city.isNotBlank() || a.postcode.isNotBlank()) {
            DetailCard("Entrega") {
                if (a.address1.isNotBlank()) Text(a.address1)
                val location = listOf(a.postcode, city, province).filter { it.isNotBlank() }.joinToString(" · ")
                if (location.isNotBlank()) Text(location)
                if (a.country.isNotBlank()) Text(if (a.country == "ES") "España" else a.country)
                if (order.shippingMethod.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(if (order.shippingMethod.startsWith("Envío", true)) order.shippingMethod.cleanWooText() else "Envío: ${order.shippingMethod.cleanWooText()}")
                }
            }
        }

        DetailCard("Resumen") {
            Text("Productos: ${orderAmount(productsWithTax, order.currency)}")
            if (order.shippingTotal.isNotBlank()) {
                Text(if (shippingAmount.compareTo(java.math.BigDecimal.ZERO) == 0) "Envío: Gratis" else "Envío: ${orderAmount(order.shippingTotal, order.currency)}")
            }
            Spacer(Modifier.height(8.dp))
            Text("Total: ${orderAmount(order.total, order.currency)}", style = MaterialTheme.typography.titleLarge)
        }

        if (canRequestReturn(order.status, order.statusLabel)) {
            Spacer(Modifier.height(8.dp))
            Text(
                "¿Necesitas devolver algo?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF454145)
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    if (order.items.size == 1) {
                        openWhatsAppReturnRequest(context, order, order.items.first())
                    } else {
                        showReturnProductPicker = true
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = Color(0xFF183B35)
                )
            ) {
                Text("Solicitar devolución")
            }
        }

        Spacer(Modifier.height(24.dp))
    }

    if (showReturnProductPicker) {
        AlertDialog(
            onDismissRequest = { showReturnProductPicker = false },
            title = { Text("¿Qué producto quieres devolver?") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    order.items.forEachIndexed { index, item ->
                        val details = returnVariationDetails(item)
                        OutlinedButton(
                            onClick = {
                                showReturnProductPicker = false
                                openWhatsAppReturnRequest(context, order, item)
                            },
                            modifier = Modifier.fillMaxWidth().padding(bottom = if (index == order.items.lastIndex) 0.dp else 8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color.Transparent,
                                contentColor = Color(0xFF183B35)
                            )
                        ) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(item.name.cleanWooText(), fontWeight = FontWeight.Medium)
                                Text("Cantidad: ${item.quantity}", style = MaterialTheme.typography.bodySmall)
                                details.forEach { (label, value) ->
                                    Text("$label: $value", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showReturnProductPicker = false }) { Text("Cancelar") }
            }
        )
    }
}

private fun canRequestReturn(status: String, label: String): Boolean {
    val normalized = "${normalizeOrderStatus(status)} ${normalizeOrderStatus(label)}"
    return hasStatus(normalized, "PROCESSING", "PROCESANDO", "PREPARACION", "COMPLETED", "COMPLETADO", "SHIPPED", "ENVIADO") &&
        !hasStatus(normalized, "CANCELLED", "CANCELED", "CANCELADO", "FAILED", "FALLIDO", "REFUNDED", "REEMBOLSADO", "PENDING", "PENDING PAYMENT", "PENDIENTE", "ON HOLD", "EN ESPERA")
}

private fun returnVariationDetails(item: AccountOrderItem): List<Pair<String, String>> =
    item.variations
        .filter { it.name.isNotBlank() && it.value.isNotBlank() }
        .mapNotNull { variation ->
            val key = normalizeOrderStatus(variation.name)
            when {
                key == "COLOR" -> "Color" to variation.value.cleanWooText()
                key == "TALLAS" || key == "TALLA" || key == "SIZE" -> "Talla" to variation.value.cleanWooText()
                else -> null
            }
        }

private fun buildReturnWhatsAppMessage(order: AccountOrderSummary, item: AccountOrderItem): String = buildString {
    append("Hola, quiero solicitar la devolución de un producto de mi pedido #${order.number}.\n\n")
    append("Producto: ${item.name.cleanWooText()}\n")
    append("Cantidad: ${item.quantity}\n")
    returnVariationDetails(item).forEach { (label, value) -> append("$label: $value\n") }
    append("\n¿Podéis indicarme cómo proceder?")
}

private fun openWhatsAppReturnRequest(context: Context, order: AccountOrderSummary, item: AccountOrderItem) {
    val message = buildReturnWhatsAppMessage(order, item)
    val uri = Uri.parse("https://wa.me/$CRIOSRANGO_WHATSAPP_NUMBER?text=${Uri.encode(message)}")
    val intent = Intent(Intent.ACTION_VIEW, uri)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$CRIOSRANGO_WHATSAPP_NUMBER?text=${Uri.encode(message)}")))
    }
}

@Composable
fun OrderStatusBadge(status: String, label: String) {
    val visibleLabel = label.ifBlank { status }
    val (backgroundColor, textColor) = orderStatusColors(status, label)
    Surface(shape = MaterialTheme.shapes.extraLarge, color = backgroundColor, contentColor = textColor) {
        Text(text = visibleLabel, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp))
    }
}

private fun orderStatusColors(status: String, label: String): Pair<Color, Color> {
    val normalizedStatus = normalizeOrderStatus(status)
    val normalizedLabel = normalizeOrderStatus(label)
    val combined = "$normalizedStatus $normalizedLabel"
    return when {
        hasStatus(combined, "COMPLETED", "COMPLETADO") -> Color(0xFFE3F1E8) to Color(0xFF285C3B)
        hasStatus(combined, "PROCESSING", "PROCESANDO", "PREPARACION") -> Color(0xFFF4EAD3) to Color(0xFF76591F)
        hasStatus(combined, "SHIPPED", "ENVIADO") -> Color(0xFFE4EFF8) to Color(0xFF245579)
        hasStatus(combined, "PENDING", "PENDING PAYMENT", "PENDIENTE") -> Color(0xFFFFF4D8) to Color(0xFF765A16)
        hasStatus(combined, "ON HOLD", "EN ESPERA") -> Color(0xFFECE9E3) to Color(0xFF5E5A53)
        hasStatus(combined, "CANCELLED", "CANCELED", "CANCELADO") -> Color(0xFFF8E3E1) to Color(0xFF8A332D)
        hasStatus(combined, "REFUNDED", "REEMBOLSADO") -> Color(0xFFEDE7F3) to Color(0xFF5A476D)
        hasStatus(combined, "FAILED", "FALLIDO") -> Color(0xFFF6E0DE) to Color(0xFF8A332D)
        else -> Color(0xFFE9E9E7) to Color(0xFF5C5C58)
    }
}

private fun normalizeOrderStatus(value: String): String =
    Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace("\\p{Mn}+".toRegex(), "")
        .uppercase(Locale.ROOT)
        .replace("&", " AND ")
        .replace("[-_]".toRegex(), " ")
        .replace("[^A-Z0-9 ]".toRegex(), " ")
        .replace("\\s+".toRegex(), " ")
        .trim()

private fun hasStatus(value: String, vararg candidates: String): Boolean =
    candidates.any { candidate -> value == candidate || value.contains(" $candidate ") || value.startsWith("$candidate ") || value.endsWith(" $candidate") }

@Composable
private fun DetailCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = if (title == "Resumen") MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium, fontWeight = if (title == "Resumen") null else FontWeight.Medium)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

private fun orderAmount(value: String, currency: String): String {
    val amount = value.replace('.', ',')
    return if (currency.equals("EUR", true)) "$amount €" else "$amount $currency"
}
