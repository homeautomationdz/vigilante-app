package com.vigilante.app.ui.attendance

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.vigilante.app.R
import com.vigilante.app.ui.components.ChipTone
import com.vigilante.app.ui.components.EmptyState
import com.vigilante.app.ui.components.MenuTile
import com.vigilante.app.ui.components.SectionHeader
import com.vigilante.app.ui.components.StatusChip
import com.vigilante.app.ui.components.VCard
import com.vigilante.app.ui.components.VigilanteTopBar
import java.time.format.DateTimeFormatter

private val timeFmt = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")

@Composable
fun AttendanceScreen(
    onBack: () -> Unit,
    onOpenLog: () -> Unit,
    onOpenRollCall: () -> Unit,
    onOpenVolunteer: (String) -> Unit,
    viewModel: AttendanceViewModel = hiltViewModel()
) {
    val recent by viewModel.recent.collectAsState()
    val dialog by viewModel.dialog.collectAsState()
    val message by viewModel.message.collectAsState()
    val manualOpen by viewModel.manualOpen.collectAsState()
    val manualQuery by viewModel.manualQuery.collectAsState()
    val manualResults by viewModel.manualResults.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val contents = result.contents
        if (contents != null) viewModel.onQrScanned(contents)
    }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            VigilanteTopBar(
                title = stringResource(R.string.menu_attendance),
                onBack = onBack,
                actions = {
                    IconButton(onClick = onOpenLog) {
                        Icon(
                            Icons.AutoMirrored.Filled.ListAlt,
                            contentDescription = "سجل الحضور"
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // Roll call is the primary way to take attendance: one list, one
            // tap per person present — no QR handling for a whole team.
            MenuTile(
                label = "نداء الحضور — تأشير من القائمة",
                icon = Icons.Filled.FactCheck,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
                onClick = onOpenRollCall
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MenuTile(
                    label = stringResource(R.string.scan_qr),
                    icon = Icons.Filled.QrCodeScanner,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        scanLauncher.launch(
                            ScanOptions()
                                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                .setBeepEnabled(true)
                                .setPrompt("وجّه الكاميرا نحو رمز QR الخاص بالمتطوع")
                        )
                    }
                )
                MenuTile(
                    label = stringResource(R.string.manual_attendance),
                    icon = Icons.Filled.PersonSearch,
                    modifier = Modifier.weight(1f),
                    onClick = viewModel::openManual
                )
            }

            SectionHeader("آخر تسجيلات الحضور", modifier = Modifier.padding(top = 10.dp))
            if (recent.isEmpty()) {
                EmptyState(text = "لا يوجد حضور مسجل بعد", icon = Icons.Filled.EventBusy)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(recent, key = { it.attendance.attendanceId }) { row ->
                        VCard(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onOpenVolunteer(row.attendance.volunteerId) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        row.volunteerName,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        row.attendance.recordedAt.format(timeFmt) +
                                            " — " + row.attendance.adminUsername,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (row.attendance.status.name == "CANCELLED") {
                                    StatusChip(text = "ملغى", tone = ChipTone.DANGER)
                                } else {
                                    StatusChip(text = "مسجل", tone = ChipTone.SUCCESS)
                                }
                            }
                        }
                    }
                }
            }
        }

        when (val d = dialog) {
            is AttendanceDialog.Confirm -> {
                AlertDialog(
                    onDismissRequest = viewModel::dismissDialog,
                    shape = MaterialTheme.shapes.large,
                    title = { Text(stringResource(R.string.attendance_found_title)) },
                    text = {
                        Text(
                            d.volunteer.displayName + "\n" +
                                stringResource(R.string.attendance_confirm)
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { viewModel.confirmRecord(d.volunteer.volunteerId) }) {
                            Text(stringResource(R.string.yes))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = viewModel::dismissDialog) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                )
            }
            is AttendanceDialog.Archived -> {
                AlertDialog(
                    onDismissRequest = viewModel::dismissDialog,
                    shape = MaterialTheme.shapes.large,
                    title = { Text(stringResource(R.string.qr_archived)) },
                    text = { Text(d.volunteer.displayName) },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.dismissDialog()
                            onOpenVolunteer(d.volunteer.volunteerId)
                        }) { Text(stringResource(R.string.view_data)) }
                    },
                    dismissButton = {
                        TextButton(onClick = viewModel::dismissDialog) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                )
            }
            AttendanceDialog.None -> Unit
        }

        if (manualOpen) {
            AlertDialog(
                onDismissRequest = viewModel::closeManual,
                shape = MaterialTheme.shapes.large,
                title = { Text(stringResource(R.string.manual_attendance)) },
                text = {
                    Column {
                        OutlinedTextField(
                            value = manualQuery,
                            onValueChange = viewModel::setManualQuery,
                            placeholder = { Text(stringResource(R.string.search_hint)) },
                            singleLine = true,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        )
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 320.dp)
                                .padding(top = 8.dp)
                        ) {
                            items(manualResults, key = { it.volunteerId }) { volunteer ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.pickManually(volunteer) }
                                        .heightIn(min = 48.dp)
                                        .padding(vertical = 8.dp)
                                ) {
                                    Text(
                                        volunteer.displayName,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        "${volunteer.volunteerId} • ${volunteer.phone1}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = viewModel::closeManual) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }
    }
}
