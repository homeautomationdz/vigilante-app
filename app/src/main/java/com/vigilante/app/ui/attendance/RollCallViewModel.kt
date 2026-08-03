package com.vigilante.app.ui.attendance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vigilante.app.core.AppFolders
import com.vigilante.app.data.local.dao.RollCallRow
import com.vigilante.app.data.repository.AttendanceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class RollCallState(
    val loading: Boolean = true,
    /** Frozen order: computed once so rows never jump under the user's finger. */
    val rows: List<RollCallRow> = emptyList(),
    val presentIds: Set<String> = emptySet(),
    val busyId: String? = null,
    val query: String = "",
    val message: String? = null
) {
    val visibleRows: List<RollCallRow>
        get() {
            val q = query.trim()
            if (q.isBlank()) return rows
            return rows.filter {
                it.displayName.contains(q, true) ||
                    it.membershipNumber.contains(q, true) ||
                    it.volunteerId.contains(q, true) ||
                    it.phone1.contains(q) ||
                    (it.municipality?.contains(q, true) == true)
            }
        }

    val presentCount: Int get() = presentIds.size
    val absentCount: Int get() = rows.size - presentIds.size
}

/**
 * Roll call: the supervisor walks the list and taps حاضر for whoever showed up;
 * everyone left untapped is simply absent (no record is created for them).
 */
@HiltViewModel
class RollCallViewModel @Inject constructor(
    private val repository: AttendanceRepository,
    private val folders: AppFolders
) : ViewModel() {

    fun photoFile(volunteerId: String): File? =
        folders.photoFile(volunteerId).takeIf { it.exists() }

    private val _state = MutableStateFlow(RollCallState())
    val state: StateFlow<RollCallState> = _state

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            val rows = withContext(Dispatchers.IO) { runCatching { repository.rollCall() } }
                .getOrElse {
                    _state.value = _state.value.copy(
                        loading = false,
                        message = "تعذر تحميل القائمة: ${it.message ?: "خطأ غير متوقع"}"
                    )
                    return@launch
                }
            _state.value = _state.value.copy(
                loading = false,
                rows = rows,
                presentIds = rows.filter { it.presentToday == 1 }.map { it.volunteerId }.toSet()
            )
        }
    }

    fun onQuery(value: String) {
        _state.value = _state.value.copy(query = value)
    }

    /** Tap toggles: mark present, tap again to undo a mis-tap. */
    fun toggle(volunteerId: String) {
        val current = _state.value
        if (current.busyId != null) return
        val wasPresent = volunteerId in current.presentIds
        _state.value = current.copy(busyId = volunteerId)

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                if (wasPresent) repository.unmarkToday(volunteerId)
                else repository.markPresent(volunteerId)
            }
            val s = _state.value
            _state.value = result.fold(
                onSuccess = {
                    s.copy(
                        busyId = null,
                        presentIds = if (wasPresent) s.presentIds - volunteerId
                        else s.presentIds + volunteerId
                    )
                },
                onFailure = { e ->
                    s.copy(busyId = null, message = e.message ?: "تعذر تسجيل الحضور")
                }
            )
        }
    }

    fun clearMessage() {
        _state.value = _state.value.copy(message = null)
    }
}
