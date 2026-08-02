package com.vigilante.app.ui.attendance

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vigilante.app.R
import com.vigilante.app.data.local.entity.AttendanceStatus
import com.vigilante.app.ui.components.EmptyState
import com.vigilante.app.ui.components.VigilanteTopBar
import java.time.format.DateTimeFormatter

private val timeFmt = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AttendanceLogScreen(
    onBack: () -> Unit,
    viewModel: AttendanceLogViewModel = hiltViewModel()
) {
    val query by viewModel.query.collectAsState()
    val rows by viewModel.rows.collectAsState()
    val message by viewModel.message.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var cancelTarget by remember { mutableStateOf<AttendanceRow?>(null) }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = { VigilanteTopBar(title = "سجل الحضور", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::setQuery,
                placeholder = { Text("ابحث بالاسم أو المشرف أو التاريخ…") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
            if (rows.isEmpty()) {
                EmptyState(text = stringResource(R.string.no_results), icon = Icons.Filled.EventBusy)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    items(rows, key = { it.attendance.attendanceId }) { row ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {},
                                    onLongClick = {
                                        if (row.attendance.status == AttendanceStatus.VALID) {
                                            cancelTarget = row
                                        }
                                    }
                                )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(row.volunteerName, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        row.attendance.recordedAt.format(timeFmt) +
                                            " — " + row.attendance.adminUsername,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    row.attendance.notes?.let {
                                        Text(
                                            it,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (row.attendance.status == AttendanceStatus.CANCELLED) {
                                    Text(
                                        "ملغى",
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        cancelTarget?.let { target ->
            var note by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { cancelTarget = null },
                title = { Text("إلغاء سجل الحضور") },
                text = {
                    Column {
                        Text("سيتم إلغاء هذا السجل منطقيًا دون حذفه. اكتب سبب الإلغاء:")
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = note,
                            onValueChange = { note = it },
                            label = { Text("سبب الإلغاء") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.cancel(target.attendance.attendanceId, note)
                        cancelTarget = null
                    }) { Text("إلغاء السجل", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { cancelTarget = null }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }
    }
}
