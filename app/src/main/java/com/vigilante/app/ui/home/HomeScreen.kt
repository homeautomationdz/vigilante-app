package com.vigilante.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vigilante.app.BuildConfig
import com.vigilante.app.R
import com.vigilante.app.data.local.entity.Permission
import com.vigilante.app.ui.components.ConfirmDialog
import com.vigilante.app.ui.components.HeaderPanel
import com.vigilante.app.ui.components.MenuTile
import com.vigilante.app.ui.components.StatCard
import com.vigilante.app.ui.navigation.Route
import com.vigilante.app.ui.theme.AppColors
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private data class MenuEntry(
    val label: String,
    val icon: ImageVector,
    val route: String
)

private data class HomeStat(
    val label: String,
    val value: String,
    val icon: ImageVector
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
            add(MenuEntry("الإحصائيات", Icons.Filled.Insights, Route.Stats.route))
            add(MenuEntry("الأرشيف", Icons.Filled.Inventory2, Route.Archive.route))
            if (viewModel.has(Permission.MANAGE_ADMINS)) {
                add(MenuEntry("المشرفون", Icons.Filled.AdminPanelSettings, Route.Admins.route))
            }
            add(MenuEntry("الإعدادات", Icons.Filled.Settings, Route.Settings.route))
            add(MenuEntry("النسخ الاحتياطي", Icons.Filled.Backup, Route.Backup.route))
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

    val c = AppColors.current
    val stats = buildList {
        add(
            HomeStat(
                stringResource(R.string.home_volunteers_count),
                activeCount.toString(),
                Icons.Filled.Groups
            )
        )
        add(
            HomeStat(
                stringResource(R.string.home_archived_count),
                archivedCount.toString(),
                Icons.Filled.Inventory2
            )
        )
        if (BuildConfig.ATTENDANCE_ENABLED) {
            add(
                HomeStat(
                    stringResource(R.string.home_attendance_today),
                    attendanceToday.toString(),
                    Icons.Filled.HowToReg
                )
            )
        }
        add(
            HomeStat(
                stringResource(R.string.home_joined_this_year),
                joinedThisYear.toString(),
                Icons.Filled.PersonAdd
            )
        )
    }

    val adminName = sessionState?.admin?.fullName ?: ""
    val roleLabel =
        if (sessionState?.admin?.role?.name == "SUPER_ADMIN") "مدير النظام" else "مشرف"

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = padding.calculateBottomPadding())
        ) {
            // Navy behind the status bar so the header reads as one panel.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(c.headerStart)
                    .statusBarsPadding()
            )

            HeaderPanel(
                title = stringResource(R.string.app_label),
                subtitle = if (adminName.isBlank()) roleLabel else "$adminName · $roleLabel",
                trailing = {
                    IconButton(onClick = { showLogoutConfirm = true }) {
                        Icon(
                            Icons.AutoMirrored.Filled.Logout,
                            contentDescription = "تسجيل الخروج",
                            tint = c.onHeader
                        )
                    }
                },
                content = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "$today · $time",
                            style = MaterialTheme.typography.labelMedium,
                            color = c.onHeaderMuted
                        )
                        stats.chunked(2).forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                row.forEach { stat ->
                                    StatCard(
                                        title = stat.label,
                                        value = stat.value,
                                        icon = stat.icon,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(menu) { entry ->
                    MenuTile(
                        label = entry.label,
                        icon = entry.icon,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onNavigate(entry.route) }
                    )
                }
            }
        }
    }
}
