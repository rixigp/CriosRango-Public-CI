package es.criosrango.shared

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardActions
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import es.criosrango.shared.account.AccountRepository
import es.criosrango.shared.account.AccountRequestRejectedException
import es.criosrango.shared.account.AccountSessionPersistenceException
import es.criosrango.shared.account.AccountUser
import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.SerializationException

private const val FORGOT_SUCCESS_COPY =
    "Si existe una cuenta asociada a ese correo electrónico, te enviaremos un enlace para restablecer tu contraseña"

data class StoreRegisterState(
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val phone: String = "",
    val birthDate: String? = null,
    val password: String = "",
    val repeatPassword: String = "",
    val validationErrors: Map<String, String> = emptyMap(),
    val submitting: Boolean = false,
    val requestError: String? = null,
    val success: AccountUser? = null
)

class StoreRegisterOwner(private val repository: AccountRepository) {
    private val gate = Mutex()
    private val _state = MutableStateFlow(StoreRegisterState())
    val state: StateFlow<StoreRegisterState> = _state.asStateFlow()

    fun update(transform: (StoreRegisterState) -> StoreRegisterState) {
        if (!_state.value.submitting) {
            _state.value = transform(_state.value).copy(requestError = null, success = null, validationErrors = emptyMap())
        }
    }

    fun reset(initialEmail: String = "") {
        if (!_state.value.submitting) _state.value = StoreRegisterState(email = initialEmail)
    }

    fun validate(value: StoreRegisterState = _state.value): Map<String, String> = buildMap {
        if (value.firstName.isBlank()) put("firstName", "Introduce tu nombre.")
        if (value.lastName.isBlank()) put("lastName", "Introduce tus apellidos.")
        if (value.email.isBlank()) put("email", "Introduce tu correo electrónico.")
        if (value.password.length < 8) put("password", "La contraseña debe tener al menos 8 caracteres.")
        if (value.repeatPassword != value.password) put("repeatPassword", "Las contraseñas no coinciden.")
    }

    suspend fun submit(): AccountUser? {
        val initial = _state.value
        val validation = validate(initial)
        if (validation.isNotEmpty()) {
            _state.value = initial.copy(validationErrors = validation, requestError = null, success = null)
            return null
        }
        if (!gate.tryLock()) return null
        try {
            val value = _state.value
            if (value.submitting) return null
            _state.value = value.copy(submitting = true, validationErrors = emptyMap(), requestError = null, success = null)
            return try {
                val user = repository.register(
                    email = value.email.trim(),
                    password = value.password,
                    firstName = value.firstName.trim(),
                    lastName = value.lastName.trim(),
                    phone = value.phone.trim(),
                    birthDate = value.birthDate
                )
                _state.value = _state.value.copy(success = user)
                user
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: AccountSessionPersistenceException) {
                _state.value = _state.value.copy(requestError = "La cuenta puede haberse creado, pero no se ha podido guardar la sesión. No vuelvas a registrarte; intenta iniciar sesión.")
                null
            } catch (exception: ResponseException) {
                _state.value = _state.value.copy(requestError = when (exception.response.status.value) {
                    400 -> "Revisa los datos introducidos."
                    409 -> "Ya existe una cuenta con ese correo electrónico."
                    429 -> "Demasiados intentos. Espera unos minutos."
                    else -> "No hemos podido crear la cuenta."
                })
                null
            } catch (_: SerializationException) {
                _state.value = _state.value.copy(requestError = "No hemos podido crear la cuenta.")
                null
            } catch (_: Exception) {
                _state.value = _state.value.copy(requestError = "No hemos podido crear la cuenta.")
                null
            }
        } finally {
            _state.value = _state.value.copy(submitting = false)
            gate.unlock()
        }
    }
}

data class StoreForgotPasswordState(
    val email: String = "",
    val submitting: Boolean = false,
    val validationError: String? = null,
    val requestError: String? = null,
    val success: Boolean = false
)

class StoreForgotPasswordOwner(private val repository: AccountRepository) {
    private val gate = Mutex()
    private val _state = MutableStateFlow(StoreForgotPasswordState())
    val state: StateFlow<StoreForgotPasswordState> = _state.asStateFlow()

    fun updateEmail(value: String) {
        if (!_state.value.submitting) _state.value = _state.value.copy(
            email = value, validationError = null, requestError = null, success = false
        )
    }

    fun reset(initialEmail: String = "") {
        if (!_state.value.submitting) _state.value = StoreForgotPasswordState(email = initialEmail)
    }

    suspend fun submit(): Boolean {
        val current = _state.value
        if (current.email.isBlank()) {
            _state.value = current.copy(validationError = "Introduce tu correo electrónico.", requestError = null, success = false)
            return false
        }
        if (!gate.tryLock()) return false
        try {
            val value = _state.value
            if (value.submitting) return false
            _state.value = value.copy(submitting = true, validationError = null, requestError = null, success = false)
            return try {
                repository.forgotPassword(value.email.trim())
                _state.value = _state.value.copy(success = true)
                true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (exception: ResponseException) {
                _state.value = _state.value.copy(requestError = when (exception.response.status.value) {
                    429 -> "Demasiadas solicitudes. Espera unos minutos."
                    else -> "No hemos podido solicitar el cambio de contraseña."
                })
                false
            } catch (_: AccountRequestRejectedException) {
                _state.value = _state.value.copy(requestError = "No hemos podido solicitar el cambio de contraseña.")
                false
            } catch (_: Exception) {
                _state.value = _state.value.copy(requestError = "No hemos podido solicitar el cambio de contraseña.")
                false
            }
        } finally {
            _state.value = _state.value.copy(submitting = false)
            gate.unlock()
        }
    }
}

@Composable
fun StoreRegisterScreen(
    owner: StoreRegisterOwner,
    initialEmail: String = "",
    onDismiss: () -> Unit,
    onRegistered: (AccountUser) -> Unit
) {
    val state by owner.state.collectAsState()
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(owner) { owner.reset(initialEmail) }
    LaunchedEffect(state.success) { state.success?.let(onRegistered) }
    AlertDialog(
        onDismissRequest = { if (!state.submitting) onDismiss() },
        title = { Text("Crear cuenta") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).pointerInput(focusManager, keyboard) { detectTapGestures(onTap = { focusManager.clearFocus(); keyboard?.hide() }) }) {
                OutlinedTextField(state.firstName, { owner.update { s -> s.copy(firstName = it) } }, label = { Text("Nombre") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(state.lastName, { owner.update { s -> s.copy(lastName = it) } }, label = { Text("Apellidos") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(state.email, { owner.update { s -> s.copy(email = it) } }, label = { Text("Correo electrónico") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(state.phone, { owner.update { s -> s.copy(phone = it) } }, label = { Text("Teléfono (opcional)") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                BirthDatePickerField(state.birthDate, { owner.update { s -> s.copy(birthDate = it) } }, modifier = Modifier.fillMaxWidth(), enabled = !state.submitting)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(state.password, { owner.update { s -> s.copy(password = it) } }, label = { Text("Contraseña") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(state.repeatPassword, { owner.update { s -> s.copy(repeatPassword = it) } }, label = { Text("Repetir contraseña") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus(); keyboard?.hide() }))
                if (state.repeatPassword.isNotEmpty() && state.password != state.repeatPassword) {
                    Spacer(Modifier.height(8.dp))
                    Text("Las contraseñas no coinciden.", color = MaterialTheme.colorScheme.error)
                }
                state.requestError?.let { Spacer(Modifier.height(8.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = { scope.launch { owner.submit() } }, enabled = !state.submitting && owner.validate(state).isEmpty()) {
                if (state.submitting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Crear cuenta")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !state.submitting) { Text("Cancelar") } }
    )
}

@Composable
fun StoreForgotPasswordScreen(
    owner: StoreForgotPasswordOwner,
    initialEmail: String = "",
    onDismiss: () -> Unit
) {
    val state by owner.state.collectAsState()
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(owner) { owner.reset(initialEmail) }
    AlertDialog(
        onDismissRequest = { if (!state.submitting) onDismiss() },
        title = { Text("Recuperar contraseña") },
        text = {
            Column(Modifier.pointerInput(focusManager, keyboard) { detectTapGestures(onTap = { focusManager.clearFocus(); keyboard?.hide() }) }) {
                Text("Introduce tu correo electrónico")
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(state.email, owner::updateEmail, label = { Text("Correo electrónico") }, singleLine = true, enabled = !state.submitting, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus(); keyboard?.hide() }))
                state.validationError?.let { Spacer(Modifier.height(8.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
                state.requestError?.let { Spacer(Modifier.height(8.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
                if (state.success) { Spacer(Modifier.height(8.dp)); Text(FORGOT_SUCCESS_COPY) }
            }
        },
        confirmButton = {
            if (state.success) TextButton(onClick = onDismiss) { Text("Cerrar") }
            else TextButton(onClick = { scope.launch { owner.submit() } }, enabled = !state.submitting && state.email.isNotBlank()) {
                if (state.submitting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Enviar correo")
            }
        },
        dismissButton = { if (!state.success) TextButton(onClick = onDismiss, enabled = !state.submitting) { Text("Cancelar") } }
    )
}
