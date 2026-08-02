package com.vigilante.app.ui.attendance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vigilante.app.data.repository.AttendanceRepository
import com.vigilante.app.data.repository.VolunteerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AttendanceLogViewModel @Inject constructor(
    private val attendanceRepository: AttendanceRepository,
    private val volunteerRepository: VolunteerRepository
) : ViewModel() {

    private val nameCache = mutableMapOf<String, String>()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    val rows: StateFlow<List<AttendanceRow>> = _query
        .flatMapLatest { attendanceRepository.searchLog(it) }
        .map { list ->
            list.map { att ->
                AttendanceRow(
                    att,
                    nameCache.getOrPut(att.volunteerId) {
                        volunteerRepository.byId(att.volunteerId)?.displayName ?: att.volunteerId
                    }
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun setQuery(value: String) {
        _query.value = value
    }

    fun cancel(attendanceId: String, note: String) {
        viewModelScope.launch {
            attendanceRepository.cancel(attendanceId, note)
                .onSuccess { _message.value = "تم إلغاء سجل الحضور" }
                .onFailure { _message.value = it.message ?: "تعذر الإلغاء" }
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}
