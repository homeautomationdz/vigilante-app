package com.vigilante.app.ui.archive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vigilante.app.data.local.entity.Permission
import com.vigilante.app.data.local.entity.Volunteer
import com.vigilante.app.data.repository.VolunteerRepository
import com.vigilante.app.security.Session
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ArchiveViewModel @Inject constructor(
    private val repository: VolunteerRepository,
    private val session: Session
) : ViewModel() {

    val archived: StateFlow<List<Volunteer>> = repository.archived()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun canRestore(): Boolean = session.has(Permission.RESTORE_FROM_ARCHIVE)

    fun restore(volunteerId: String) {
        viewModelScope.launch {
            repository.restore(volunteerId)
                .onSuccess { _message.value = "تمت استعادة المتطوع" }
                .onFailure { _message.value = it.message ?: "تعذرت الاستعادة" }
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}
