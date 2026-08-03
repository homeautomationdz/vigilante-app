package com.vigilante.app.ui.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vigilante.app.R
import com.vigilante.app.ui.components.EmptyState
import com.vigilante.app.ui.components.SectionHeader
import com.vigilante.app.ui.components.VCard
import com.vigilante.app.ui.components.VigilanteTopBar
import java.time.format.DateTimeFormatter

private val dateTimeFmt = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")

private const val EXCEL_MIME =
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

@Composable
fun BackupScreen(
    onBack: () -> Unit,
    onOpenImport: () -> Unit,
    viewModel: BackupViewModel = hiltViewModel()
) {
    val backups by viewModel.backups.collectAsState()
    val message by viewModel.message.collectAsState()
    val errorDialog by viewModel.errorDialog.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val exportReady by viewModel.exportReady.collectAsState()
    val backupOutcome by viewModel.backupOutcome.collectAsState()
    val passwordPrompt by viewModel.passwordPrompt.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    // Export: the user explicitly taps a button inside the dialog below to open
    // the system "save as" dialog (user gesture -> launch; no auto-launch from
    // effects, which could re-fire or be dropped across activity recreation).
    val exportSaveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(EXCEL_MIME)
    ) { uri ->
        if (uri == null) {
            viewModel.postMessage("أُلغي الحفظ")
        } else {
            viewModel.saveExportTo(uri)
        }
    }

    // Manual backup: same pattern, seeded with the backup file name.
    val backupSaveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(EXCEL_MIME)
    ) { uri ->
        if (uri == null) {
            viewModel.postMessage("أُلغي الحفظ")
        } else {
            viewModel.saveBackupTo(uri)
        }
    }

    // Fatal errors: explicit dialog, never a transient snackbar.
    errorDialog?.let { text ->
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            shape = MaterialTheme.shapes.large,
            title = { Text("خطأ") },
            text = { Text(text) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissError) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }

    // No Excel password yet: set one here and the tapped action is retried.
    if (passwordPrompt != null) {
        var password by remember { mutableStateOf("") }
        var confirm by remember { mutableStateOf("") }
        var showPassword by remember { mutableStateOf(false) }
        var validationError by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = viewModel::dismissPasswordPrompt,
            shape = MaterialTheme.shapes.large,
            title = { Text("حدد كلمة مرور ملفات Excel") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "كل ملف مصدَّر أو نسخة احتياطية تُشارَك تُشفَّر بكلمة مرور. " +
                            "حددها مرة واحدة وستُستخدم تلقائيًا بعد ذلك.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            validationError = null
                        },
                        label = { Text("كلمة المرور") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.small,
                        visualTransformation = if (showPassword) VisualTransformation.None
                        else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    if (showPassword) Icons.Filled.VisibilityOff
                                    else Icons.Filled.Visibility,
                                    contentDescription = stringResource(R.string.show_password)
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = confirm,
                        onValueChange = {
                            confirm = it
                            validationError = null
                        },
                        label = { Text("تأكيد كلمة المرور") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.small,
                        visualTransformation = if (showPassword) VisualTransformation.None
                        else PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    validationError?.let { error ->
                        Text(
                            error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        validationError = when {
                            password.isBlank() -> "كلمة المرور فارغة"
                            password != confirm -> "كلمتا المرور غير متطابقتين"
                            else -> null
                        }
                        if (validationError == null) {
                            viewModel.saveExcelPasswordAndRetry(password)
                        }
                    },
                    enabled = busy == BackupBusy.NONE
                ) {
                    Text("حفظ ومتابعة")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissPasswordPrompt) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Export finished: ask the user WHERE to save it.
    exportReady?.let { ready ->
        AlertDialog(
            onDismissRequest = viewModel::dismissExportReady,
            shape = MaterialTheme.shapes.large,
            title = { Text("الملف جاهز — اختر مكان الحفظ") },
            text = {
                Text(
                    "تم إنشاء الملف:\n${ready.fileName}\n\n" +
                        "اختر المكان الذي تريد حفظه فيه. توجد نسخة داخلية أيضًا في مجلد " +
                        "Vigilante/Export داخل التطبيق."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { exportSaveLauncher.launch(ready.fileName) },
                    enabled = busy == BackupBusy.NONE
                ) {
                    Text("اختيار مكان الحفظ…")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissExportReady) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    backupOutcome?.let { outcome ->
        AlertDialog(
            onDismissRequest = viewModel::dismissBackupOutcome,
            shape = MaterialTheme.shapes.large,
            title = { Text("تم إنشاء النسخة الاحتياطية") },
            text = {
                Text(
                    if (outcome.protectedFile != null) {
                        "توجد نسخة داخلية في:\n${outcome.internalPath}" +
                            "\n\nيمكنك أيضًا حفظ النسخة المشفرة في المكان الذي تختاره."
                    } else {
                        "تم الحفظ داخل مجلد التطبيق:\n${outcome.internalPath}"
                    }
                )
            },
            confirmButton = {
                if (outcome.protectedFile != null) {
                    TextButton(
                        onClick = { backupSaveLauncher.launch(outcome.record.fileName) },
                        enabled = busy == BackupBusy.NONE
                    ) {
                        Text("اختيار مكان الحفظ…")
                    }
                } else {
                    TextButton(onClick = viewModel::dismissBackupOutcome) {
                        Text("موافق")
                    }
                }
            },
            dismissButton = {
                if (outcome.protectedFile != null) {
                    TextButton(onClick = viewModel::dismissBackupOutcome) {
                        Text("موافق")
                    }
                }
            }
        )
    }

    Scaffold(
        topBar = { VigilanteTopBar(title = stringResource(R.string.menu_backup), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            if (busy != BackupBusy.NONE) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                )
            }
            Button(
                onClick = viewModel::createBackup,
                enabled = busy == BackupBusy.NONE,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp)
                    .height(54.dp)
            ) {
                if (busy == BackupBusy.BACKUP) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(
                        Icons.Filled.Backup,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.backup_now),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (viewModel.canExport()) {
                    OutlinedButton(
                        onClick = viewModel::export,
                        enabled = busy == BackupBusy.NONE,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                    ) {
                        if (busy == BackupBusy.EXPORT) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp))
                        } else {
                            Icon(
                                Icons.Filled.FileUpload,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.export_excel))
                        }
                    }
                }
                if (viewModel.canImport()) {
                    OutlinedButton(
                        onClick = onOpenImport,
                        enabled = busy == BackupBusy.NONE,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                    ) {
                        Icon(
                            Icons.Filled.FileDownload,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.import_excel))
                    }
                }
            }

            SectionHeader("النسخ السابقة", modifier = Modifier.padding(top = 10.dp))
            if (backups.isEmpty()) {
                EmptyState(text = "لا توجد نسخ احتياطية بعد", icon = Icons.Filled.Backup)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(backups, key = { it.backupId }) { record ->
                        VCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    record.fileName,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    record.createdAt.format(dateTimeFmt) + " — " + record.createdBy,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "متطوعون: ${record.volunteerCount} • حضور: ${record.attendanceCount} • " +
                                        "مشرفون: ${record.adminCount} • الحجم: ${formatSize(record.sizeBytes)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                                Text(
                                    "المكان: Vigilante/Backup/${record.fileName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1_048_576 -> "%.1f م.ب".format(bytes / 1_048_576.0)
    bytes >= 1024 -> "%.0f ك.ب".format(bytes / 1024.0)
    else -> "$bytes بايت"
}
