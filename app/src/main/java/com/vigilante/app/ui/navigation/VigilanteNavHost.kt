package com.vigilante.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.vigilante.app.ui.admins.AdminsScreen
import com.vigilante.app.ui.archive.ArchiveScreen
import com.vigilante.app.ui.attendance.AttendanceLogScreen
import com.vigilante.app.ui.attendance.AttendanceScreen
import com.vigilante.app.ui.backup.BackupScreen
import com.vigilante.app.ui.backup.ImportFlowScreen
import com.vigilante.app.ui.home.HomeScreen
import com.vigilante.app.ui.login.FirstRunScreen
import com.vigilante.app.ui.login.LoginScreen
import com.vigilante.app.ui.login.SplashContent
import com.vigilante.app.ui.settings.AuditLogScreen
import com.vigilante.app.ui.settings.PlacesScreen
import com.vigilante.app.ui.settings.RecycleBinScreen
import com.vigilante.app.ui.settings.SettingsScreen
import com.vigilante.app.ui.settings.SystemHealthScreen
import com.vigilante.app.ui.stats.StatsScreen
import com.vigilante.app.ui.volunteers.VolunteerDetailScreen
import com.vigilante.app.ui.volunteers.VolunteerEditScreen
import com.vigilante.app.ui.volunteers.VolunteerListScreen

sealed class Route(val route: String) {
    data object Splash : Route("splash")
    data object FirstRun : Route("first_run")
    data object Login : Route("login")
    data object Home : Route("home")
    data object VolunteerList : Route("volunteers")
    data object VolunteerDetail : Route("volunteer/{id}") {
        fun of(id: String) = "volunteer/$id"
    }
    data object VolunteerEdit : Route("volunteer_edit?id={id}") {
        fun of(id: String?) = if (id.isNullOrBlank()) "volunteer_edit" else "volunteer_edit?id=$id"
    }
    data object Attendance : Route("attendance")
    data object AttendanceLog : Route("attendance_log")
    data object Stats : Route("stats")
    data object Archive : Route("archive")
    data object Admins : Route("admins")
    data object Settings : Route("settings")
    data object Backup : Route("backup")
    data object AuditLog : Route("audit_log")
    data object Places : Route("places")
    data object RecycleBin : Route("recycle_bin")
    data object SystemHealth : Route("system_health")
    data object ImportFlow : Route("import_flow")
}

@Composable
fun VigilanteNavHost(
    navController: NavHostController,
    startDestination: String,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(Route.Splash.route) { SplashContent() }

        composable(Route.FirstRun.route) {
            FirstRunScreen(
                onCreated = {
                    navController.navigate(Route.Login.route) {
                        popUpTo(Route.FirstRun.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Route.Login.route) {
            LoginScreen(
                onLoggedIn = {
                    navController.navigate(Route.Home.route) {
                        popUpTo(Route.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Route.Home.route) {
            HomeScreen(
                onNavigate = { route -> navController.navigate(route) },
                onLogout = {
                    navController.navigate(Route.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Route.VolunteerList.route) {
            VolunteerListScreen(
                onBack = { navController.popBackStack() },
                onOpenVolunteer = { id -> navController.navigate(Route.VolunteerDetail.of(id)) },
                onAddVolunteer = { navController.navigate(Route.VolunteerEdit.of(null)) }
            )
        }

        composable(
            Route.VolunteerDetail.route,
            arguments = listOf(navArgument("id") { type = NavType.StringType })
        ) {
            VolunteerDetailScreen(
                onBack = { navController.popBackStack() },
                onEdit = { id -> navController.navigate(Route.VolunteerEdit.of(id)) }
            )
        }

        composable(
            Route.VolunteerEdit.route,
            arguments = listOf(
                navArgument("id") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) {
            VolunteerEditScreen(onDone = { navController.popBackStack() })
        }

        composable(Route.Attendance.route) {
            AttendanceScreen(
                onBack = { navController.popBackStack() },
                onOpenLog = { navController.navigate(Route.AttendanceLog.route) },
                onOpenVolunteer = { id -> navController.navigate(Route.VolunteerDetail.of(id)) }
            )
        }

        composable(Route.AttendanceLog.route) {
            AttendanceLogScreen(onBack = { navController.popBackStack() })
        }

        composable(Route.Stats.route) {
            StatsScreen(onBack = { navController.popBackStack() })
        }

        composable(Route.Archive.route) {
            ArchiveScreen(
                onBack = { navController.popBackStack() },
                onOpenVolunteer = { id -> navController.navigate(Route.VolunteerDetail.of(id)) }
            )
        }

        composable(Route.Admins.route) {
            AdminsScreen(onBack = { navController.popBackStack() })
        }

        composable(Route.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenAuditLog = { navController.navigate(Route.AuditLog.route) },
                onOpenRecycleBin = { navController.navigate(Route.RecycleBin.route) },
                onOpenSystemHealth = { navController.navigate(Route.SystemHealth.route) },
                onOpenPlaces = { navController.navigate(Route.Places.route) }
            )
        }

        composable(Route.Backup.route) {
            BackupScreen(
                onBack = { navController.popBackStack() },
                onOpenImport = { navController.navigate(Route.ImportFlow.route) }
            )
        }

        composable(Route.AuditLog.route) {
            AuditLogScreen(onBack = { navController.popBackStack() })
        }

        composable(Route.Places.route) {
            PlacesScreen(onBack = { navController.popBackStack() })
        }

        composable(Route.RecycleBin.route) {
            RecycleBinScreen(onBack = { navController.popBackStack() })
        }

        composable(Route.SystemHealth.route) {
            SystemHealthScreen(onBack = { navController.popBackStack() })
        }

        composable(Route.ImportFlow.route) {
            ImportFlowScreen(onBack = { navController.popBackStack() })
        }
    }
}
