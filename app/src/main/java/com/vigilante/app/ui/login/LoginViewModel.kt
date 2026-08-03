package com.vigilante.app.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vigilante.app.R
import com.vigilante.app.core.Validation
import com.vigilante.app.data.repository.AuthRepository
import com.vigilante.app.data.repository.LoginResult
import com.vigilante.app.data.repository.RecoveryResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class LoginUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val lockRemainingSeconds: Long = 0,
    val success: Boolean = false
)

/**
 * [recoveryCode] is non-null only between a successful creation and the moment
 * the owner confirms they wrote the code down — the screen must not navigate on.
 */
data class FirstRunUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val recoveryCode: String? = null
)

/**
 * Drives the "نسيت كلمة المرور؟" dialog on the login screen.
 * [errorRes] is a string-resource id for messages that already exist in strings.xml.
 */
data class RecoveryUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val errorRes: Int? = null,
    val newRecoveryCode: String? = null
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _login = MutableStateFlow(LoginUiState())
    val login: StateFlow<LoginUiState> = _login

    private val _firstRun = MutableStateFlow(FirstRunUiState())
    val firstRun: StateFlow<FirstRunUiState> = _firstRun

    private val _recovery = MutableStateFlow(RecoveryUiState())
    val recovery: StateFlow<RecoveryUiState> = _recovery

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
                    .onSuccess { result ->
                        _firstRun.value = FirstRunUiState(recoveryCode = result.recoveryCode)
                    }
                    .onFailure {
                        _firstRun.value = FirstRunUiState(
                            error = it.message ?: "تعذر إنشاء الحساب"
                        )
                    }
            }
        }
    }

    /** Called once the owner confirms the first-run code was written down. */
    fun acknowledgeFirstRunCode() {
        _firstRun.value = FirstRunUiState()
    }

    // ---- account recovery ----

    /** Resets the password with the recovery code; the reply carries a new code. */
    fun recover(username: String, code: String, newPassword: String, confirm: String) {
        val name = username.trim()
        when {
            name.isBlank() || code.isBlank() || newPassword.isBlank() ->
                _recovery.value = RecoveryUiState(error = "أكمل جميع الحقول")
            newPassword != confirm ->
                _recovery.value = RecoveryUiState(error = "كلمتا المرور غير متطابقتين")
            else -> viewModelScope.launch {
                _recovery.value = RecoveryUiState(loading = true)
                val result = withContext(Dispatchers.IO) {
                    authRepository.recoverWithCode(name, code, newPassword)
                }
                _recovery.value = when (result) {
                    is RecoveryResult.Success ->
                        RecoveryUiState(newRecoveryCode = result.newRecoveryCode)
                    is RecoveryResult.Invalid ->
                        RecoveryUiState(error = "اسم المستخدم أو رمز الاسترجاع غير صحيح")
                    is RecoveryResult.WeakPassword ->
                        RecoveryUiState(errorRes = R.string.error_password_weak)
                    is RecoveryResult.NoRecoveryConfigured ->
                        RecoveryUiState(error = "لا يوجد رمز استرجاع محفوظ لهذا التطبيق")
                    is RecoveryResult.Locked -> RecoveryUiState(
                        error = "تم القفل مؤقتًا، حاول بعد ${result.remainingSeconds} ثانية"
                    )
                }
            }
        }
    }

    /** Clears the dialog state (cancelled, or the new code was acknowledged). */
    fun clearRecovery() {
        _recovery.value = RecoveryUiState()
    }

    fun clearRecoveryError() {
        if (_recovery.value.error != null || _recovery.value.errorRes != null) {
            _recovery.value = _recovery.value.copy(error = null, errorRes = null)
        }
    }

    fun clearLoginError() {
        if (_login.value.error != null) _login.value = _login.value.copy(error = null)
    }
}
