package com.vigilante.app.ui.volunteers

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.vigilante.app.R
import kotlinx.coroutines.launch
import com.vigilante.app.data.local.entity.Permission
import com.vigilante.app.data.local.entity.Volunteer
import com.vigilante.app.data.local.entity.VolunteerStatus
import com.vigilante.app.ui.components.ConfirmDialog
import com.vigilante.app.ui.components.LoadingBox
import com.vigilante.app.ui.components.SectionHeader
import com.vigilante.app.ui.components.VigilanteTopBar
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VolunteerDetailScreen(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    viewModel: VolunteerDetailViewModel = hiltViewModel()
) {
    val volunteer by viewModel.volunteer.collectAsState()
    val summary by viewModel.summary.collectAsState()
    val tags by viewModel.tags.collectAsState()
    val timeline by viewModel.timeline.collectAsState()
    val qrFile by viewModel.qrFile.collectAsState()
    val message by viewModel.message.collectAsState()
    val closed by viewModel.closed.collectAsState()

    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showAttendanceConfirm by remember { mutableStateOf(false) }
    var showArchiveDialog by remember { mutableStateOf(false) }
    var showDeleteFirst by remember { mutableStateOf(false) }
    var showDeleteSecond by remember { mutableStateOf(false) }

    val whatsappMissing = stringResource(R.string.whatsapp_not_installed)
    val phoneCopied = stringResource(R.string.phone_copied)

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }
    LaunchedEffect(closed) {
        if (closed) onBack()
    }

    Scaffold(
        topBar = { VigilanteTopBar(title = "صفحة المتطوع", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        val v = volunteer
        if (v == null) {
            LoadingBox(Modifier.padding(padding))
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val photo = viewModel.photoFile()
                if (photo != null) {
                    AsyncImage(
                        model = photo,
                        contentDescription = stringResource(R.string.photo),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(110.dp)
                            .clip(CircleShape)
                    )
                } else {
                    Surface(
                        modifier = Modifier.size(110.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Icon(
                            Icons.Filled.Person,
                            contentDescription = null,
                            modifier = Modifier.padding(24.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (qrFile != null) {
                    AsyncImage(
                        model = qrFile,
                        contentDescription = "QR",
                        modifier = Modifier.size(110.dp)
                    )
                }
            }

            Text(
                v.displayName,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(top = 12.dp)
            )
            AssistChip(
                onClick = {},
                label = {
                    Text(
                        if (v.status == VolunteerStatus.ACTIVE) stringResource(R.string.status_active)
                        else stringResource(R.string.status_archived)
                    )
                }
            )

            // Quick actions
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActionButton(stringResource(R.string.call), Icons.Filled.Call) {
                    context.startActivity(
                        Intent(Intent.ACTION_DIAL, Uri.parse("tel:${v.phone1}"))
                    )
                }
                ActionButton(stringResource(R.string.whatsapp), Icons.Filled.Chat) {
                    try {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/${v.phone1}"))
                                .setPackage("com.whatsapp")
                        )
                    } catch (e: ActivityNotFoundException) {
                        scope.launch { snackbarHostState.showSnackbar(whatsappMissing) }
                    }
                }
                ActionButton(stringResource(R.string.copy_number), Icons.Filled.ContentCopy) {
                    clipboard.setText(AnnotatedString(v.phone1))
                    scope.launch { snackbarHostState.showSnackbar(phoneCopied) }
                }
                ActionButton(stringResource(R.string.share_card), Icons.Filled.Share) {
                    val share = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, viewModel.shareText(v))
                    }
                    context.startActivity(Intent.createChooser(share, "مشاركة بيانات المتطوع"))
                }
                if (v.status == VolunteerStatus.ACTIVE &&
                    viewModel.has(Permission.RECORD_ATTENDANCE)
                ) {
                    ActionButton(stringResource(R.string.record_attendance), Icons.Filled.HowToReg) {
                        showAttendanceConfirm = true
                    }
                }
                if (viewModel.has(Permission.EDIT_VOLUNTEER)) {
                    ActionButton(stringResource(R.string.edit), Icons.Filled.Edit) {
                        onEdit(v.volunteerId)
                    }
                }
                if (v.status == VolunteerStatus.ACTIVE &&
                    viewModel.has(Permission.ARCHIVE_VOLUNTEER)
                ) {
                    ActionButton(stringResource(R.string.archive_action), Icons.Filled.Archive) {
                        showArchiveDialog = true
                    }
                }
                if (v.status == VolunteerStatus.ARCHIVED &&
                    viewModel.has(Permission.RESTORE_FROM_ARCHIVE)
                ) {
                    ActionButton(stringResource(R.string.restore_action), Icons.Filled.Restore) {
                        viewModel.restore()
                    }
                }
                if (v.status == VolunteerStatus.ARCHIVED &&
                    viewModel.has(Permission.PERMANENT_DELETE)
                ) {
                    ActionButton(
                        stringResource(R.string.delete_permanently),
                        Icons.Filled.DeleteForever
                    ) { showDeleteFirst = true }
                }
            }

            HorizontalDivider()
            SectionHeader("البيانات")
            Card {
                Column(modifier = Modifier.padding(12.dp)) {
                    DetailRow(stringResource(R.string.volunteer_id), v.volunteerId)
                    DetailRow(stringResource(R.string.membership_number), v.membershipNumber)
                    DetailRow(stringResource(R.string.first_name), v.firstName)
                    DetailRow(stringResource(R.string.last_name), v.lastName)
                    DetailRow(stringResource(R.string.father_name), v.fatherName)
                    DetailRow(stringResource(R.string.birth_date), v.birthDate?.toString())
                    DetailRow(stringResource(R.string.join_date), v.joinDate.toString())
                    DetailRow(stringResource(R.string.municipality), v.municipality)
                    DetailRow(stringResource(R.string.district), v.district)
                    DetailRow(stringResource(R.string.blood_group), v.bloodGroup)
                    DetailRow(stringResource(R.string.phone1), v.phone1)
                    DetailRow(stringResource(R.string.phone2), v.phone2)
                    DetailRow(stringResource(R.string.notes), v.notes)
                    if (v.status == VolunteerStatus.ARCHIVED) {
                        DetailRow("تاريخ الأرشفة", v.archiveDate?.format(dateTimeFmt))
                        DetailRow("أرشفه", v.archivedBy)
                        DetailRow("سبب الأرشفة", v.archiveReason)
                    }
                }
            }

            if (tags.isNotEmpty()) {
                SectionHeader(stringResource(R.string.tags))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    tags.forEach { tag ->
                        AssistChip(onClick = {}, label = { Text(tag.name) })
                    }
                }
            }

            SectionHeader("ملخص الحضور")
            Card {
                Column(modifier = Modifier.padding(12.dp)) {
                    DetailRow("عدد مرات الحضور", summary.count.toString())
                    DetailRow("أول حضور", summary.first?.format(dateTimeFmt))
                    DetailRow("آخر حضور", summary.last?.format(dateTimeFmt))
                }
            }

            SectionHeader(stringResource(R.string.timeline))
            if (timeline.isEmpty()) {
                Text(
                    "لا توجد عمليات مسجلة",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Card {
                    Column(modifier = Modifier.padding(12.dp)) {
                        timeline.forEach { log ->
                            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                                Text(
                                    "${log.timestamp.format(dateTimeFmt)} — ${log.adminUsername}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(log.details, style = MaterialTheme.typography.bodyMedium)
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        if (showAttendanceConfirm) {
            ConfirmDialog(
                title = stringResource(R.string.record_attendance),
                text = stringResource(R.string.attendance_confirm),
                onConfirm = {
                    showAttendanceConfirm = false
                    viewModel.recordAttendance()
                },
                onDismiss = { showAttendanceConfirm = false }
            )
        }

        if (showArchiveDialog) {
            var reason by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showArchiveDialog = false },
                title = { Text(stringResource(R.string.archive_action)) },
                text = {
                    Column {
                        Text(stringResource(R.string.archive_confirm))
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = reason,
                            onValueChange = { reason = it },
                            label = { Text(stringResource(R.string.archive_reason)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        showArchiveDialog = false
                        viewModel.archive(reason)
                    }) { Text(stringResource(R.string.yes)) }
                },
                dismissButton = {
                    TextButton(onClick = { showArchiveDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }

        if (showDeleteFirst) {
            ConfirmDialog(
                title = stringResource(R.string.delete_permanently),
                text = "هل تريد حذف هذا المتطوع نهائيًا؟ سينتقل إلى سلة المحذوفات أولًا.",
                destructive = true,
                onConfirm = {
                    showDeleteFirst = false
                    showDeleteSecond = true
                },
                onDismiss = { showDeleteFirst = false }
            )
        }
        if (showDeleteSecond) {
            ConfirmDialog(
                title = "تأكيد نهائي",
                text = "هذه العملية حساسة. هل أنت متأكد تمامًا من الحذف النهائي؟",
                confirmLabel = "حذف نهائي",
                destructive = true,
                onConfirm = {
                    showDeleteSecond = false
                    viewModel.deletePermanently()
                },
                onDismiss = { showDeleteSecond = false }
            )
        }
    }
}

private val dateTimeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")

@Composable
private fun ActionButton(label: String, icon: ImageVector, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(6.dp))
        Text(label)
    }
}

@Composable
private fun DetailRow(label: String, value: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value?.takeIf { it.isNotBlank() } ?: "—",
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
