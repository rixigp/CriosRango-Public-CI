package es.criosrango.shared

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import es.criosrango.shared.account.AccountOrderItem
import es.criosrango.shared.account.AccountOrderSummary
import es.criosrango.shared.account.AccountRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class StoreOrdersState(
    val loading: Boolean = false,
    val orders: List<AccountOrderSummary> = emptyList(),
    val error: String? = null,
    val refreshing: Boolean = false,
    val authenticatedUserId: Int? = null
)

class StoreOrdersOwner(
    private val repository: AccountRepository,
    private val sessionState: StoreAccountSessionState
) {
    private val mutableState = MutableStateFlow(StoreOrdersState())
    val state: StateFlow<StoreOrdersState> = mutableState.asStateFlow()
    private val loadMutex = Mutex()
    private var generation = 0L
    private var loadedForUser: Int? = null

    fun onSessionChanged(userId: Int?) {
        if (userId == null || !repository.hasSession) {
            generation++
            loadedForUser = null
            mutableState.value = StoreOrdersState()
        } else if (mutableState.value.authenticatedUserId != userId) {
            generation++
            loadedForUser = null
            mutableState.value = StoreOrdersState(loading = true, authenticatedUserId = userId)
        }
    }

    suspend fun load(force: Boolean = false) {
        val userId = sessionState.snapshot.value.user?.id
        if (userId == null || !repository.hasSession) {
            onSessionChanged(null)
            return
        }
        if (!force && loadedForUser == userId && mutableState.value.error == null) return
        if (!loadMutex.tryLock()) return
        val requestGeneration = generation
        try {
            if (sessionState.snapshot.value.user?.id != userId || !repository.hasSession) return
            mutableState.value = mutableState.value.copy(
                loading = mutableState.value.orders.isEmpty(),
                refreshing = mutableState.value.orders.isNotEmpty(),
                error = null,
                authenticatedUserId = userId
            )
            try {
                val response = repository.orders(20).orders
                if (requestGeneration == generation &&
                    repository.hasSession &&
                    sessionState.snapshot.value.user?.id == userId
                ) {
                    mutableState.value = StoreOrdersState(
                        orders = response,
                        loading = false,
                        refreshing = false,
                        authenticatedUserId = userId
                    )
                    loadedForUser = userId
                }
            } catch (cancelled: CancellationException) {
                if (requestGeneration == generation) {
                    mutableState.value = mutableState.value.copy(loading = false, refreshing = false, error = null)
                }
                throw cancelled
            } catch (_: Exception) {
                if (requestGeneration == generation && sessionState.snapshot.value.user?.id == userId) {
                    mutableState.value = mutableState.value.copy(
                        loading = false,
                        refreshing = false,
                        error = if (repository.hasSession) "No se han podido cargar los pedidos." else null
                    )
                }
                if (!repository.hasSession) throw IllegalStateException("La sesión de cuenta ha caducado.")
            }
        } finally {
            loadMutex.unlock()
        }
    }

    suspend fun refresh() = load(force = true)

    fun hasActiveSession(): Boolean = repository.hasSession

    fun findById(orderId: Int): AccountOrderSummary? =
        mutableState.value.orders.firstOrNull { it.id == orderId }

    fun resolveSelection(orderId: Int): StoreOrderSelectionResolution =
        resolveStoreOrderSelection(orderId, mutableState.value, loadedForUser == mutableState.value.authenticatedUserId)

    suspend fun resolveOrderById(orderId: Int): StoreOrderSelectionResolution {
        if (orderId <= 0) return StoreOrderSelectionResolution.NotFound
        findById(orderId)?.let { return StoreOrderSelectionResolution.Resolved(it) }
        val before = mutableState.value
        if (before.loading || before.refreshing) return StoreOrderSelectionResolution.Pending
        load(force = before.error != null)
        return resolveSelection(orderId)
    }

    suspend fun claimPendingOrderAndRefresh(): es.criosrango.shared.account.AccountClaimOrderResponse? {
        return claimAndRefreshIfSuccessful(
            claim = { repository.claimPendingOrder() },
            refresh = { refresh() }
        )
    }
}

enum class StoreOrderStatusKind { COMPLETED, PROCESSING, SHIPPED, PENDING, ON_HOLD, CANCELLED, REFUNDED, FAILED, UNKNOWN }

data class StoreOrderStatusStyle(val kind: StoreOrderStatusKind, val label: String, val background: Long, val foreground: Long)

object StoreOrderStatusMapper {
    fun normalize(value: String): String = value.lowercase()
        .replace("á", "a").replace("à", "a").replace("ä", "a")
        .replace("é", "e").replace("è", "e").replace("ë", "e")
        .replace("í", "i").replace("ì", "i").replace("ï", "i")
        .replace("ó", "o").replace("ò", "o").replace("ö", "o")
        .replace("ú", "u").replace("ù", "u").replace("ü", "u")
        .replace("ñ", "n").replace("&", " and ")
        .replace('-', ' ').replace('_', ' ')
        .replace(Regex("[^a-z0-9 ]"), " ")
        .replace(Regex("\\s+"), " ").trim()

    fun classify(status: String, label: String): StoreOrderStatusKind {
        val value = normalize("$status $label")
        fun has(vararg candidates: String) = candidates.any { value == it || value.contains(" $it ") || value.startsWith("$it ") || value.endsWith(" $it") }
        return when {
            has("completed", "completado") -> StoreOrderStatusKind.COMPLETED
            has("processing", "procesando", "preparacion") -> StoreOrderStatusKind.PROCESSING
            has("shipped", "enviado") -> StoreOrderStatusKind.SHIPPED
            has("pending", "pending payment", "pendiente") -> StoreOrderStatusKind.PENDING
            has("on hold", "en espera") -> StoreOrderStatusKind.ON_HOLD
            has("cancelled", "canceled", "cancelado") -> StoreOrderStatusKind.CANCELLED
            has("refunded", "reembolsado") -> StoreOrderStatusKind.REFUNDED
            has("failed", "fallido") -> StoreOrderStatusKind.FAILED
            else -> StoreOrderStatusKind.UNKNOWN
        }
    }

    fun visibleLabel(status: String, serverLabel: String): String = serverLabel.takeIf { it.isNotBlank() } ?: status

    fun style(status: String, label: String): StoreOrderStatusStyle = when (classify(status, label)) {
        StoreOrderStatusKind.COMPLETED -> StoreOrderStatusStyle(StoreOrderStatusKind.COMPLETED, visibleLabel(status, label), 0xFFE3F1E8, 0xFF285C3B)
        StoreOrderStatusKind.PROCESSING -> StoreOrderStatusStyle(StoreOrderStatusKind.PROCESSING, visibleLabel(status, label), 0xFFF4EAD3, 0xFF76591F)
        StoreOrderStatusKind.SHIPPED -> StoreOrderStatusStyle(StoreOrderStatusKind.SHIPPED, visibleLabel(status, label), 0xFFE4EFF8, 0xFF245579)
        StoreOrderStatusKind.PENDING -> StoreOrderStatusStyle(StoreOrderStatusKind.PENDING, visibleLabel(status, label), 0xFFFFF4D8, 0xFF765A16)
        StoreOrderStatusKind.ON_HOLD -> StoreOrderStatusStyle(StoreOrderStatusKind.ON_HOLD, visibleLabel(status, label), 0xFFECE9E3, 0xFF5E5A53)
        StoreOrderStatusKind.CANCELLED -> StoreOrderStatusStyle(StoreOrderStatusKind.CANCELLED, visibleLabel(status, label), 0xFFF8E3E1, 0xFF8A332D)
        StoreOrderStatusKind.REFUNDED -> StoreOrderStatusStyle(StoreOrderStatusKind.REFUNDED, "Reembolsado", 0xFFEDE7F3, 0xFF5A476D)
        StoreOrderStatusKind.FAILED -> StoreOrderStatusStyle(StoreOrderStatusKind.FAILED, visibleLabel(status, label), 0xFFF6E0DE, 0xFF8A332D)
        StoreOrderStatusKind.UNKNOWN -> StoreOrderStatusStyle(StoreOrderStatusKind.UNKNOWN, visibleLabel(status, label), 0xFFE9E9E7, 0xFF5C5C58)
    }
}

@Composable
fun StoreOrderStatusBadge(status: String, label: String) {
    val style = StoreOrderStatusMapper.style(status, label)
    Surface(shape = RoundedCornerShape(50), color = Color(style.background), contentColor = Color(style.foreground)) {
        Text(style.label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp))
    }
}

@Composable
private fun StoreOrdersHeader(title: String, onBack: () -> Unit) {
    StorePageHeader(
        title = title,
        onBack = onBack,
        horizontalPadding = 0.dp
    )
}

enum class StoreOrdersFilter { ALL, PROCESSING, CANCELLED, ON_HOLD, COMPLETED, REFUNDED }

fun filterStoreOrders(orders: List<AccountOrderSummary>, filter: StoreOrdersFilter): List<AccountOrderSummary> =
    when (filter) {
        StoreOrdersFilter.ALL -> orders
        StoreOrdersFilter.PROCESSING -> orders.filter { StoreOrderStatusMapper.classify(it.status, it.statusLabel) == StoreOrderStatusKind.PROCESSING }
        StoreOrdersFilter.CANCELLED -> orders.filter { StoreOrderStatusMapper.classify(it.status, it.statusLabel) == StoreOrderStatusKind.CANCELLED }
        StoreOrdersFilter.ON_HOLD -> orders.filter { StoreOrderStatusMapper.classify(it.status, it.statusLabel) in setOf(StoreOrderStatusKind.PENDING, StoreOrderStatusKind.ON_HOLD) }
        StoreOrdersFilter.COMPLETED -> orders.filter { StoreOrderStatusMapper.classify(it.status, it.statusLabel) == StoreOrderStatusKind.COMPLETED }
        StoreOrdersFilter.REFUNDED -> orders.filter { StoreOrderStatusMapper.classify(it.status, it.statusLabel) == StoreOrderStatusKind.REFUNDED }
    }

private val OrdersGreen = Color(0xFF183B35)

@Composable
private fun StoreOrdersFilterRow(selected: StoreOrdersFilter, onSelected: (StoreOrdersFilter) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp).horizontalScroll(rememberScrollState()).padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(StoreOrdersFilter.ALL to "Todos", StoreOrdersFilter.ON_HOLD to "En espera", StoreOrdersFilter.PROCESSING to "Procesando", StoreOrdersFilter.COMPLETED to "Completado", StoreOrdersFilter.REFUNDED to "Reembolsado", StoreOrdersFilter.CANCELLED to "Cancelado").forEach { (filter, label) ->
            val active = selected == filter
            Surface(modifier = Modifier.clickable { onSelected(filter) }, shape = RoundedCornerShape(50), color = if (active) OrdersGreen else Color.White, border = if (active) null else BorderStroke(1.dp, Color(0xFFD9DEDB))) {
                Text(label, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), color = if (active) Color.White else Color(0xFF29332F), fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun StoreOrderThumbnailStack(itemCount: Int) {
    Box(Modifier.size(width = 78.dp, height = 76.dp)) {
        if (itemCount >= 2) Surface(Modifier.size(62.dp).offset(x = 14.dp, y = 10.dp), shape = RoundedCornerShape(11.dp), color = Color(0xFFE7EFEB), border = BorderStroke(1.dp, Color(0xFFD8E2DD))) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.Image, contentDescription = null, tint = Color(0xFF9BAAA3), modifier = Modifier.size(24.dp)) }
        }
        Surface(Modifier.size(74.dp), shape = RoundedCornerShape(11.dp), color = Color(0xFFF1F3F1), border = BorderStroke(1.dp, Color(0xFFE0E4E1))) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.Image, contentDescription = "Imagen de producto no disponible", tint = Color(0xFFA3AAA5), modifier = Modifier.size(28.dp)) }
        }
    }
}

@Composable
private fun StoreOrderSummaryCard(
    order: AccountOrderSummary,
    onOpenOrder: (AccountOrderSummary) -> Unit,
    onRequestReturn: (AccountOrderSummary) -> Unit
) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE2E5E2)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StoreOrderThumbnailStack(order.items.size)
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Pedido #${order.number.ifBlank { order.id.toString() }}",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF202923)
                        )
                        StoreOrderStatusBadge(order.status, order.statusLabel)
                    }
                    val date = formatStoreOrderDate(order.dateCreated.orEmpty())
                    if (date.isNotBlank()) {
                        Text(
                            "Realizado el $date",
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF727A75),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                    if (order.total.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Total: ",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF303832)
                            )
                            Text(
                                formatStoreMinorUnits(storeOrderMinorUnits(order.total), order.currency),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF202923)
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = Color(0xFFE7E9E7), thickness = 1.dp)
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (storeOrderIsReturnEligible(order.status, order.statusLabel)) {
                    Text(
                        "↩ Solicitar devolución",
                        modifier = Modifier.clickable { onRequestReturn(order) }
                            .padding(vertical = 6.dp).padding(end = 8.dp),
                        color = OrdersGreen,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelLarge
                    )
                } else {
                    Spacer(Modifier.width(1.dp))
                }
                Row(
                    modifier = Modifier.clickable { onOpenOrder(order) }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text("Ver detalle", color = OrdersGreen, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                    Text("›", color = OrdersGreen, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
@Composable
private fun StoreOrdersEmptyState(message: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 38.dp, horizontal = 16.dp), contentAlignment = Alignment.Center) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun StoreOrdersScreen(owner: StoreOrdersOwner, sessionState: StoreAccountSessionState, onBack: () -> Unit, onOpenOrder: (AccountOrderSummary) -> Unit, onRequestReturn: (AccountOrderSummary) -> Unit, onSessionExpired: () -> Unit) {
    val state by owner.state.collectAsState()
    val auth by sessionState.snapshot.collectAsState()
    var selectedFilter by remember { mutableStateOf(StoreOrdersFilter.ALL) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(auth.user?.id) {
        selectedFilter = StoreOrdersFilter.ALL
        owner.onSessionChanged(auth.user?.id)
        if (auth.user != null) {
            try { owner.refresh() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (!owner.hasActiveSession()) onSessionExpired() }
        }
    }
    Column(Modifier.fillMaxSize().background(Color(0xFFFAFBFA)).padding(horizontal = 20.dp)) {
        StoreOrdersHeader(title = "Pedidos", onBack = onBack)
        StoreOrdersFilterRow(selectedFilter) { selectedFilter = it }
        when {
            auth.user == null -> Unit
            state.authenticatedUserId != auth.user?.id -> Box(Modifier.fillMaxWidth().padding(top = 36.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.loading && state.orders.isEmpty() -> Box(Modifier.fillMaxWidth().padding(top = 36.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.error != null && state.orders.isEmpty() -> Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(state.error.orEmpty(), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = { scope.launch { try { owner.refresh() } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { if (!owner.hasActiveSession()) onSessionExpired() } } }) { Text("Reintentar") }
            }
            else -> {
                val visibleOrders = filterStoreOrders(state.orders, selectedFilter)
                val emptyMessage = when (selectedFilter) {
                    StoreOrdersFilter.ALL -> "No tienes pedidos todavía."
                    StoreOrdersFilter.PROCESSING -> "No tienes pedidos procesando."
                    StoreOrdersFilter.CANCELLED -> "No tienes pedidos cancelados."
                    StoreOrdersFilter.ON_HOLD -> "No tienes pedidos en espera."
                    StoreOrdersFilter.COMPLETED -> "No tienes pedidos completados."
                    StoreOrdersFilter.REFUNDED -> "No tienes pedidos reembolsados."
                }
                if (visibleOrders.isEmpty()) StoreOrdersEmptyState(emptyMessage)
                else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 0.dp, end = 0.dp, top = 4.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(visibleOrders, key = { it.id }) { order -> StoreOrderSummaryCard(order, onOpenOrder, onRequestReturn) }
                }
            }
        }
    }
}

@Composable
fun StoreOrderDetailScreen(
    order: AccountOrderSummary,
    onBack: () -> Unit,
    onRequestReturn: (AccountOrderSummary) -> Unit
) {
    val address = order.shippingAddress
    val province = storeProvinceName(address.state)
    val city = if (address.city.equals("tarancon", true)) "Tarancón" else address.city
    val hasAddress = listOf(address.address1, address.address2, address.city, address.postcode, address.country).any { it.isNotBlank() }
    val shippingMinor = storeOrderMinorUnits(order.shippingTotal)
    val totalMinor = storeOrderMinorUnits(order.total)
    val subtotalMinor = storeOrderMinorUnits(order.subtotal)
    val hasSubtotal = order.subtotal.isNotBlank()
    val productsMinor = if (hasSubtotal) subtotalMinor else totalMinor - shippingMinor
    val vatMinor = if (hasSubtotal) totalMinor - subtotalMinor - shippingMinor else 0L
    val green = Color(0xFF285C3B)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        item {
            Box(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                StoreOrdersHeader(title = "Detalle de pedido", onBack = onBack)
            }
        }
        item {
            StoreOrderDetailCard {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        "Pedido #${order.number.ifBlank { order.id.toString() }}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    StoreOrderDetailStatusBadge(order.status, order.statusLabel)
                    val date = formatStoreOrderDate(order.dateCreated.orEmpty())
                    if (date.isNotBlank()) {
                        Text(
                            "Realizado el $date",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (order.total.isNotBlank()) {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 1.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Total:", style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.width(7.dp))
                            Text(
                                formatStoreMinorUnits(totalMinor, order.currency),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
        item {
            StoreOrderDetailCard(title = "Productos (${order.items.size})") {
                Surface(Modifier.fillMaxWidth().height(1.dp), color = Color(0xFFEAEAEA)) {}
                Spacer(Modifier.height(6.dp))
                if (order.items.isEmpty()) {
                    Text("No hay productos disponibles.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    order.items.forEachIndexed { index, item ->
                        if (index > 0) {
                            Spacer(Modifier.height(6.dp))
                            Surface(Modifier.fillMaxWidth().height(1.dp), color = Color(0xFFEAEAEA)) {}
                            Spacer(Modifier.height(6.dp))
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            Surface(
                                modifier = Modifier.size(68.dp),
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFF3F4F2)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Filled.Image,
                                        contentDescription = "Imagen de producto no disponible",
                                        tint = Color(0xFF9A9D98),
                                        modifier = Modifier.size(27.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    cleanStoreWooText(item.name),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                val variations = storeOrderVariations(item)
                                if (variations.isNotBlank()) {
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        variations,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "Cantidad: ${item.quantity}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
        item {
            StoreOrderDetailCard(
                title = "Entrega",
                icon = { Icon(Icons.Filled.LocalShipping, null, tint = green, modifier = Modifier.size(18.dp)) }
            ) {
                if (!hasAddress) {
                    Text("No hay dirección de entrega disponible.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    if (address.address1.isNotBlank()) Text(cleanStoreWooText(address.address1), style = MaterialTheme.typography.bodySmall)
                    if (address.address2.isNotBlank()) Text(cleanStoreWooText(address.address2), style = MaterialTheme.typography.bodySmall)
                    val location = listOf(address.postcode, city, province).filter { it.isNotBlank() }.joinToString(" · ")
                    if (location.isNotBlank()) Text(location, style = MaterialTheme.typography.bodySmall)
                    if (address.country.isNotBlank()) {
                        Text(
                            if (address.country.equals("ES", true)) "España" else address.country,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                if (order.shippingTotal.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (shippingMinor == 0L) "Envío gratuito" else formatStoreMinorUnits(shippingMinor, order.currency),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = green
                    )
                }
            }
        }
        item {
            StoreOrderDetailCard(
                title = "Pago",
                icon = { Icon(Icons.Filled.CreditCard, null, tint = green, modifier = Modifier.size(18.dp)) },
                compact = true
            ) {
                val payment = storeFriendlyPaymentMethod(order.paymentMethodTitle, order.paymentMethod)
                if (payment.isNotBlank()) {
                    Text(payment, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                }
            }
        }
        item {
            StoreOrderDetailCard(
                title = "Resumen",
                icon = { Icon(Icons.Filled.ReceiptLong, null, tint = green, modifier = Modifier.size(18.dp)) }
            ) {
                StoreOrderSummaryLine("Productos", formatStoreMinorUnits(productsMinor, order.currency))
                if (vatMinor > 0L) {
                    StoreOrderSummaryLine("IVA", formatStoreMinorUnits(vatMinor, order.currency))
                }
                if (order.shippingTotal.isNotBlank()) {
                    StoreOrderSummaryLine(
                        "Envío",
                        if (shippingMinor == 0L) "Gratis" else formatStoreMinorUnits(shippingMinor, order.currency)
                    )
                }
                Spacer(Modifier.height(2.dp))
                Surface(Modifier.fillMaxWidth().height(1.dp), color = Color(0xFFE6E6E6)) {}
                Row(
                    Modifier.fillMaxWidth().padding(top = 5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Total", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        formatStoreMinorUnits(totalMinor, order.currency),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        if (storeOrderIsReturnEligible(order.status, order.statusLabel)) {
            item {
                StoreOrderDetailCard(compact = true) {
                    Text("¿Necesitas devolver algo?", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(3.dp))
                    OutlinedButton(
                        onClick = { onRequestReturn(order) },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Solicitar devolución")
                    }
                }
            }
        }
    }
}

@Composable
private fun StoreOrderDetailStatusBadge(status: String, label: String) {
    val style = StoreOrderStatusMapper.style(status, label)
    Surface(
        shape = RoundedCornerShape(50),
        color = Color(style.background),
        contentColor = Color(style.foreground)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(13.dp))
            Text(style.label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}


@Composable
private fun StoreOrderDetailCard(
    title: String? = null,
    icon: (@Composable () -> Unit)? = null,
    compact: Boolean = false,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(0.7.dp, Color(0xFFE7E8E6)),
        elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = if (compact) 10.dp else 12.dp)) {
            if (title != null && icon != null) {
                Row(verticalAlignment = Alignment.Top) {
                    icon()
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(if (compact) 2.dp else 4.dp))
                        content()
                    }
                }
            } else if (title != null) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(if (compact) 3.dp else 6.dp))
                content()
            } else {
                content()
            }
        }
    }
}

@Composable
private fun StoreOrderSummaryLine(label: String, value: String, subdued: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = if (subdued) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.width(10.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

private fun storeFriendlyPaymentMethod(title: String, method: String): String {
    val source = "$title $method".trim()
    if (source.isBlank()) return ""
    return when {
        source.contains("bizum", true) -> "Bizum"
        source.contains("tarjeta", true) || source.contains("card", true) ||
            source.contains("credit", true) || source.contains("debit", true) ||
            source.contains("ceca", true) || source.contains("redsys", true) ||
            source.contains("stripe", true) -> "Tarjeta"
        title.isNotBlank() && title.any { it.isWhitespace() } -> cleanStoreWooText(title)
        else -> "Pago"
    }
}

private fun storeOrderVariations(item: AccountOrderItem): String = item.variations
    .filter { it.name.isNotBlank() && it.value.isNotBlank() }
    .joinToString(" · ") { "${if (it.name.equals("Tallas", true)) "Talla" else it.name}: ${cleanStoreWooText(it.value)}" }

private fun cleanStoreWooText(value: String): String = value
    .replace("&amp;", "&").replace("&#038;", "&").replace("&quot;", "\"")
    .replace("&#8217;", "’").replace("&rsquo;", "’").replace("&nbsp;", " ").trim()

private fun formatStoreOrderDate(value: String): String {
    val raw = value.take(10).split("-")
    return if (raw.size == 3 && raw[0].length == 4) "${raw[2]}-${raw[1]}-${raw[0]}" else value.take(10)
}

internal fun storeOrderMinorUnits(value: String): Long {
    val raw = value.trim().replace(",", ".")
    if (raw.isBlank()) return 0L
    val negative = raw.startsWith("-")
    val unsigned = raw.removePrefix("-").removePrefix("+")
    val parts = unsigned.split(".", limit = 2)
    val whole = parts[0].filter { it in '0'..'9' }.toLongOrNull() ?: return 0L
    val fraction = parts.getOrElse(1) { "" }.filter { it in '0'..'9' }
    val firstTwo = (fraction + "00").take(2).toLongOrNull() ?: 0L
    val rounded = fraction.length > 2 && fraction[2] >= '5'
    val cents = whole * 100L + firstTwo + if (rounded) 1L else 0L
    return if (negative) -cents else cents
}

internal fun formatStoreMinorUnits(value: Long, currency: String): String {
    val absolute = kotlin.math.abs(value)
    val amount = (if (value < 0) "-" else "") + (absolute / 100L).toString() + "," + (absolute % 100L).toString().padStart(2, '0')
    return if (currency.equals("EUR", true)) "$amount €" else "$amount $currency"
}

private fun formatStoreAmount(value: String, currency: String): String {
    val amount = value.replace('.', ',')
    return if (currency.equals("EUR", true)) "$amount €" else "$amount $currency"
}


private fun storeProvinceName(code: String): String = listOf(
    "C" to "A Coruña", "VI" to "Araba/Álava", "AB" to "Albacete", "A" to "Alicante", "AL" to "Almería",
    "O" to "Asturias", "AV" to "Ávila", "BA" to "Badajoz", "B" to "Barcelona", "BI" to "Bizkaia",
    "BU" to "Burgos", "CC" to "Cáceres", "CA" to "Cádiz", "S" to "Cantabria", "CS" to "Castellón",
    "CE" to "Ceuta", "CR" to "Ciudad Real", "CO" to "Córdoba", "CU" to "Cuenca", "GI" to "Girona",
    "GR" to "Granada", "GU" to "Guadalajara", "SS" to "Gipuzkoa", "H" to "Huelva", "HU" to "Huesca",
    "J" to "Jaén", "LE" to "León", "L" to "Lleida", "LO" to "La Rioja", "LU" to "Lugo",
    "M" to "Madrid", "MA" to "Málaga", "ML" to "Melilla", "MU" to "Murcia", "NA" to "Navarra",
    "OR" to "Ourense", "P" to "Palencia", "GC" to "Las Palmas", "PO" to "Pontevedra", "SA" to "Salamanca",
    "TF" to "Santa Cruz de Tenerife", "SG" to "Segovia", "SE" to "Sevilla", "SO" to "Soria", "T" to "Tarragona",
    "TE" to "Teruel", "TO" to "Toledo", "V" to "Valencia", "VA" to "Valladolid", "ZA" to "Zamora", "Z" to "Zaragoza"
).firstOrNull { it.first.equals(code, true) }?.second ?: code

