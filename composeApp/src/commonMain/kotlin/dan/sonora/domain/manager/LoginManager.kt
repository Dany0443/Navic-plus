package dan.sonora.domain.manager

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import dan.sonora.domain.repositories.DbRepository
import dan.sonora.ui.core.LoginUiState

class LoginManager(
    private val repository: DbRepository,
    private val sessionManager: SessionManager,
    private val syncManager: SyncManager
) {
	val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

	val loginState: StateFlow<LoginUiState>
		field = MutableStateFlow<LoginUiState>(LoginUiState.Idle)

	val instanceState = TextFieldState()
	val usernameState = TextFieldState()
	val passwordState = TextFieldState()

	var instanceError by mutableStateOf(false)
		private set
	var usernameError by mutableStateOf(false)
		private set
	var passwordError by mutableStateOf(false)
		private set

	fun validateInstance() {
		instanceError = instanceState.text.isBlank()
	}

	fun validateUsername() {
		usernameError = usernameState.text.isBlank()
	}

	fun validatePassword() {
		passwordError = passwordState.text.isBlank()
	}

	fun validateStuff(): Boolean {
		validateInstance()
		validateUsername()
		validatePassword()
		return !instanceError && !usernameError && !passwordError
	}

	init {
		loadUser()
	}

	fun loadUser() {
		scope.launch {
			if (sessionManager.isLoggedIn.value) {
				loginState.value = LoginUiState.Success
			} else {
				loginState.value = LoginUiState.Idle
			}
		}
	}

	fun login(): Boolean {
		if (!validateStuff()) return false

		scope.launch {
			loginState.value = LoginUiState.Loading

			try {
				val rawUrl = instanceState.text.toString().trim()

				sessionManager.login(
					rawUrl,
					usernameState.text.toString().trim(),
					passwordState.text.toString()
				)

				syncManager.triggerFullSync()

				syncManager.syncState
					.takeWhile { it.isSyncing }
					.collect { state ->
						loginState.value = LoginUiState.Syncing(state.progress, state.message)
					}

				val finalState = syncManager.syncState.value
				loginState.value = if (finalState.error != null) {
					LoginUiState.Error(Exception(finalState.error))
				} else {
					LoginUiState.Success
				}

			} catch (e: Exception) {
				loginState.value = LoginUiState.Error(e)
			}
		}

		return true
	}

	fun logout() {
		loginState.value = LoginUiState.Idle
		sessionManager.logout()
		scope.launch {
			repository.removeEverything()
		}
	}
}
