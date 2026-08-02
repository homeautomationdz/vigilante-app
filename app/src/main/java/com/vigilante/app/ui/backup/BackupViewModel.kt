package com.vigilante.app.ui.backup

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vigilante.app.core.SystemLogger
import com.vigilante.app.data.excel.BackupManager
import com.vigilante.app.data.excel.ImportExportService
import com.vigilante.app.data.excel.ManualBackupOutcome
import com.vigilante.app.data.local.VigilanteDatabase
import com.vigilante.app.data.local.entity.BackupRecord
import com.vigilante.app.data.local.entity.Permission
import com.vigilante.app.security.Session
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

const val APP_VERSION = "1.5"

/** Which long-running operation is in flight (drives per-button spinners). */
enum class BackupBusy { NONE, BACKUP, EXPORT, SAVING }

/**
 * A finished export waiting for the user to pick WHERE to save it.
 * Backed by [SavedStateHandle] (plain path + name) so it survives process
 * death while the system file picker is in the foreground — the old
 * StateFlow-only version silently dropped the picked Uri in that case.
 */
data class ExportReady(val filePath: String, val fileName: String)

@HiltViewModel
class BackupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    db: VigilanteDatabase,
    private val backupManager: BackupManager,
    private val importExportService: ImportExportService,
    private val session: Session,
    private val syslog: SystemLogger,
    private val savedState: SavedStateHandle
) : ViewModel() {

    val backups: StateFlow<List<BackupRecord>> = db.backupDao().all()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Transient snackbar text (cancellations, success confirmations). */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    /** Fatal errors — shown in an AlertDialog, never a transient snackbar. */
    private val _errorDialog = MutableStateFlow<String?>(null)
    val errorDialog: StateFlow<String?> = _errorDialog

    private val _busy = MutableStateFlow(BackupBusy.NONE)
    val busy: StateFlow<BackupBusy> = _busy

    /** Set when an export finished and is waiting for a save location. */
    val exportReady: StateFlow<ExportReady?> =
        savedState.getStateFlow<String?>(KEY_EXPORT_PATH, null)
            .combine(savedState.getStateFlow<String?>(KEY_EXPORT_NAME, null)) { path, name ->
                if (path != null && name != null) ExportReady(path, name) else null
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Result of the last manual backup — shown in a dialog with its location. */
    private val _backupOutcome = MutableStateFlow<ManualBackupOutcome?>(null)
    val backupOutcome: StateFlow<ManualBackupOutcome?> = _backupOutcome

    fun canImport(): Boolean = session.has(Permission.IMPORT_EXCEL)
    fun canExport(): Boolean = session.has(Permission.EXPORT_EXCEL)

    fun createBackup() {
        if (_busy.value != BackupBusy.NONE) return
        viewModelScope.launch {
            _busy.value = BackupBusy.BACKUP
            runCatching {
                withContext(Dispatchers.IO) {
                    backupManager.createManual(APP_VERSION)
                }
            }
                .onSuccess { _backupOutcome.value = it }
                .onFailure { e ->
                    syslog.log("BACKUP_UI", "فشل إنشاء نسخة احتياطية يدوية", e)
                    _errorDialog.value =
                        "تعذر إنشاء النسخة الاحتياطية: ${e.message ?: "خطأ غير متوقع"}"
                }
            _busy.value = BackupBusy.NONE
        }
    }

    /** Builds + encrypts the workbook on IO; on success [exportReady] is set. */
    fun export() {
        if (_busy.value != BackupBusy.NONE) return
        viewModelScope.launch {
            _busy.value = BackupBusy.EXPORT
            withContext(Dispatchers.IO) { importExportService.export(APP_VERSION) }
                .onSuccess {
                    savedState[KEY_EXPORT_PATH] = it.file.absolutePath
                    savedState[KEY_EXPORT_NAME] = it.fileName
                }
                .onFailure { e ->
                    // The service already wrote the stack trace to system.log.
                    _errorDialog.value = "فشل التصدير: ${e.message ?: "خطأ غير متوقع"}"
                }
            _busy.value = BackupBusy.NONE
        }
    }

    /** Copies the finished export to the user-picked SAF location. */
    fun saveExportTo(uri: Uri) {
        val ready = exportReady.value
        if (ready == null) {
            _errorDialog.value = "لم يعد الملف المصدَّر متاحًا — أعد التصدير من جديد"
            return
        }
        copyFileTo(
            source = File(ready.filePath),
            uri = uri,
            tag = "EXPORT_UI",
            onSaved = {
                dismissExportReady()
                _message.value = "تم تصدير قاعدة البيانات بنجاح."
            }
        )
    }

    /** Copies the encrypted manual backup to the user-picked SAF location. */
    fun saveBackupTo(uri: Uri) {
        val protectedFile = _backupOutcome.value?.protectedFile
        if (protectedFile == null) {
            _errorDialog.value = "لم تعد النسخة المشفرة متاحة — أنشئ نسخة احتياطية جديدة"
            return
        }
        copyFileTo(
            source = protectedFile,
            uri = uri,
            tag = "BACKUP_UI",
            onSaved = {
                _backupOutcome.value = null
                _message.value = "تم حفظ النسخة المشفرة في المكان الذي اخترته"
            }
        )
    }

    /**
     * Shared hardened copy: IO dispatcher, "wt" (truncate) mode so overwriting
     * a longer existing file can never leave stale trailing bytes, explicit
     * flush, and a >0 bytes sanity check. Failures surface as an AlertDialog
     * and land in system.log.
     */
    private fun copyFileTo(source: File, uri: Uri, tag: String, onSaved: () -> Unit) {
        viewModelScope.launch {
            _busy.value = BackupBusy.SAVING
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    check(source.exists() && source.length() > 0) {
                        "الملف الأصلي غير موجود — أعد العملية من جديد"
                    }
                    val resolver = context.contentResolver
                    val output = runCatching { resolver.openOutputStream(uri, "wt") }
                        .getOrNull() ?: resolver.openOutputStream(uri)
                        ?: error("تعذر فتح الوجهة المختارة للكتابة")
                    var written = 0L
                    output.use { out ->
                        source.inputStream().use { input -> written = input.copyTo(out) }
                        out.flush()
                    }
                    check(written > 0) { "لم تُكتب أي بيانات إلى الوجهة المختارة" }
                }
            }
            result
                .onSuccess { onSaved() }
                .onFailure { e ->
                    syslog.log(tag, "فشل نسخ الملف إلى الوجهة المختارة عبر SAF", e)
                    _errorDialog.value = "فشل التصدير: ${e.message ?: "خطأ غير متوقع"}"
                }
            _busy.value = BackupBusy.NONE
        }
    }

    fun dismissExportReady() {
        savedState[KEY_EXPORT_PATH] = null
        savedState[KEY_EXPORT_NAME] = null
    }

    fun dismissBackupOutcome() {
        _backupOutcome.value = null
    }

    fun dismissError() {
        _errorDialog.value = null
    }

    fun postMessage(text: String) {
        _message.value = text
    }

    fun clearMessage() {
        _message.value = null
    }

    private companion object {
        const val KEY_EXPORT_PATH = "exportReadyPath"
        const val KEY_EXPORT_NAME = "exportReadyName"
    }
}
