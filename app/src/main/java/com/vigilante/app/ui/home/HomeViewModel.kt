package com.vigilante.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vigilante.app.data.local.entity.Permission
import com.vigilante.app.data.repository.AuthRepository
import com.vigilante.app.data.repository.StatsRepository
import com.vigilante.app.security.Session
import com.vigilante.app.security.SessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    statsRepository: StatsRepository,
    private val authRepository: AuthRepository,
    private val session: Session
) : ViewModel() {

    val sessionState: StateFlow<SessionState?> = session.current

    val activeCount: StateFlow<Int> = statsRepository.activeCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val archivedCount: StateFlow<Int> = statsRepository.archivedCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val attendanceToday: StateFlow<Int> = statsRepository.attendanceToday()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val joinedThisYear: StateFlow<Int> = statsRepository.joinedThisYear()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun has(permission: Permission): Boolean = session.has(permission)

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            authRepository.logout()
            onDone()
        }
    }
}
