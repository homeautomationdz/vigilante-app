package com.vigilante.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vigilante.app.data.local.entity.AdminRole
import com.vigilante.app.data.local.entity.AppSetting
import com.vigilante.app.data.local.entity.Permission
import com.vigilante.app.data.repository.AdminRepository
import com.vigilante.app.data.repository.SettingsRepository
import com.vigilante.app.security.Session
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val loading: Boolean = true,
    val orgId: String = "",
    val orgName: String = "",
    val orgPhone: String = "",
    val orgEmail: String = "",
    val orgAddress: String = "",
    val lockSeconds: String = "30",
    val duplicateSeconds: String = "60",
    val sessionTimeoutMinutes: String = "15",
    val recycleBinDays: String = "30",
    val excelPassword: String = "",
    val encryptExports: Boolean = false,
    val readOnlyMode: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val adminRepository: AdminRepository,
    private val session: Session
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    init {
        load()
    }

    fun isSuperAdmin(): Boolean =
        session.current.value?.admin?.role == AdminRole.SUPER_ADMIN

    fun canManageSettings(): Boolean =
        session.has(Permission.MANAGE_SETTINGS) || isSuperAdmin()

    fun canViewAuditLog(): Boolean = session.has(Permission.VIEW_AUDIT_LOG)

    /** الأماكن list editing is gated by the same settings permission the repository checks. */
    fun canManagePlaces(): Boolean = session.has(Permission.MANAGE_SETTINGS)

    fun canSeeRecycleBin(): Boolean = isSuperAdmin()

    private fun load() {
        viewModelScope.launch {
            _state.value = SettingsUiState(
                loading = false,
                orgId = settingsRepository.get(AppSetting.KEY_ORG_ID).orEmpty(),
                orgName = settingsRepository.get(AppSetting.KEY_ORG_NAME).orEmpty(),
                orgPhone = settingsRepository.get(AppSetting.KEY_ORG_PHONE).orEmpty(),
                orgEmail = settingsRepository.get(AppSetting.KEY_ORG_EMAIL).orEmpty(),
                orgAddress = settingsRepository.get(AppSetting.KEY_ORG_ADDRESS).orEmpty(),
                lockSeconds = settingsRepository.get(AppSetting.KEY_LOCK_SECONDS) ?: "30",
                duplicateSeconds =
                    settingsRepository.get(AppSetting.KEY_DUPLICATE_ATTENDANCE_SECONDS) ?: "60",
                sessionTimeoutMinutes =
                    settingsRepository.get(AppSetting.KEY_SESSION_TIMEOUT_MINUTES) ?: "15",
                recycleBinDays = settingsRepository.get(AppSetting.KEY_RECYCLE_BIN_DAYS) ?: "30",
                excelPassword = settingsRepository.get(AppSetting.KEY_EXCEL_PASSWORD).orEmpty(),
                encryptExports =
                    settingsRepository.get(AppSetting.KEY_ENCRYPT_EXPORTS) == "true",
                readOnlyMode = session.readOnlyMode ||
                    settingsRepository.get(AppSetting.KEY_READ_ONLY_MODE) == "true"
            )
        }
    }

    fun update(transform: (SettingsUiState) -> SettingsUiState) {
        _state.value = transform(_state.value)
    }

    fun saveOrganization() {
        viewModelScope.launch {
            val s = _state.value
            val results = listOf(
                settingsRepository.set(AppSetting.KEY_ORG_NAME, s.orgName.trim()),
                settingsRepository.set(AppSetting.KEY_ORG_PHONE, s.orgPhone.trim()),
                settingsRepository.set(AppSetting.KEY_ORG_EMAIL, s.orgEmail.trim()),
                settingsRepository.set(AppSetting.KEY_ORG_ADDRESS, s.orgAddress.trim())
            )
            val failure = results.firstOrNull { it.isFailure }
            _message.value = if (failure == null) "تم حفظ معلومات الجمعية"
            else failure.exceptionOrNull()?.message ?: "تعذر الحفظ"
        }
    }

    fun saveSecurity() {
        viewModelScope.launch {
            val s = _state.value
            val lock = s.lockSeconds.toIntOrNull()
            val dup = s.duplicateSeconds.toIntOrNull()
            val timeout = s.sessionTimeoutMinutes.toIntOrNull()
            val bin = s.recycleBinDays.toIntOrNull()
            if (lock == null || dup == null || timeout == null || bin == null ||
                lock <= 0 || dup <= 0 || timeout <= 0 || bin <= 0
            ) {
                _message.value = "أدخل قيمًا رقمية صحيحة أكبر من صفر"
                return@launch
            }
            val results = listOf(
                settingsRepository.set(AppSetting.KEY_LOCK_SECONDS, lock.toString()),
                settingsRepository.set(
                    AppSetting.KEY_DUPLICATE_ATTENDANCE_SECONDS, dup.toString()
                ),
                settingsRepository.set(
                    AppSetting.KEY_SESSION_TIMEOUT_MINUTES, timeout.toString()
                ),
                settingsRepository.set(AppSetting.KEY_RECYCLE_BIN_DAYS, bin.toString())
            )
            session.timeoutMinutes = timeout.toLong()
            val failure = results.firstOrNull { it.isFailure }
            _message.value = if (failure == null) "تم حفظ إعدادات الأمان"
            else failure.exceptionOrNull()?.message ?: "تعذر الحفظ"
        }
    }

    fun saveExcelPassword() {
        viewModelScope.launch {
            val password = _state.value.excelPassword.trim()
            if (password.isEmpty()) {
                _message.value = "أدخل كلمة مرور غير فارغة"
                return@launch
            }
            settingsRepository.set(AppSetting.KEY_EXCEL_PASSWORD, password)
                .onSuccess { _message.value = "تم حفظ كلمة مرور ملفات Excel" }
                .onFailure { _message.value = it.message ?: "تعذر الحفظ" }
        }
    }

    /** Exports are plain by default; encryption is opt-in (needs a saved password). */
    fun setEncryptExports(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.set(AppSetting.KEY_ENCRYPT_EXPORTS, enabled.toString())
                .onSuccess {
                    _state.value = _state.value.copy(encryptExports = enabled)
                    _message.value = if (enabled) "سيتم تشفير الملفات المصدَّرة"
                    else "سيتم تصدير الملفات بدون تشفير"
                }
                .onFailure { _message.value = it.message ?: "تعذر تغيير الإعداد" }
        }
    }

    fun setReadOnlyMode(enabled: Boolean) {
        viewModelScope.launch {
            // Disabling must lift the in-memory flag FIRST, otherwise the
            // permission check inside SettingsRepository.set would fail.
            if (!enabled) session.readOnlyMode = false
            settingsRepository.set(AppSetting.KEY_READ_ONLY_MODE, enabled.toString())
                .onSuccess {
                    session.readOnlyMode = enabled
                    _state.value = _state.value.copy(readOnlyMode = enabled)
                    _message.value = if (enabled) "تم تفعيل وضع القراءة فقط"
                    else "تم إيقاف وضع القراءة فقط"
                }
                .onFailure {
                    _message.value = it.message ?: "تعذر تغيير الوضع"
                }
        }
    }

    fun changePassword(current: String, newPassword: String, confirm: String) {
        if (newPassword != confirm) {
            _message.value = "كلمتا المرور غير متطابقتين"
            return
        }
        viewModelScope.launch {
            adminRepository.changeOwnPassword(current, newPassword)
                .onSuccess { _message.value = "تم تغيير كلمة المرور" }
                .onFailure { _message.value = it.message ?: "تعذر تغيير كلمة المرور" }
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}
