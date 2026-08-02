package com.vigilante.app.ui.admins

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vigilante.app.R
import com.vigilante.app.data.local.entity.Admin
import com.vigilante.app.data.local.entity.AdminRole
import com.vigilante.app.ui.components.ConfirmDialog
import com.vigilante.app.ui.components.EmptyState
import com.vigilante.app.ui.components.VigilanteTopBar
import com.vigilante.app.ui.volunteers.DropdownField
import java.time.format.DateTimeFormatter

private val dateTimeFmt = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")

@Composable
fun AdminsScreen(
    onBack: () -> Unit,
    viewModel: AdminsViewModel = hiltViewModel()
) {
    val admins by viewModel.admins.collectAsState()
    val message by viewModel.message.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var showAddDialog by remember { mutableStateOf(false) }
    var toggleTarget by remember { mutableStateOf<Admin?>(null) }
    var resetTarget by remember { mutableStateOf<Admin?>(null) }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = { VigilanteTopBar(title = stringResource(R.string.menu_admins), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (viewModel.canManage()) {
                ExtendedFloatingActionButton(
                    onClick = { showAddDialog = true },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.add_admin)) }
                )
            }
        }
    ) { padding ->
        if (admins.isEmpty()) {
            EmptyState(
                text = "لا يوجد مشرفون",
                icon = Icons.Filled.ManageAccounts,
                modifier = Modifier.padding(padding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp)
            ) {
                items(admins, key = { it.adminId }) { admin ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(admin.fullName, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        admin.username,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                AssistChip(
                                    onClick = {},
                                    label = {
                                        Text(
                                            if (admin.role == AdminRole.SUPER_ADMIN)
                                                stringResource(R.string.role_superadmin)
                                            else stringResource(R.string.role_admin)
                                        )
                                    }
                                )
                            }
                            Text(
                                stringResource(R.string.last_login) + ": " +
                                    (admin.lastLogin?.format(dateTimeFmt) ?: "—"),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            if (viewModel.canManage()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Switch(
                                            checked = admin.active,
                                            onCheckedChange = { toggleTarget = admin }
                                        )
                                        Text(
                                            if (admin.active) stringResource(R.string.account_active)
                                            else stringResource(R.string.account_disabled),
                                            style = MaterialTheme.typography.labelMedium,
                                            modifier = Modifier.padding(start = 6.dp)
                                        )
                                    }
                                    IconButton(onClick = { resetTarget = admin }) {
                                        Icon(
                                            Icons.Filled.LockReset,
                                            contentDescription = stringResource(R.string.reset_password)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showAddDialog) {
            AddAdminDialog(
                onAdd = { fullName, username, password, role ->
                    viewModel.add(fullName, username, password, role)
                    showAddDialog = false
                },
                onDismiss = { showAddDialog = false }
            )
        }

        toggleTarget?.let { target ->
            ConfirmDialog(
                title = if (target.active) "تعطيل الحساب" else "تفعيل الحساب",
                text = if (target.active)
                    "هل تريد تعطيل حساب ${target.fullName}؟"
                else "هل تريد تفعيل حساب ${target.fullName}؟",
                destructive = target.active,
                onConfirm = {
                    viewModel.setActive(target.adminId, !target.active)
                    toggleTarget = null
                },
                onDismiss = { toggleTarget = null }
            )
        }

        resetTarget?.let { target ->
            var newPassword by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { resetTarget = null },
                title = { Text(stringResource(R.string.reset_password)) },
                text = {
                    Column {
                        Text("كلمة مرور جديدة لحساب ${target.username}")
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = newPassword,
                            onValueChange = { newPassword = it },
                            label = { Text(stringResource(R.string.password)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.resetPassword(target.adminId, newPassword)
                        resetTarget = null
                    }) { Text(stringResource(R.string.ok)) }
                },
                dismissButton = {
                    TextButton(onClick = { resetTarget = null }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }
    }
}

@Composable
private fun AddAdminDialog(
    onAdd: (String, String, String, AdminRole) -> Unit,
    onDismiss: () -> Unit
) {
    var fullName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val roleAdmin = stringResource(R.string.role_admin)
    val roleSuper = stringResource(R.string.role_superadmin)
    var roleLabel by remember { mutableStateOf(roleAdmin) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_admin)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = { Text("الاسم الكامل") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(stringResource(R.string.username)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.password)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                DropdownField(
                    label = "الدور",
                    selected = roleLabel,
                    options = listOf(roleAdmin, roleSuper),
                    onSelected = { roleLabel = it }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onAdd(
                    fullName, username, password,
                    if (roleLabel == roleSuper) AdminRole.SUPER_ADMIN else AdminRole.ADMIN
                )
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
