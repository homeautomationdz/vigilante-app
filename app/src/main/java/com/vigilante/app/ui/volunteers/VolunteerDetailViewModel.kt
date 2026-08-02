package com.vigilante.app.ui.volunteers

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vigilante.app.core.AppFolders
import com.vigilante.app.data.files.QrStore
import com.vigilante.app.data.local.entity.AuditLog
import com.vigilante.app.data.local.entity.Permission
import com.vigilante.app.data.local.entity.Tag
import com.vigilante.app.data.local.entity.Volunteer
import com.vigilante.app.data.repository.AttendanceOutcome
import com.vigilante.app.data.repository.AttendanceRepository
import com.vigilante.app.data.repository.VolunteerRepository
import com.vigilante.app.security.Session
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class VolunteerDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: VolunteerRepository,
    private val attendanceRepository: AttendanceRepository,
    private val qrStore: QrStore,
    private val folders: AppFolders,
    private val session: Session
) : ViewModel() {

    val volunteerId: String = savedStateHandle.get<String>("id").orEmpty()

    val volunteer: StateFlow<Volunteer?> = repository.byIdFlow(volunteerId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _summary =
        MutableStateFlow(AttendanceRepository.VolunteerAttendanceSummary(0, null, null))
    val summary: StateFlow<AttendanceRepository.VolunteerAttendanceSummary> = _summary

    private val _tags = MutableStateFlow<List<Tag>>(emptyList())
    val tags: StateFlow<List<Tag>> = _tags

    private val _timeline = MutableStateFlow<List<AuditLog>>(emptyList())
    val timeline: StateFlow<List<AuditLog>> = _timeline

    private val _qrFile = MutableStateFlow<File?>(null)
    val qrFile: StateFlow<File?> = _qrFile

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    private val _closed = MutableStateFlow(false)
    val closed: StateFlow<Boolean> = _closed

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _summary.value = attendanceRepository.summaryFor(volunteerId)
            _tags.value = repository.tagsFor(volunteerId)
            _timeline.value = repository.timeline(volunteerId).reversed()
            _qrFile.value = withContext(Dispatchers.IO) {
                runCatching { qrStore.ensureQr(volunteerId) }.getOrNull()
            }
        }
    }

    fun photoFile(): File? = folders.photoFile(volunteerId).takeIf { it.exists() }

    fun has(permission: Permission): Boolean = session.has(permission)

    fun recordAttendance() {
        viewModelScope.launch {
            when (val outcome = attendanceRepository.record(volunteerId)) {
                is AttendanceOutcome.Recorded -> {
                    _message.value = "تم تسجيل الحضور"
                    refresh()
                }
                is AttendanceOutcome.Duplicate ->
                    _message.value = "تم تسجيل حضور هذا المتطوع منذ لحظات"
                is AttendanceOutcome.VolunteerArchived ->
                    _message.value = "لا يمكن تسجيل حضور متطوع مؤرشف"
                is AttendanceOutcome.VolunteerNotFound ->
                    _message.value = "هذا المتطوع غير موجود داخل قاعدة البيانات"
            }
        }
    }

    fun archive(reason: String?) {
        viewModelScope.launch {
            repository.archive(volunteerId, reason)
                .onSuccess {
                    _message.value = "تم نقل المتطوع إلى الأرشيف"
                    refresh()
                }
                .onFailure { _message.value = it.message ?: "تعذر تنفيذ الأرشفة" }
        }
    }

    fun restore() {
        viewModelScope.launch {
            repository.restore(volunteerId)
                .onSuccess {
                    _message.value = "تمت استعادة المتطوع"
                    refresh()
                }
                .onFailure { _message.value = it.message ?: "تعذرت الاستعادة" }
        }
    }

    fun deletePermanently() {
        viewModelScope.launch {
            repository.moveToRecycleBin(volunteerId)
                .onSuccess { _closed.value = true }
                .onFailure { _message.value = it.message ?: "تعذر الحذف" }
        }
    }

    fun shareText(v: Volunteer): String = buildString {
        appendLine("بطاقة متطوع — Vigilante")
        appendLine("المعرف: ${v.volunteerId}")
        appendLine("رقم العضوية: ${v.membershipNumber}")
        appendLine("الاسم: ${v.displayName}")
        v.birthDate?.let { appendLine("تاريخ الميلاد: $it") }
        appendLine("تاريخ الانضمام: ${v.joinDate}")
        v.municipality?.let { appendLine("البلدية: $it") }
        v.district?.let { appendLine("الحي: $it") }
        v.bloodGroup?.let { appendLine("زمرة الدم: $it") }
        appendLine("الهاتف: ${v.phone1}")
        v.phone2?.let { appendLine("هاتف ثانٍ: $it") }
    }

    fun clearMessage() {
        _message.value = null
    }
}
