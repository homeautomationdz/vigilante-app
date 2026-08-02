package com.vigilante.app.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vigilante.app.core.Validation
import com.vigilante.app.data.repository.AuthRepository
import com.vigilante.app.data.repository.LoginResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val lockRemainingSeconds: Long = 0,
    val success: Boolean = false
)

data class FirstRunUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val created: Boolean = false
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _login = MutableStateFlow(LoginUiState())
    val login: StateFlow<LoginUiState> = _login

    private val _firstRun = MutableStateFlow(FirstRunUiState())
    val firstRun: StateFlow<FirstRunUiState> = _firstRun

    private var countdownJob: Job? = null

    fun doLogin(username: String, password: String) {
        if (username.isBlank() || password.isBlank()) {
            _login.value = _login.value.copy(error = "أدخل اسم المستخدم وكلمة المرور")
            return
        }
        viewModelScope.launch {
            _login.value = LoginUiState(loading = true)
            when (val result = authRepository.login(username, password)) {
                is LoginResult.Success -> _login.value = LoginUiState(success = true)
                is LoginResult.WrongCredentials -> _login.value =
                    LoginUiState(error = "اسم المستخدم أو كلمة المرور غير صحيحة")
                is LoginResult.AccountDisabled -> _login.value =
                    LoginUiState(error = "هذا الحساب معطل. تواصل مع مدير النظام")
                is LoginResult.Locked -> startCountdown(result.remainingSeconds)
            }
        }
    }

    private fun startCountdown(seconds: Long) {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            var remaining = seconds
            while (remaining > 0) {
                _login.value = LoginUiState(lockRemainingSeconds = remaining)
                delay(1000)
                remaining--
            }
            _login.value = LoginUiState()
        }
    }

    /** First run: one full-name field — the name itself is the username. */
    fun createFirstAccount(
        fullName: String,
        password: String,
        confirm: String
    ) {
        val name = fullName.trim()
        when {
            name.isBlank() ->
                _firstRun.value = FirstRunUiState(error = "الاسم الكامل إلزامي")
            !Validation.isValidUsername(name) ->
                _firstRun.value = FirstRunUiState(error = "الاسم الكامل غير صالح (3 أحرف على الأقل)")
            !Validation.isValidPassword(password) ->
                _firstRun.value = FirstRunUiState(
                    error = "كلمة المرور يجب أن تكون 8 أحرف على الأقل وتحتوي على حرف ورقم"
                )
            password != confirm ->
                _firstRun.value = FirstRunUiState(error = "كلمتا المرور غير متطابقتين")
            else -> viewModelScope.launch {
                _firstRun.value = FirstRunUiState(loading = true)
                authRepository.createFirstSuperAdmin(
                    fullName = name,
                    username = name,
                    password = password
                )
                    .onSuccess { _firstRun.value = FirstRunUiState(created = true) }
                    .onFailure {
                        _firstRun.value = FirstRunUiState(
                            error = it.message ?: "تعذر إنشاء الحساب"
                        )
                    }
            }
        }
    }

    fun clearLoginError() {
        if (_login.value.error != null) _login.value = _login.value.copy(error = null)
    }
}
