package com.vigilante.app.ui.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vigilante.app.R
import com.vigilante.app.data.excel.ConflictResolution
import com.vigilante.app.data.excel.MergeItem
import com.vigilante.app.ui.components.VigilanteTopBar
import java.time.format.DateTimeFormatter

private val dateTimeFmt = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")

@Composable
fun ImportFlowScreen(
    onBack: () -> Unit,
    viewModel: ImportViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val message by viewModel.message.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.onFilePicked(uri)
    }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            VigilanteTopBar(title = stringResource(R.string.import_excel), onBack = onBack)
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (state) {
                is ImportUiState.Working -> {
                    Spacer(Modifier.height(48.dp))
                    CircularProgressIndicator()
                    Text(
                        "جارٍ المعالجة… يتم إنشاء نسخة احتياطية تلقائية قبل أي تعديل",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
                else -> {
                    Text(
                        "اختر ملف Volunteers_Import.xlsx لبدء الاستيراد.\n" +
                            "سيتم فحص الملف والتحقق من البيانات قبل أي دمج، " +
                            "مع إنشاء نسخة احتياطية تلقائية.",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                    Button(
                        onClick = {
                            filePicker.launch(
                                arrayOf(
                                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                    "application/octet-stream"
                                )
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        Icon(Icons.Filled.UploadFile, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("اختيار ملف", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }

        when (val s = state) {
            is ImportUiState.Failed -> {
                AlertDialog(
                    onDismissRequest = viewModel::cancelFlow,
                    title = { Text("تعذر الاستيراد") },
                    text = { Text(s.reason) },
                    confirmButton = {
                        TextButton(onClick = viewModel::cancelFlow) {
                            Text(stringResource(R.string.ok))
                        }
                    }
                )
            }
            is ImportUiState.Errors -> {
                AlertDialog(
                    onDismissRequest = viewModel::cancelFlow,
                    title = { Text("أخطاء في الملف") },
                    text = {
                        LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                            items(s.errors) { error ->
                                Text(
                                    "سطر ${error.rowNumber} (${error.sheet}): ${error.message}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = viewModel::cancelFlow) {
                            Text(stringResource(R.string.ok))
                        }
                    }
                )
            }
            is ImportUiState.OrgWarning -> {
                AlertDialog(
                    onDismissRequest = viewModel::cancelFlow,
                    title = { Text("تنبيه: جمعية مختلفة") },
                    text = {
                        Text(
                            "هذا الملف يعود لجمعية مختلفة.\n" +
                                "معرف الملف: ${s.fileOrgId}\n" +
                                "معرف الجمعية الحالية: ${s.localOrgId}\n\n" +
                                "هل تريد المتابعة رغم ذلك؟"
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = viewModel::proceedDespiteOrgMismatch) {
                            Text(stringResource(R.string.continue_anyway))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = viewModel::cancelFlow) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                )
            }
            is ImportUiState.PlanReady -> {
                AlertDialog(
                    onDismissRequest = viewModel::cancelFlow,
                    title = { Text("ملخص الدمج") },
                    text = {
                        Column {
                            SummaryRow("جدد", s.plan.newCount)
                            SummaryRow("معدلون تلقائيًا", s.plan.autoApplyCount)
                            SummaryRow("متجاهلون تلقائيًا", s.plan.autoIgnoreCount)
                            SummaryRow("بدون تغيير", s.plan.unchangedCount)
                            SummaryRow("تعارضات تتطلب قرارًا", s.plan.conflictCount)
                            SummaryRow("سجلات حضور جديدة", s.plan.newAttendance.size)
                            SummaryRow("مشرفون جدد", s.plan.newAdmins.size)
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = viewModel::confirmPlan) {
                            Text("تنفيذ الدمج")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = viewModel::cancelFlow) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                )
            }
            is ImportUiState.Resolving -> {
                val item = s.conflicts[s.index]
                ConflictDialog(
                    item = item,
                    position = s.index + 1,
                    total = s.conflicts.size,
                    onKeepCurrent = {
                        viewModel.resolveCurrentConflict(ConflictResolution.KEEP_CURRENT)
                    },
                    onTakeImported = {
                        viewModel.resolveCurrentConflict(ConflictResolution.TAKE_IMPORTED)
                    }
                )
            }
            is ImportUiState.Done -> {
                AlertDialog(
                    onDismissRequest = onBack,
                    title = { Text(stringResource(R.string.import_report_title)) },
                    text = {
                        Column {
                            SummaryRow("إضافات", s.report.added)
                            SummaryRow("تعديلات", s.report.updated)
                            SummaryRow("تجاهلات", s.report.ignored)
                            SummaryRow("سجلات حضور", s.report.attendanceAdded)
                            SummaryRow("مشرفون", s.report.adminsAdded)
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            Text(
                                "النسخة الاحتياطية: ${s.report.backupFileName}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = onBack) { Text(stringResource(R.string.ok)) }
                    }
                )
            }
            else -> Unit
        }
    }
}

@Composable
private fun SummaryRow(label: String, count: Int) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            count.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun ConflictDialog(
    item: MergeItem,
    position: Int,
    total: Int,
    onKeepCurrent: () -> Unit,
    onTakeImported: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("تعارض $position من $total") },
        text = {
            Column {
                Text(
                    item.reason,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Card {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("النسخة الحالية", style = MaterialTheme.typography.titleMedium)
                        item.current?.let { current ->
                            Text(current.displayName)
                            Text("هاتف: ${current.phone1}" + (current.phone2?.let { " / $it" } ?: ""))
                            Text(
                                "آخر تعديل: ${current.updatedBy ?: current.createdBy} — " +
                                    (current.updatedAt?.format(dateTimeFmt)
                                        ?: current.createdAt.format(dateTimeFmt)),
                                style = MaterialTheme.typography.labelMedium
                            )
                        } ?: Text("—")
                    }
                }
                Spacer(Modifier.height(8.dp))
                Card {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("النسخة المستوردة", style = MaterialTheme.typography.titleMedium)
                        Text(item.incoming.displayName)
                        Text(
                            "هاتف: ${item.incoming.phone1}" +
                                (item.incoming.phone2?.let { " / $it" } ?: "")
                        )
                        Text(
                            "آخر تعديل: ${item.incoming.updatedBy ?: item.incoming.createdBy} — " +
                                (item.incoming.updatedAt?.format(dateTimeFmt)
                                    ?: item.incoming.createdAt.format(dateTimeFmt)),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onTakeImported) { Text("اعتماد المستوردة") }
        },
        dismissButton = {
            TextButton(onClick = onKeepCurrent) { Text("الاحتفاظ بالحالية") }
        }
    )
}
