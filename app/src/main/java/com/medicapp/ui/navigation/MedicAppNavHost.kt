package com.medicapp.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.medicapp.ui.addedit.AddEditEntryScreen
import com.medicapp.ui.allergies.AllergiesScreen
import com.medicapp.ui.auth.LoginScreen
import com.medicapp.ui.auth.RegisterScreen
import com.medicapp.ui.dashboard.DashboardScreen
import com.medicapp.ui.history.HistoryScreen
import com.medicapp.ui.schedule.CreateScheduleScreen
import com.medicapp.ui.schedule.RemindersScreen
import com.medicapp.ui.schedule.ScheduleDetailScreen
import com.medicapp.ui.search.MedicationDetailScreen
import com.medicapp.ui.search.SearchScreen
import java.net.URLEncoder

private const val ROUTE_LOGIN = "login"
private const val ROUTE_REGISTER = "register"
private const val ROUTE_DASHBOARD = "dashboard"
private const val ROUTE_SEARCH = "search"
private const val ROUTE_ALLERGIES = "allergies"
private const val ROUTE_HISTORY = "history"
private const val ROUTE_REMINDERS = "reminders"
private const val ROUTE_MEDICATION_DETAIL = "medicationDetail/{nregistro}"
private const val ROUTE_ADD_EDIT = "addEdit?nregistro={nregistro}&nombre={nombre}&fotoUrl={fotoUrl}&entryId={entryId}"
private const val ROUTE_CREATE_SCHEDULE = "createSchedule?nregistro={nregistro}&nombre={nombre}&fotoUrl={fotoUrl}"
private const val ROUTE_SCHEDULE_DETAIL = "scheduleDetail/{scheduleId}"

private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

@Composable
fun MedicAppNavHost(
    initialScheduleId: String? = null,
    sessionViewModel: SessionViewModel = viewModel(factory = SessionViewModel.Factory),
) {
    val sessionState by sessionViewModel.sessionState.collectAsStateWithLifecycle()

    when (val state = sessionState) {
        SessionState.Loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        else -> {
            val startDestination = if (state == SessionState.LoggedIn) ROUTE_DASHBOARD else ROUTE_LOGIN
            MainNavHost(
                startDestination = startDestination,
                initialScheduleId = if (state == SessionState.LoggedIn) initialScheduleId else null,
                onSignOut = sessionViewModel::signOut,
            )
        }
    }
}

@Composable
private fun MainNavHost(startDestination: String, initialScheduleId: String?, onSignOut: () -> Unit) {
    val navController = rememberNavController()
    val bottomBarRoutes = setOf(ROUTE_SEARCH, ROUTE_ALLERGIES, ROUTE_HISTORY, ROUTE_REMINDERS)
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.hierarchy?.firstOrNull { it.route in bottomBarRoutes }?.route

    LaunchedEffect(initialScheduleId) {
        if (initialScheduleId != null) {
            navController.navigate("scheduleDetail/${encode(initialScheduleId)}")
        }
    }

    Scaffold(
        bottomBar = {
            if (currentRoute in bottomBarRoutes) {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentRoute == ROUTE_SEARCH,
                        onClick = {
                            navController.navigate(ROUTE_SEARCH) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(Icons.Default.Search, contentDescription = null) },
                        label = { Text("Buscar") },
                    )
                    NavigationBarItem(
                        selected = currentRoute == ROUTE_ALLERGIES,
                        onClick = {
                            navController.navigate(ROUTE_ALLERGIES) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(Icons.Default.Warning, contentDescription = null) },
                        label = { Text("Alergias") },
                    )
                    NavigationBarItem(
                        selected = currentRoute == ROUTE_HISTORY,
                        onClick = {
                            navController.navigate(ROUTE_HISTORY) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(Icons.Default.Folder, contentDescription = null) },
                        label = { Text("Medicamentos") },
                    )
                    NavigationBarItem(
                        selected = currentRoute == ROUTE_REMINDERS,
                        onClick = {
                            navController.navigate(ROUTE_REMINDERS) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(Icons.Default.Notifications, contentDescription = null) },
                        label = { Text("Recordatorios") },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
        ) {
            composable(ROUTE_LOGIN) {
                LoginScreen(
                    onLoggedIn = { navController.navigate(ROUTE_DASHBOARD) { popUpTo(0) } },
                    onNavigateToRegister = { navController.navigate(ROUTE_REGISTER) },
                )
            }
            composable(ROUTE_REGISTER) {
                RegisterScreen(
                    onRegistered = { navController.navigate(ROUTE_DASHBOARD) { popUpTo(0) } },
                    onNavigateToLogin = { navController.popBackStack() },
                )
            }
            composable(ROUTE_DASHBOARD) {
                DashboardScreen(
                    onNavigateSearch = { navController.navigate(ROUTE_SEARCH) },
                    onNavigateAllergies = { navController.navigate(ROUTE_ALLERGIES) },
                    onNavigateHistory = { navController.navigate(ROUTE_HISTORY) },
                    onNavigateReminders = { navController.navigate(ROUTE_REMINDERS) },
                    onLogout = {
                        onSignOut()
                        navController.navigate(ROUTE_LOGIN) { popUpTo(0) }
                    },
                )
            }
            composable(ROUTE_SEARCH) {
                SearchScreen(
                    contentPadding = innerPadding,
                    onOpenDetail = { medication ->
                        navController.navigate("medicationDetail/${encode(medication.nregistro)}")
                    },
                )
            }
            composable(
                route = ROUTE_MEDICATION_DETAIL,
                arguments = listOf(navArgument("nregistro") { type = NavType.StringType }),
            ) {
                MedicationDetailScreen(
                    onAddToHistory = { medication ->
                        val fotoUrlParam = medication.fotoUrl?.let { "&fotoUrl=${encode(it)}" }.orEmpty()
                        navController.navigate(
                            "addEdit?nregistro=${encode(medication.nregistro)}&nombre=${encode(medication.nombre)}$fotoUrlParam",
                        )
                    },
                    onCreateReminder = { medication ->
                        val fotoUrlParam = medication.fotoUrl?.let { "&fotoUrl=${encode(it)}" }.orEmpty()
                        navController.navigate(
                            "createSchedule?nregistro=${encode(medication.nregistro)}&nombre=${encode(medication.nombre)}$fotoUrlParam",
                        )
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(ROUTE_ALLERGIES) {
                AllergiesScreen(contentPadding = innerPadding)
            }
            composable(ROUTE_HISTORY) {
                HistoryScreen(
                    contentPadding = innerPadding,
                    onEditEntry = { entry -> navController.navigate("addEdit?entryId=${entry.id}") },
                    onOpenMedication = { entry ->
                        navController.navigate("medicationDetail/${encode(entry.nregistro)}")
                    },
                )
            }
            composable(ROUTE_REMINDERS) {
                RemindersScreen(
                    contentPadding = innerPadding,
                    onOpenSchedule = { schedule ->
                        navController.navigate("scheduleDetail/${encode(schedule.id)}")
                    },
                )
            }
            composable(
                route = ROUTE_SCHEDULE_DETAIL,
                arguments = listOf(navArgument("scheduleId") { type = NavType.StringType }),
            ) {
                ScheduleDetailScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = ROUTE_CREATE_SCHEDULE,
                arguments = listOf(
                    navArgument("nregistro") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("nombre") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("fotoUrl") { type = NavType.StringType; nullable = true; defaultValue = null },
                ),
            ) {
                CreateScheduleScreen(
                    onDone = { navController.popBackStack() },
                    onCancel = { navController.popBackStack() },
                )
            }
            composable(
                route = ROUTE_ADD_EDIT,
                arguments = listOf(
                    navArgument("nregistro") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("nombre") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("fotoUrl") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("entryId") { type = NavType.StringType; nullable = true; defaultValue = null },
                ),
            ) {
                AddEditEntryScreen(
                    onDone = { navController.popBackStack() },
                    onCancel = { navController.popBackStack() },
                )
            }
        }
    }
}
