package com.vigilante.app.ui.attendance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vigilante.app.data.local.entity.Attendance
import com.vigilante.app.data.local.entity.Volunteer
import com.vigilante.app.data.repository.AttendanceOutcome
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

data class AttendanceRow(
    val attendance: Attendance,
    val volunteerName: String
)

/** Dialog state machine for the attendance screen. */
sealed interface AttendanceDialog {
    data object None : AttendanceDialog
    data class Confirm(val volunteer: Volunteer) : AttendanceDialog
    data class Archived(val volunteer: Volunteer) : AttendanceDialog
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AttendanceViewModel @Inject constructor(
    private val attendanceRepository: AttendanceRepository,
    private val volunteerRepository: VolunteerRepository
) : ViewModel() {

    private val nameCache = mutableMapOf<String, String>()

    val recent: StateFlow<List<AttendanceRow>> = attendanceRepository.recent(50)
        .map { list -> list.map { AttendanceRow(it, nameFor(it.volunteerId)) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _dialog = MutableStateFlow<AttendanceDialog>(AttendanceDialog.None)
    val dialog: StateFlow<AttendanceDialog> = _dialog

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    // ---- manual pick ----
    private val _manualQuery = MutableStateFlow("")
    val manualQuery: StateFlow<String> = _manualQuery

    private val _manualOpen = MutableStateFlow(false)
    val manualOpen: StateFlow<Boolean> = _manualOpen

    val manualResults: StateFlow<List<Volunteer>> = _manualQuery
        .flatMapLatest { volunteerRepository.search(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private suspend fun nameFor(volunteerId: String): String =
        nameCache.getOrPut(volunteerId) {
            volunteerRepository.byId(volunteerId)?.displayName ?: volunteerId
        }

    fun onQrScanned(content: String) {
        viewModelScope.launch {
            when (val outcome = attendanceRepository.lookup(content)) {
                is AttendanceOutcome.Recorded ->
                    _dialog.value = AttendanceDialog.Confirm(outcome.volunteer)
                is AttendanceOutcome.VolunteerArchived ->
                    _dialog.value = AttendanceDialog.Archived(outcome.volunteer)
                is AttendanceOutcome.VolunteerNotFound ->
                    _message.value = "هذا المتطوع غير موجود داخل قاعدة البيانات"
                is AttendanceOutcome.Duplicate ->
                    _message.value = "تم تسجيل حضور هذا المتطوع منذ لحظات"
            }
        }
    }

    fun pickManually(volunteer: Volunteer) {
        _manualOpen.value = false
        _dialog.value = AttendanceDialog.Confirm(volunteer)
    }

    fun confirmRecord(volunteerId: String) {
        _dialog.value = AttendanceDialog.None
        viewModelScope.launch {
            when (val outcome = attendanceRepository.record(volunteerId)) {
                is AttendanceOutcome.Recorded ->
                    _message.value = "تم تسجيل الحضور — ${outcome.volunteer.displayName}"
                is AttendanceOutcome.Duplicate ->
                    _message.value = "تم تسجيل حضور هذا المتطوع منذ لحظات"
                is AttendanceOutcome.VolunteerArchived ->
                    _dialog.value = AttendanceDialog.Archived(outcome.volunteer)
                is AttendanceOutcome.VolunteerNotFound ->
                    _message.value = "هذا المتطوع غير موجود داخل قاعدة البيانات"
            }
        }
    }

    fun dismissDialog() {
        _dialog.value = AttendanceDialog.None
    }

    fun openManual() {
        _manualQuery.value = ""
        _manualOpen.value = true
    }

    fun closeManual() {
        _manualOpen.value = false
    }

    fun setManualQuery(query: String) {
        _manualQuery.value = query
    }

    fun clearMessage() {
        _message.value = null
    }
}
