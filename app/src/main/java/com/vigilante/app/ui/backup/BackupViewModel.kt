package com.vigilante.app.ui.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vigilante.app.data.excel.BackupManager
import com.vigilante.app.data.excel.ImportExportService
import com.vigilante.app.data.local.VigilanteDatabase
import com.vigilante.app.data.local.entity.BackupRecord
import com.vigilante.app.data.local.entity.Permission
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

const val APP_VERSION = "1.0"

@HiltViewModel
class BackupViewModel @Inject constructor(
    db: VigilanteDatabase,
    private val backupManager: BackupManager,
    private val importExportService: ImportExportService,
    private val session: Session
) : ViewModel() {

    val backups: StateFlow<List<BackupRecord>> = db.backupDao().all()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    /** File to hand to a share intent after a successful export. */
    private val _exportedFile = MutableStateFlow<File?>(null)
    val exportedFile: StateFlow<File?> = _exportedFile

    fun canImport(): Boolean = session.has(Permission.IMPORT_EXCEL)
    fun canExport(): Boolean = session.has(Permission.EXPORT_EXCEL)

    fun createBackup() {
        viewModelScope.launch {
            _busy.value = true
            runCatching {
                withContext(Dispatchers.IO) {
                    backupManager.create(reason = "MANUAL", appVersion = APP_VERSION)
                }
            }
                .onSuccess { _message.value = "تم إنشاء النسخة الاحتياطية: ${it.fileName}" }
                .onFailure { _message.value = "تعذر إنشاء النسخة الاحتياطية" }
            _busy.value = false
        }
    }

    fun export() {
        viewModelScope.launch {
            _busy.value = true
            withContext(Dispatchers.IO) { importExportService.export(APP_VERSION) }
                .onSuccess { file ->
                    _message.value = "تم التصدير: ${file.name}"
                    _exportedFile.value = file
                }
                .onFailure { _message.value = it.message ?: "تعذر التصدير" }
            _busy.value = false
        }
    }

    fun consumeExportedFile() {
        _exportedFile.value = null
    }

    fun clearMessage() {
        _message.value = null
    }
}
