package es.criosrango.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import es.criosrango.shared.account.AccountCustomerAddress
import es.criosrango.shared.account.AccountRepository
import es.criosrango.shared.account.AccountUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Account-specific validation. Deliberately independent from checkout's geographic rules. */
object StoreAccountFormValidation {
    val spanishProvinceCodes: Set<String> = setOf(
        "C","VI","AB","A","AL","O","AV","BA","B","BI","BU","CC","CA","S","CS","CE","CR","CO","CU","GI","GR","GU","SS","H","HU","J","LE","L","LO","LU","M","MA","ML","MU","NA","OR","P","GC","PO","SA","TF","SG","SE","SO","T","TE","TO","V","VA","ZA","Z"
    )

    fun profileError(firstName: String, lastName: String): String? =
        if (firstName.trim().isBlank() || lastName.trim().isBlank()) "Introduce tu nombre y apellidos." else null

    fun mergeProfileNames(address: AccountCustomerAddress, firstName: String, lastName: String): AccountCustomerAddress =
        address.copy(firstName = firstName.trim(), lastName = lastName.trim())

    fun cleanAddress(address: AccountCustomerAddress): AccountCustomerAddress = address.copy(
        firstName = address.firstName.trim(),
        lastName = address.lastName.trim(),
        address1 = address.address1.trim(),
        address2 = address.address2.trim(),
        postcode = address.postcode.trim(),
        city = address.city.trim(),
        state = address.state.trim().uppercase(),
        country = "ES"
    )

    fun addressError(address: AccountCustomerAddress): String? {
        val clean = cleanAddress(address)
        if (clean.firstName.isBlank() || clean.lastName.isBlank() || clean.address1.isBlank() ||
            clean.postcode.isBlank() || clean.city.isBlank() || clean.state.isBlank()
        ) return "Completa todos los campos obligatorios."
        if (clean.postcode.length != 5 || clean.postcode.any { it !in '0'..'9' }) return "Introduce un código postal válido."
        if (clean.state !in spanishProvinceCodes) return "Selecciona una provincia."
        return null
    }
}

/** One operation owner shared by Android and iOS profile forms. */
class StoreProfileOwner(
    private val repository: AccountRepository,
    private val sessionState: StoreAccountSessionState
) {
    private var inFlight = false

    @Synchronized private fun acquire(): Boolean {
        if (inFlight) return false
        inFlight = true
        return true
    }
    @Synchronized private fun release() { inFlight = false }

    suspend fun save(user: AccountUser, firstName: String, lastName: String): AccountUser? {
        if (!acquire()) return null
        try {
            val first = firstName.trim()
            val last = lastName.trim()
            require(StoreAccountFormValidation.profileError(first, last) == null) {
                "Introduce tu nombre y apellidos."
            }
            // Preserve every address field not edited by Profile; this remains the existing save contract.
            val address = repository.customerAddress()
            repository.saveCustomerAddress(StoreAccountFormValidation.mergeProfileNames(address, first, last))
            val updated = user.copy(
                firstName = first,
                lastName = last,
                displayName = listOf(first, last).filter { it.isNotBlank() }.joinToString(" ").ifBlank { user.displayName }
            )
            sessionState.publishAuthenticatedUser(updated)
            return updated
        } finally {
            release()
        }
    }
}

data class StoreAddressSnapshot(
    val address: AccountCustomerAddress = AccountCustomerAddress(),
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
    val loaded: Boolean = false
)

/** Stable address owner: cached state prevents remounts from showing a stale local copy. */
class StoreAddressOwner(private val repository: AccountRepository) {
    private val mutableSnapshot = MutableStateFlow(StoreAddressSnapshot())
    val snapshot: StateFlow<StoreAddressSnapshot> = mutableSnapshot.asStateFlow()
    private var loadStarted = false
    private var inFlight = false

    @Synchronized private fun acquire(): Boolean {
        if (inFlight) return false
        inFlight = true
        return true
    }
    @Synchronized private fun release() { inFlight = false }

    suspend fun load(force: Boolean = false) {
        synchronized(this) {
            if (loadStarted && !force) return
            loadStarted = true
        }
        mutableSnapshot.value = mutableSnapshot.value.copy(loading = true, error = null)
        try {
            val address = repository.customerAddress()
            mutableSnapshot.value = StoreAddressSnapshot(address = address, loading = false, loaded = true)
        } catch (failure: Exception) {
            mutableSnapshot.value = mutableSnapshot.value.copy(
                loading = false, loaded = true,
                error = failure.message ?: "No se ha podido cargar la dirección."
            )
            if (!repository.hasSession) throw failure
        }
    }

    fun edit(address: AccountCustomerAddress) {
        mutableSnapshot.value = mutableSnapshot.value.copy(address = address, error = null, notice = null)
    }

    suspend fun save(): Boolean {
        if (!acquire()) return false
        try {
            val clean = StoreAccountFormValidation.cleanAddress(mutableSnapshot.value.address)
            val validation = StoreAccountFormValidation.addressError(clean)
            if (validation != null) {
                mutableSnapshot.value = mutableSnapshot.value.copy(error = validation, notice = null)
                return false
            }
            mutableSnapshot.value = mutableSnapshot.value.copy(saving = true, error = null, notice = null)
            repository.saveCustomerAddress(clean)
            mutableSnapshot.value = mutableSnapshot.value.copy(
                address = clean, saving = false, loaded = true, notice = "Dirección actualizada", error = null
            )
            return true
        } catch (failure: Exception) {
            mutableSnapshot.value = mutableSnapshot.value.copy(
                saving = false, error = failure.message ?: "No se ha podido guardar la dirección.", notice = null
            )
            if (!repository.hasSession) throw failure
            return false
        } finally {
            release()
        }
    }
}

@Composable
fun StoreProfileScreen(
    repository: AccountRepository,
    sessionState: StoreAccountSessionState,
    user: AccountUser?,
    onUserChanged: (AccountUser) -> Unit,
    onSessionExpired: () -> Unit,
    onBack: () -> Unit
) {
    val owner = remember(repository, sessionState) { StoreProfileOwner(repository, sessionState) }
    val scope = rememberCoroutineScope()
    var firstName by remember(user?.id) { mutableStateOf(user?.firstName.orEmpty()) }
    var lastName by remember(user?.id) { mutableStateOf(user?.lastName.orEmpty()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.Top) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Datos personales", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            TextButton(onClick = onBack) { Text("Volver") }
        }
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(firstName, { firstName = it; error = null }, label = { Text("Nombre") }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(lastName, { lastName = it; error = null }, label = { Text("Apellidos") }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(user?.email.orEmpty().ifBlank { "—" }, {}, label = { Text("Correo electrónico") }, readOnly = true, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(formatBirthDateForDisplay(user?.birthDate).ifBlank { "No indicada" }, {}, label = { Text("Fecha de nacimiento") }, readOnly = true, singleLine = true, modifier = Modifier.fillMaxWidth())
        notice?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(20.dp))
        Button(
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                if (busy) return@Button
                val validation = StoreAccountFormValidation.profileError(firstName, lastName)
                if (validation != null) { error = validation; notice = null; return@Button }
                busy = true; error = null; notice = null
                scope.launch {
                    try {
                        val saved = owner.save(requireNotNull(user) { "No hay una sesión de cuenta activa." }, firstName, lastName)
                        if (saved != null) { onUserChanged(saved); notice = "Datos actualizados" }
                    } catch (failure: Exception) {
                        if (!repository.hasSession) onSessionExpired()
                        else error = failure.message ?: "No se han podido guardar los datos."
                    } finally { busy = false }
                }
            }
        ) { if (busy) CircularProgressIndicator() else Text("Guardar cambios") }
    }
}

@Composable
fun StoreAddressScreen(
    repository: AccountRepository,
    addressOwner: StoreAddressOwner,
    onSessionExpired: () -> Unit,
    onBack: () -> Unit
) {
    val snapshot by addressOwner.snapshot.collectAsState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(addressOwner) {
        try { addressOwner.load() }
        catch (_: Exception) { if (!repository.hasSession) onSessionExpired() }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.Top) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Dirección de entrega", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            TextButton(onClick = onBack) { Text("Volver") }
        }
        if (snapshot.loading && !snapshot.loaded) {
            CircularProgressIndicator()
        } else {
            val a = snapshot.address
            @Composable fun field(label: String, value: String, change: (String) -> Unit) {
                OutlinedTextField(value, change, label = { Text(label) }, singleLine = true, enabled = !snapshot.saving, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
            }
            field("Nombre", a.firstName) { addressOwner.edit(a.copy(firstName = it)) }
            field("Apellidos", a.lastName) { addressOwner.edit(a.copy(lastName = it)) }
            field("Dirección", a.address1) { addressOwner.edit(a.copy(address1 = it)) }
            field("Dirección adicional / Piso, puerta, etc. (opcional)", a.address2) { addressOwner.edit(a.copy(address2 = it)) }
            field("Código postal", a.postcode) { addressOwner.edit(a.copy(postcode = it)) }
            field("Ciudad", a.city) { addressOwner.edit(a.copy(city = it)) }
            field("Provincia", a.state) { addressOwner.edit(a.copy(state = it)) }
            field("País", "España") {}
            snapshot.notice?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            snapshot.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                enabled = !snapshot.saving,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    if (snapshot.saving) return@Button
                    scope.launch {
                        try { addressOwner.save() }
                        catch (_: Exception) { if (!repository.hasSession) onSessionExpired() }
                    }
                }
            ) { if (snapshot.saving) CircularProgressIndicator() else Text("Guardar dirección") }
        }
    }
}
