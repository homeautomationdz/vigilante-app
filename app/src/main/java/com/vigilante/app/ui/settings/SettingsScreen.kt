package com.vigilante.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
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
import com.vigilante.app.ui.components.VCard
import com.vigilante.app.ui.components.VigilanteTopBar
import com.vigilante.app.ui.login.APP_VERSION
import com.vigilante.app.ui.theme.AppColors

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
                .padding(horizontal = 16.dp)
        ) {
            SettingsSection("معلومات الجمعية") {
                OutlinedTextField(
                    value = state.orgId,
                    onValueChange = {},
                    readOnly = true,
                    enabled = false,
                    label = { Text("معرف الجمعية (للقراءة فقط)") },
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = state.orgName,
                    onValueChange = { value -> viewModel.update { it.copy(orgName = value) } },
                    label = { Text("اسم الجمعية") },
                    enabled = viewModel.canManageSettings(),
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = state.orgPhone,
                    onValueChange = { value -> viewModel.update { it.copy(orgPhone = value) } },
                    label = { Text("هاتف الجمعية") },
                    enabled = viewModel.canManageSettings(),
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = state.orgEmail,
                    onValueChange = { value -> viewModel.update { it.copy(orgEmail = value) } },
                    label = { Text("البريد الإلكتروني") },
                    enabled = viewModel.canManageSettings(),
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = state.orgAddress,
                    onValueChange = { value -> viewModel.update { it.copy(orgAddress = value) } },
                    label = { Text("العنوان") },
                    enabled = viewModel.canManageSettings(),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                )
                if (viewModel.canManageSettings()) {
                    Button(
                        onClick = viewModel::saveOrganization,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) { Text(stringResource(R.string.save)) }
                }
            }

            SettingsSection("الأمان") {
                OutlinedButton(
                    onClick = { showPasswordDialog = true },
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
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
                        shape = MaterialTheme.shapes.small,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (BuildConfig.ATTENDANCE_ENABLED) {
                        OutlinedTextField(
                            value = state.duplicateSeconds,
                            onValueChange = { value ->
                                viewModel.update {
                                    it.copy(duplicateSeconds = value.filter(Char::isDigit))
                                }
                            },
                            label = { Text("نافذة منع تكرار الحضور (ثوانٍ)") },
                            singleLine = true,
                            shape = MaterialTheme.shapes.small,
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
                        shape = MaterialTheme.shapes.small,
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
                        shape = MaterialTheme.shapes.small,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = viewModel::saveSecurity,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) { Text(stringResource(R.string.save)) }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        ) {
                            Text(
                                "تشفير الملفات المصدَّرة",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "افتراضيًا الملفات تُصدَّر بدون تشفير. " +
                                    "فعّل هذا الخيار فقط إذا كنت تشارك الملف خارج الفريق.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = state.encryptExports,
                            onCheckedChange = viewModel::setEncryptExports
                        )
                    }
                    var showExcelPassword by remember { mutableStateOf(false) }
                    OutlinedTextField(
                        value = state.excelPassword,
                        onValueChange = { value ->
                            viewModel.update { it.copy(excelPassword = value) }
                        },
                        label = { Text("كلمة مرور ملفات Excel المصدَّرة") },
                        enabled = state.encryptExports,
                        singleLine = true,
                        shape = MaterialTheme.shapes.small,
                        visualTransformation = if (showExcelPassword) VisualTransformation.None
                        else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(
                                onClick = { showExcelPassword = !showExcelPassword },
                                enabled = state.encryptExports
                            ) {
                                Icon(
                                    if (showExcelPassword) Icons.Filled.VisibilityOff
                                    else Icons.Filled.Visibility,
                                    contentDescription = stringResource(R.string.show_password)
                                )
                            }
                        },
                        supportingText = {
                            Text(
                                "تُطلب عند فتح الملف على الكمبيوتر، " +
                                    "وتُستخدم تلقائيًا عند إعادة استيراده.",
                                color = if (state.encryptExports)
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.outline
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = viewModel::saveExcelPassword,
                        enabled = state.encryptExports,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) { Text("حفظ كلمة مرور Excel") }
                }
            }

            if (viewModel.isSuperAdmin()) {
                SectionHeader("الوضع")
                VCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.read_only_mode),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
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
            VCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    if (viewModel.canViewAuditLog()) {
                        LinkRow(
                            stringResource(R.string.audit_log),
                            Icons.AutoMirrored.Filled.ListAlt,
                            onOpenAuditLog
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    if (viewModel.canSeeRecycleBin()) {
                        LinkRow(
                            stringResource(R.string.recycle_bin),
                            Icons.Filled.Delete,
                            onOpenRecycleBin
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    if (viewModel.canManagePlaces()) {
                        LinkRow(
                            "إدارة الأماكن",
                            Icons.Filled.Place,
                            onOpenPlaces
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    LinkRow(
                        stringResource(R.string.system_health),
                        Icons.Filled.HealthAndSafety,
                        onOpenSystemHealth
                    )
                }
            }

            SectionHeader(stringResource(R.string.about))
            VCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TileIcon(Icons.Filled.Info)
                    Column(modifier = Modifier.padding(start = 12.dp)) {
                        Text(
                            stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            stringResource(R.string.version) + " " + APP_VERSION,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
        }

        if (showPasswordDialog) {
            var current by remember { mutableStateOf("") }
            var newPassword by remember { mutableStateOf("") }
            var confirm by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showPasswordDialog = false },
                shape = MaterialTheme.shapes.large,
                title = { Text("تغيير كلمة المرور") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = current,
                            onValueChange = { current = it },
                            label = { Text("كلمة المرور الحالية") },
                            singleLine = true,
                            shape = MaterialTheme.shapes.small,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = newPassword,
                            onValueChange = { newPassword = it },
                            label = { Text("كلمة المرور الجديدة") },
                            singleLine = true,
                            shape = MaterialTheme.shapes.small,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = confirm,
                            onValueChange = { confirm = it },
                            label = { Text(stringResource(R.string.confirm_password)) },
                            singleLine = true,
                            shape = MaterialTheme.shapes.small,
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

/** SectionHeader + a VCard holding that group's controls. */
@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    SectionHeader(title)
    VCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

/** Icon in the tinted rounded square used across the design system. */
@Composable
private fun TileIcon(icon: ImageVector) {
    val c = AppColors.current
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(c.tileIconBg),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = c.tileIcon
        )
    }
}

@Composable
private fun LinkRow(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TileIcon(icon)
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
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
