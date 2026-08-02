package com.vigilante.app.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vigilante.app.BuildConfig
import com.vigilante.app.R
import com.vigilante.app.data.local.entity.Permission
import com.vigilante.app.ui.components.ConfirmDialog
import com.vigilante.app.ui.components.StatCard
import com.vigilante.app.ui.components.VigilanteTopBar
import com.vigilante.app.ui.navigation.Route
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private data class MenuEntry(
    val label: String,
    val icon: ImageVector,
    val route: String
)

@Composable
fun HomeScreen(
    onNavigate: (String) -> Unit,
    onLogout: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val sessionState by viewModel.sessionState.collectAsState()
    val activeCount by viewModel.activeCount.collectAsState()
    val archivedCount by viewModel.archivedCount.collectAsState()
    val attendanceToday by viewModel.attendanceToday.collectAsState()
    val joinedThisYear by viewModel.joinedThisYear.collectAsState()
    var showLogoutConfirm by remember { mutableStateOf(false) }

    val today = remember { LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd")) }
    val time = remember { LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")) }

    val menu = remember(sessionState) {
        buildList {
            add(MenuEntry("المتطوعون", Icons.Filled.Groups, Route.VolunteerList.route))
            if (BuildConfig.ATTENDANCE_ENABLED) {
                add(MenuEntry("الحضور", Icons.Filled.HowToReg, Route.Attendance.route))
            }
            add(MenuEntry("الإحصائيات", Icons.Filled.BarChart, Route.Stats.route))
            add(MenuEntry("الأرشيف", Icons.Filled.Archive, Route.Archive.route))
            if (viewModel.has(Permission.MANAGE_ADMINS)) {
                add(MenuEntry("المشرفون", Icons.Filled.ManageAccounts, Route.Admins.route))
            }
            add(MenuEntry("الإعدادات", Icons.Filled.Settings, Route.Settings.route))
            add(MenuEntry("النسخ الاحتياطي", Icons.Filled.Save, Route.Backup.route))
        }
    }

    if (showLogoutConfirm) {
        ConfirmDialog(
            title = "تسجيل الخروج",
            text = "هل تريد تسجيل الخروج من التطبيق؟",
            onConfirm = {
                showLogoutConfirm = false
                viewModel.logout(onLogout)
            },
            onDismiss = { showLogoutConfirm = false }
        )
    }

    Scaffold(
        topBar = {
            VigilanteTopBar(
                title = stringResource(R.string.app_label),
                actions = {
                    IconButton(onClick = { showLogoutConfirm = true }) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "تسجيل الخروج")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        sessionState?.admin?.fullName ?: "",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        if (sessionState?.admin?.role?.name == "SUPER_ADMIN") "مدير النظام" else "مشرف",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(today, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        time,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatCard(
                    title = stringResource(R.string.home_volunteers_count),
                    value = activeCount.toString(),
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = stringResource(R.string.home_archived_count),
                    value = archivedCount.toString(),
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (BuildConfig.ATTENDANCE_ENABLED) {
                    StatCard(
                        title = stringResource(R.string.home_attendance_today),
                        value = attendanceToday.toString(),
                        modifier = Modifier.weight(1f)
                    )
                }
                StatCard(
                    title = stringResource(R.string.home_joined_this_year),
                    value = joinedThisYear.toString(),
                    modifier = Modifier.weight(1f)
                )
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(menu) { entry ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigate(entry.route) },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 22.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                entry.icon,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                entry.label,
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
