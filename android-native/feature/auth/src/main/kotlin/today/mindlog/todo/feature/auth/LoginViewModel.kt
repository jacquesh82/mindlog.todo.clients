package today.mindlog.todo.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import today.mindlog.todo.core.data.AuthProviders
import today.mindlog.todo.core.data.AuthRepository
import today.mindlog.todo.core.data.OAuthCallback
import today.mindlog.todo.core.data.OAuthCallbackBus
import javax.inject.Inject

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    val providers: AuthProviders = AuthProviders(),
    /** Non-null once mindlog id returned an account without an email. */
    val pendingToken: String? = null,
    val pendingEmail: String = "",
) {
    val canSubmit: Boolean get() = !busy && email.isNotBlank() && password.isNotBlank()
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val callbackBus: OAuthCallbackBus,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    init {
        // Ask the server which sign-in paths it has configured before drawing
        // any of their buttons.
        viewModelScope.launch {
            _state.update { it.copy(providers = authRepository.authProviders()) }
        }
        viewModelScope.launch {
            callbackBus.callbacks.collect(::onOAuthCallback)
        }
    }

    fun onEmailChange(value: String) = _state.update { it.copy(email = value, error = null) }
    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, error = null) }
    fun onPendingEmailChange(value: String) = _state.update { it.copy(pendingEmail = value) }

    fun signIn() {
        val current = _state.value
        if (!current.canSubmit) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            authRepository.login(current.email.trim(), current.password)
                .onFailure { e -> _state.update { it.copy(busy = false, error = e.readable()) } }
            // On success SessionStore flips `signedIn` and the graph navigates;
            // there is nothing left for this screen to do.
        }
    }

    /** Returns the URL for the Custom Tab, or null if the provider is off. */
    suspend fun mindlogIdUrl(create: Boolean): String? =
        if (_state.value.providers.mindlogId) authRepository.mindlogIdAuthUrl(create) else null

    fun submitPendingEmail() {
        val current = _state.value
        val token = current.pendingToken ?: return
        if (current.pendingEmail.isBlank()) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            authRepository.completeMindlogId(token, current.pendingEmail.trim())
                .onFailure { e -> _state.update { it.copy(busy = false, error = e.readable()) } }
        }
    }

    private fun onOAuthCallback(callback: OAuthCallback) {
        when (callback) {
            is OAuthCallback.Tokens -> viewModelScope.launch {
                authRepository.adoptTokens(callback.accessToken, callback.refreshToken)
            }
            is OAuthCallback.NeedsEmail ->
                _state.update { it.copy(pendingToken = callback.pendingToken, busy = false) }
            is OAuthCallback.Failed ->
                _state.update { it.copy(busy = false, error = callback.reason) }
        }
    }
}

private fun Throwable.readable(): String = when (this) {
    is retrofit2.HttpException -> when (code()) {
        401 -> "Incorrect email or password."
        429 -> "Too many attempts. Try again shortly."
        else -> "The server refused the request (${code()})."
    }
    is java.io.IOException -> "Cannot reach the server."
    else -> message ?: "Something went wrong."
}
