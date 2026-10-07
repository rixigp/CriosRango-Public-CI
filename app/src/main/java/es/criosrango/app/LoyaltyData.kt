package es.criosrango.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import es.criosrango.shared.loyalty.LoyaltyApiException
import es.criosrango.shared.loyalty.LoyaltyRedeemOption
import es.criosrango.shared.loyalty.LoyaltyRepository
import es.criosrango.shared.loyalty.LoyaltyReward
import es.criosrango.shared.loyalty.LoyaltyWallet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LoyaltyViewModel(application: android.app.Application) : AndroidViewModel(application) {
    private val repository = LoyaltyRepository(AccountSessionStore(application))

    private val _wallet = MutableStateFlow<LoyaltyWallet?>(null)
    val wallet = _wallet.asStateFlow()
    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private var pendingPoints: Int? = null
    private var pendingRequestId: String? = null

    fun refresh() {
        if (_loading.value) return
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                _wallet.value = repository.getWallet()
            } catch (exception: Exception) {
                _error.value = exception.message ?: "No se ha podido cargar el monedero."
            } finally {
                _loading.value = false
            }
        }
    }

    fun redeem(points: Int, applyCoupon: suspend (String) -> Boolean) {
        if (_loading.value) return
        val requestId = if (pendingPoints == points && !pendingRequestId.isNullOrBlank()) {
            pendingRequestId!!
        } else {
            pendingPoints = points
            repository.newRequestId().also { pendingRequestId = it }
        }
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                val response = repository.redeem(points, requestId)
                _wallet.value = repository.getWallet()
                val coupon = response.coupon
                if (coupon == null) {
                    _error.value = "El canje no ha devuelto un cupón utilizable."
                } else if (!applyCoupon(coupon.code)) {
                    _error.value = "El crédito se ha creado, pero no se ha podido aplicar al carrito. Puedes intentarlo de nuevo desde el crédito pendiente."
                    _wallet.value = repository.getWallet()
                }
                pendingPoints = null
                pendingRequestId = null
            } catch (exception: LoyaltyApiException) {
                _error.value = exception.message
                // pendingRequestId deliberately remains unchanged so a retry reuses it.
            } catch (exception: Exception) {
                _error.value = exception.message ?: "No se ha podido utilizar el monedero."
                // pendingRequestId deliberately remains unchanged so a retry reuses it.
            } finally {
                _loading.value = false
            }
        }
    }

    fun applyPending(reward: LoyaltyReward, applyCoupon: suspend (String) -> Boolean) {
        if (_loading.value) return
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                if (!applyCoupon(reward.code)) {
                    _error.value = "No se ha podido aplicar el crédito. Puedes volver a intentarlo."
                }
                _wallet.value = repository.getWallet()
            } catch (exception: Exception) {
                _error.value = exception.message ?: "No se ha podido aplicar el crédito."
            } finally {
                _loading.value = false
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun hasPendingRedeem(): Boolean = pendingRequestId != null
}

@Composable
internal fun LoyaltyRedeemDialog(
    options: List<LoyaltyRedeemOption>,
    loading: Boolean,
    onDismiss: () -> Unit,
    onSelect: (LoyaltyRedeemOption) -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text("¿Cuánto quieres utilizar?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { option ->
                    Button(
                        onClick = { onSelect(option) },
                        enabled = !loading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (option.isMaximum) "Máximo: " + option.value + " €" else option.value + " €")
                    }
                }
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.padding(top = 4.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, enabled = !loading) { Text("Cancelar") }
        }
    )
}

internal fun loyaltyDisplayMoney(value: String): String = value.replace('.', ',') + " €"
