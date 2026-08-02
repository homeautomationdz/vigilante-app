package com.vigilante.app.ui.backup

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.vigilante.app.R
import com.vigilante.app.ui.components.EmptyState
import com.vigilante.app.ui.components.SectionHeader
import com.vigilante.app.ui.components.VigilanteTopBar
import java.time.format.DateTimeFormatter

private val dateTimeFmt = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")

@Composable
fun BackupScreen(
    onBack: () -> Unit,
    onOpenImport: () -> Unit,
    viewModel: BackupViewModel = hiltViewModel()
) {
    val backups by viewModel.backups.collectAsState()
    val message by viewModel.message.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val exportedFile by viewModel.exportedFile.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    LaunchedEffect(exportedFile) {
        val file = exportedFile ?: return@LaunchedEffect
        runCatching {
            val uri = FileProvider.getUriForFile(context, "com.vigilante.app.fileprovider", file)
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(share, "مشاركة ملف التصدير"))
        }
        viewModel.consumeExportedFile()
    }

    Scaffold(
        topBar = { VigilanteTopBar(title = stringResource(R.string.menu_backup), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            if (busy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
            }
            Button(
                onClick = viewModel::createBackup,
                enabled = !busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Icon(Icons.Filled.Save, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.backup_now), style = MaterialTheme.typography.titleMedium)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (viewModel.canExport()) {
                    OutlinedButton(
                        onClick = viewModel::export,
                        enabled = !busy,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                    ) {
                        Icon(Icons.Filled.FileUpload, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.export_excel))
                    }
                }
                if (viewModel.canImport()) {
                    OutlinedButton(
                        onClick = onOpenImport,
                        enabled = !busy,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                    ) {
                        Icon(Icons.Filled.FileDownload, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.import_excel))
                    }
                }
            }

            SectionHeader("النسخ السابقة", modifier = Modifier.padding(top = 16.dp))
            if (backups.isEmpty()) {
                EmptyState(text = "لا توجد نسخ احتياطية بعد", icon = Icons.Filled.Save)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(backups, key = { it.backupId }) { record ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(record.fileName, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    record.createdAt.format(dateTimeFmt) + " — " + record.createdBy,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "متطوعون: ${record.volunteerCount} • حضور: ${record.attendanceCount} • " +
                                        "مشرفون: ${record.adminCount} • الحجم: ${formatSize(record.sizeBytes)}",
                                    style = MaterialTheme.typography.bodyMedium,
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
