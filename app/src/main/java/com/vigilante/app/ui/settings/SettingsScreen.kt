package com.vigilante.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vigilante.app.BuildConfig
import com.vigilante.app.R
import com.vigilante.app.ui.components.LoadingBox
import com.vigilante.app.ui.components.SectionHeader
import com.vigilante.app.ui.components.VigilanteTopBar
import com.vigilante.app.ui.login.APP_VERSION

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenAuditLog: () -> Unit,
    onOpenRecycleBin: () -> Unit,
    onOpenSystemHealth: () -> Unit,
    onOpenPlaces: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val message by viewModel.message.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showPasswordDialog by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = { VigilanteTopBar(title = stringResource(R.string.menu_settings), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (state.loading) {
            LoadingBox(Modifier.padding(padding))
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SectionHeader("معلومات الجمعية")
            Card {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = state.orgId,
                        onValueChange = {},
                        readOnly = true,
                        enabled = false,
                        label = { Text("معرف الجمعية (للقراءة فقط)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = state.orgName,
                        onValueChange = { value -> viewModel.update { it.copy(orgName = value) } },
                        label = { Text("اسم الجمعية") },
                        enabled = viewModel.canManageSettings(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = state.orgPhone,
                        onValueChange = { value -> viewModel.update { it.copy(orgPhone = value) } },
                        label = { Text("هاتف الجمعية") },
                        enabled = viewModel.canManageSettings(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = state.orgEmail,
                        onValueChange = { value -> viewModel.update { it.copy(orgEmail = value) } },
                        label = { Text("البريد الإلكتروني") },
                        enabled = viewModel.canManageSettings(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = state.orgAddress,
                        onValueChange = { value -> viewModel.update { it.copy(orgAddress = value) } },
                        label = { Text("العنوان") },
                        enabled = viewModel.canManageSettings(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (viewModel.canManageSettings()) {
                        Button(
                            onClick = viewModel::saveOrganization,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(stringResource(R.string.save)) }
                    }
                }
            }

            SectionHeader("الأمان")
            Card {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { showPasswordDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Lock, contentDescription = null)
                        Spacer(Modifier.padding(4.dp))
                        Text("تغيير كلمة المرور")
                    }
                    if (viewModel.canManageSettings()) {
                        OutlinedTextField(
                            value = state.lockSeconds,
                            onValueChange = { value ->
                                viewModel.update { it.copy(lockSeconds = value.filter(Char::isDigit)) }
                            },
                            label = { Text("مدة قفل تسجيل الدخول (ثوانٍ)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (BuildConfig.ATTENDANCE_ENABLED) {
                            OutlinedTextField(
                                value = state.duplicateSeconds,
                                onValueChange = { value ->
                                    viewModel.update { it.copy(duplicateSeconds = value.filter(Char::isDigit)) }
                                },
                                label = { Text("نافذة منع تكرار الحضور (ثوانٍ)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        OutlinedTextField(
                            value = state.sessionTimeoutMinutes,
                            onValueChange = { value ->
                                viewModel.update {
                                    it.copy(sessionTimeoutMinutes = value.filter(Char::isDigit))
                                }
                            },
                            label = { Text("مهلة انتهاء الجلسة (دقائق)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = state.recycleBinDays,
                            onValueChange = { value ->
                                viewModel.update { it.copy(recycleBinDays = value.filter(Char::isDigit)) }
                            },
                            label = { Text("مدة بقاء سلة المحذوفات (أيام)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = viewModel::saveSecurity,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(stringResource(R.string.save)) }

                        HorizontalDivider()
                        var showExcelPassword by remember { mutableStateOf(false) }
                        OutlinedTextField(
                            value = state.excelPassword,
                            onValueChange = { value ->
                                viewModel.update { it.copy(excelPassword = value) }
                            },
                            label = { Text("كلمة مرور ملفات Excel المصدَّرة") },
                            singleLine = true,
                            visualTransformation = if (showExcelPassword) VisualTransformation.None
                            else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showExcelPassword = !showExcelPassword }) {
                                    Icon(
                                        if (showExcelPassword) Icons.Filled.VisibilityOff
                                        else Icons.Filled.Visibility,
                                        contentDescription = stringResource(R.string.show_password)
                                    )
                                }
                            },
                            supportingText = {
                                Text("يُطلب إدخالها عند فتح الملف على الكمبيوتر")
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = viewModel::saveExcelPassword,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("حفظ كلمة مرور Excel") }
                    }
                }
            }

            if (viewModel.isSuperAdmin()) {
                Card {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.read_only_mode),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                "عند التفعيل تُمنع جميع عمليات التعديل",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = state.readOnlyMode,
                            onCheckedChange = viewModel::setReadOnlyMode
                        )
                    }
                }
            }

            SectionHeader("روابط")
            Card {
                Column {
                    if (viewModel.canViewAuditLog()) {
                        LinkRow(
                            stringResource(R.string.audit_log),
                            Icons.AutoMirrored.Filled.ListAlt,
                            onOpenAuditLog
                        )
                        HorizontalDivider()
                    }
                    if (viewModel.canSeeRecycleBin()) {
                        LinkRow(
                            stringResource(R.string.recycle_bin),
                            Icons.Filled.Delete,
                            onOpenRecycleBin
                        )
                        HorizontalDivider()
                    }
                    if (viewModel.canManagePlaces()) {
                        LinkRow(
                            "إدارة الأماكن",
                            Icons.Filled.Place,
                            onOpenPlaces
                        )
                        HorizontalDivider()
                    }
                    LinkRow(
                        stringResource(R.string.system_health),
                        Icons.Filled.HealthAndSafety,
                        onOpenSystemHealth
                    )
                }
            }

            SectionHeader(stringResource(R.string.about))
            Card {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column(modifier = Modifier.padding(start = 12.dp)) {
                        Text(
                            stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            stringResource(R.string.version) + " " + APP_VERSION,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        if (showPasswordDialog) {
            var current by remember { mutableStateOf("") }
            var newPassword by remember { mutableStateOf("") }
            var confirm by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showPasswordDialog = false },
                title = { Text("تغيير كلمة المرور") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = current,
                            onValueChange = { current = it },
                            label = { Text("كلمة المرور الحالية") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = newPassword,
                            onValueChange = { newPassword = it },
                            label = { Text("كلمة المرور الجديدة") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = confirm,
                            onValueChange = { confirm = it },
                            label = { Text(stringResource(R.string.confirm_password)) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.changePassword(current, newPassword, confirm)
                        showPasswordDialog = false
                    }) { Text(stringResource(R.string.save)) }
                },
                dismissButton = {
                    TextButton(onClick = { showPasswordDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }
    }
}

@Composable
private fun LinkRow(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(start = 12.dp)
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
