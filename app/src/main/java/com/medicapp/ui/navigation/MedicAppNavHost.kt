package com.medicapp.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Today
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
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.medicapp.ui.history.HistoryScreen
import com.medicapp.ui.schedule.CreateScheduleScreen
import com.medicapp.ui.schedule.RemindersScreen
import com.medicapp.ui.schedule.ScheduleDetailScreen
import com.medicapp.ui.search.MedicationDetailScreen
import com.medicapp.ui.search.SearchScreen
import com.medicapp.ui.today.TodayScreen
import java.net.URLEncoder

private const val ROUTE_LOGIN = "login"
private const val ROUTE_REGISTER = "register"
private const val ROUTE_TODAY = "today"
private const val ROUTE_SEARCH = "search"
private const val ROUTE_ALLERGIES = "allergies"
private const val ROUTE_HISTORY = "history"
private const val ROUTE_REMINDERS = "reminders"
private const val ROUTE_MEDICATION_DETAIL = "medicationDetail/{nregistro}"
private const val ROUTE_ADD_EDIT = "addEdit?nregistro={nregistro}&nombre={nombre}&fotoUrl={fotoUrl}&entryId={entryId}"
private const val ROUTE_CREATE_SCHEDULE = "createSchedule?nregistro={nregistro}&nombre={nombre}&fotoUrl={fotoUrl}"
private const val ROUTE_SCHEDULE_DETAIL = "scheduleDetail/{scheduleId}"

/** Pestañas de la barra inferior. Recordatorios ya no es pestaña: se abre desde Hoy. */
private data class BottomTab(val route: String, val label: String, val icon: ImageVector)

private val bottomTabs = listOf(
    BottomTab(ROUTE_TODAY, "Hoy", Icons.Default.Today),
    BottomTab(ROUTE_SEARCH, "Buscar", Icons.Default.Search),
    BottomTab(ROUTE_HISTORY, "Historial", Icons.Default.History),
    BottomTab(ROUTE_ALLERGIES, "Alergias", Icons.Default.Warning),
)

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
            val startDestination = if (state == SessionState.LoggedIn) ROUTE_TODAY else ROUTE_LOGIN
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
    val bottomBarRoutes = bottomTabs.map { it.route }.toSet()
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
                    bottomTabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                        )
                    }
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
                    onLoggedIn = { navController.navigate(ROUTE_TODAY) { popUpTo(0) } },
                    onNavigateToRegister = { navController.navigate(ROUTE_REGISTER) },
                )
            }
            composable(ROUTE_REGISTER) {
                RegisterScreen(
                    onRegistered = { navController.navigate(ROUTE_TODAY) { popUpTo(0) } },
                    onNavigateToLogin = { navController.popBackStack() },
                )
            }
            composable(ROUTE_TODAY) {
                TodayScreen(
                    contentPadding = innerPadding,
                    onOpenReminders = { navController.navigate(ROUTE_REMINDERS) },
                    onSearch = {
                        navController.navigate(ROUTE_SEARCH) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
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
