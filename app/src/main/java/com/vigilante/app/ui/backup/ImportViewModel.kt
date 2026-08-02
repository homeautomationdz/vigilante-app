package com.vigilante.app.ui.backup

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vigilante.app.core.AppFolders
import com.vigilante.app.data.excel.ConflictResolution
import com.vigilante.app.data.excel.ImportExportService
import com.vigilante.app.data.excel.ImportReadResult
import com.vigilante.app.data.excel.ImportReport
import com.vigilante.app.data.excel.ImportedData
import com.vigilante.app.data.excel.MergeDecisionKind
import com.vigilante.app.data.excel.MergeItem
import com.vigilante.app.data.excel.MergePlan
import com.vigilante.app.data.excel.RowError
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

sealed interface ImportUiState {
    data object Idle : ImportUiState
    data object Working : ImportUiState

    /** Terminal failure — shown in an explicit AlertDialog, not a snackbar. */
    data class Failed(val reason: String) : ImportUiState
    data class Errors(val errors: List<RowError>) : ImportUiState
    data class OrgWarning(
        val fileOrgId: String,
        val localOrgId: String,
        val data: ImportedData
    ) : ImportUiState
    data class PlanReady(val plan: MergePlan) : ImportUiState
    data class Resolving(
        val plan: MergePlan,
        val conflicts: List<MergeItem>,
        val index: Int,
        val resolutions: Map<String, ConflictResolution>
    ) : ImportUiState
    data class Done(val report: ImportReport) : ImportUiState
}

@HiltViewModel
class ImportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val service: ImportExportService,
    private val folders: AppFolders
) : ViewModel() {

    private val _state = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    val state: StateFlow<ImportUiState> = _state

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun onFilePicked(uri: Uri) {
        viewModelScope.launch {
            _state.value = ImportUiState.Working
            val readResult = withContext(Dispatchers.IO) {
                runCatching {
                    folders.ensureAll()
                    val target = File(folders.import, AppFolders.IMPORT_FILE_NAME)
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    } ?: error("تعذر فتح الملف")
                    service.readImportFile(target)
                }
            }
            readResult
                .onSuccess { handleReadResult(it) }
                .onFailure {
                    _state.value = ImportUiState.Failed(it.message ?: "تعذر قراءة الملف")
                }
        }
    }

    private suspend fun handleReadResult(result: ImportReadResult) {
        when (result) {
            is ImportReadResult.InvalidFile ->
                _state.value = ImportUiState.Failed("ملف Excel غير صالح: ${result.reason}")
            is ImportReadResult.ValidationFailed ->
                _state.value = ImportUiState.Errors(result.errors)
            is ImportReadResult.WrongOrganization ->
                _state.value = ImportUiState.OrgWarning(
                    result.fileOrgId, result.localOrgId, result.data
                )
            is ImportReadResult.Success -> analyze(result.data)
        }
    }

    fun proceedDespiteOrgMismatch() {
        val current = _state.value
        if (current is ImportUiState.OrgWarning) {
            viewModelScope.launch { analyze(current.data) }
        }
    }

    private suspend fun analyze(data: ImportedData) {
        _state.value = ImportUiState.Working
        runCatching { withContext(Dispatchers.IO) { service.analyze(data) } }
            .onSuccess { plan -> _state.value = ImportUiState.PlanReady(plan) }
            .onFailure {
                _state.value = ImportUiState.Failed(
                    "تعذر تحليل البيانات: ${it.message ?: "خطأ غير متوقع"}"
                )
            }
    }

    fun confirmPlan() {
        val current = _state.value
        if (current !is ImportUiState.PlanReady) return
        val conflicts = current.plan.items.filter { it.kind == MergeDecisionKind.CONFLICT }
        if (conflicts.isEmpty()) {
            apply(current.plan, emptyMap())
        } else {
            _state.value = ImportUiState.Resolving(current.plan, conflicts, 0, emptyMap())
        }
    }

    fun resolveCurrentConflict(resolution: ConflictResolution) {
        val current = _state.value
        if (current !is ImportUiState.Resolving) return
        val item = current.conflicts[current.index]
        val resolutions = current.resolutions + (item.incoming.volunteerId to resolution)
        val nextIndex = current.index + 1
        if (nextIndex >= current.conflicts.size) {
            apply(current.plan, resolutions)
        } else {
            _state.value = current.copy(index = nextIndex, resolutions = resolutions)
        }
    }

    private fun apply(plan: MergePlan, resolutions: Map<String, ConflictResolution>) {
        viewModelScope.launch {
            _state.value = ImportUiState.Working
            withContext(Dispatchers.IO) { service.apply(plan, resolutions, APP_VERSION) }
                .onSuccess { report -> _state.value = ImportUiState.Done(report) }
                .onFailure {
                    _state.value = ImportUiState.Failed(
                        "تعذر تنفيذ الاستيراد: ${it.message ?: "خطأ غير متوقع"}"
                    )
                }
        }
    }

    fun cancelFlow() {
        _state.value = ImportUiState.Idle
    }

    fun clearMessage() {
        _message.value = null
    }
}
