package com.example.posmobile.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.posmobile.data.Container
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AuthState { Loading, LoggedOut, LoggedIn }

/** The outlet (property + POS location) the user is currently ordering for. */
data class Outlet(
    val propertySlug: String,
    val propertyName: String,
    val locationId: String,
    val locationName: String,
)

/** Activity-scoped session: auth gate + selected outlet. */
class SessionViewModel : ViewModel() {
    private val auth = Container.auth
    private val settings = Container.settings

    var authState by mutableStateOf(AuthState.Loading)
        private set
    var selectedOutlet by mutableStateOf<Outlet?>(null)
        private set

    init { resume() }

    /** On launch / config change: try a silent refresh via the stored cookie. */
    fun resume() {
        authState = AuthState.Loading
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                settings.isConfigured && auth.ensureAccessToken() != null
            }
            authState = if (ok) AuthState.LoggedIn else AuthState.LoggedOut
        }
    }

    suspend fun login(email: String, password: String) {
        auth.login(email, password)
        selectedOutlet = null
        authState = AuthState.LoggedIn
    }

    fun logout() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { auth.logout() }
            selectedOutlet = null
            authState = AuthState.LoggedOut
        }
    }

    fun selectOutlet(outlet: Outlet) { selectedOutlet = outlet }
    fun clearOutlet() { selectedOutlet = null }
}
