package com.vigilante.app.ui.admins

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vigilante.app.data.local.entity.Admin
import com.vigilante.app.data.local.entity.AdminRole
import com.vigilante.app.data.local.entity.Permission
import com.vigilante.app.data.repository.AdminRepository
import com.vigilante.app.security.Session
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AdminsViewModel @Inject constructor(
    private val repository: AdminRepository,
    private val session: Session
) : ViewModel() {

    val admins: StateFlow<List<Admin>> = repository.all()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun canManage(): Boolean = session.has(Permission.MANAGE_ADMINS)

    fun add(fullName: String, username: String, password: String, role: AdminRole) {
        viewModelScope.launch {
            repository.add(fullName, username, password, role)
                .onSuccess { _message.value = "تمت إضافة المشرف" }
                .onFailure { _message.value = it.message ?: "تعذرت إضافة المشرف" }
        }
    }

    fun setActive(adminId: String, active: Boolean) {
        viewModelScope.launch {
            repository.setActive(adminId, active)
                .onSuccess {
                    _message.value = if (active) "تم تفعيل الحساب" else "تم تعطيل الحساب"
                }
                .onFailure { _message.value = it.message ?: "تعذر تنفيذ العملية" }
        }
    }

    fun resetPassword(adminId: String, newPassword: String) {
        viewModelScope.launch {
            repository.resetPassword(adminId, newPassword)
                .onSuccess { _message.value = "تمت إعادة تعيين كلمة المرور" }
                .onFailure { _message.value = it.message ?: "تعذرت إعادة التعيين" }
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}
