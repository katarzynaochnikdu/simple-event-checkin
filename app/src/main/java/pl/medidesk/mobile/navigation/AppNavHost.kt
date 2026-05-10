package pl.medidesk.mobile.navigation

import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.Modifier
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import pl.medidesk.mobile.core.datastore.AuthDataStore
import pl.medidesk.mobile.core.network.MobileApiService
import pl.medidesk.mobile.core.network.SessionManager
import pl.medidesk.mobile.feature.auth.presentation.screen.LoginScreen
import pl.medidesk.mobile.feature.auth.presentation.screen.ResetPasswordScreen
import pl.medidesk.mobile.feature.dashboard.presentation.screen.DashboardScreen
import pl.medidesk.mobile.feature.dashboard.presentation.screen.MyMenteesScreen
import pl.medidesk.mobile.feature.dashboard.presentation.screen.StatsScreen
import pl.medidesk.mobile.feature.events.presentation.screen.EventsScreen
import pl.medidesk.mobile.feature.more.presentation.screen.SettingsScreen
import pl.medidesk.mobile.feature.participants.presentation.screen.ParticipantDetailsScreen
import pl.medidesk.mobile.feature.participants.presentation.screen.ParticipantsScreen
import pl.medidesk.mobile.feature.scanner.presentation.screen.ScannerScreen
import javax.inject.Inject

enum class AuthCheck { CHECKING, LOGGED_IN, NOT_LOGGED_IN }

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authDataStore: AuthDataStore,
    private val apiService: MobileApiService,
    val sessionManager: SessionManager
) : ViewModel() {
    private val _authState = MutableStateFlow(AuthCheck.CHECKING)
    val authState: StateFlow<AuthCheck> = _authState.asStateFlow()

    init {
        viewModelScope.launch {
            val token = authDataStore.tokenFlow.firstOrNull()
            if (token.isNullOrBlank()) {
                _authState.value = AuthCheck.NOT_LOGGED_IN
                return@launch
            }
            try {
                val resp = apiService.me()
                if (resp.isSuccessful && resp.body() != null) {
                    _authState.value = AuthCheck.LOGGED_IN
                } else {
                    authDataStore.clearAll()
                    _authState.value = AuthCheck.NOT_LOGGED_IN
                }
            } catch (_: Exception) {
                _authState.value = AuthCheck.LOGGED_IN
            }
        }
    }
}

@Composable
fun AppNavHost(
    pendingDeepLink: Uri? = null,
    onDeepLinkConsumed: () -> Unit = {},
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    val authState by authViewModel.authState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        authViewModel.sessionManager.sessionExpired.collect {
            navController.navigate(Screen.Login.createRoute("ORGANIZER")) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    // Deep link: medidesk://reset-password?token=...
    LaunchedEffect(pendingDeepLink, authState) {
        // Czekamy aż auth check się skończy (żeby NavHost był skonfigurowany)
        if (authState == AuthCheck.CHECKING) return@LaunchedEffect
        val link = pendingDeepLink ?: return@LaunchedEffect
        if (link.scheme == "medidesk" && link.host == "reset-password") {
            val token = link.getQueryParameter("token").orEmpty()
            if (token.isNotBlank()) {
                navController.navigate(Screen.ResetPassword.createRoute(token)) {
                    // Reset zawsze "świeży" — czyścimy stack
                    popUpTo(0) { inclusive = true }
                }
            }
            onDeepLinkConsumed()
        }
    }

    val startDest = when (authState) {
        AuthCheck.CHECKING -> return
        AuthCheck.LOGGED_IN -> Screen.Events.route
        AuthCheck.NOT_LOGGED_IN -> Screen.Login.createRoute("ORGANIZER")
    }

    NavHost(
        navController = navController,
        startDestination = startDest,
        modifier = Modifier.fillMaxSize()
    ) {
        composable(
            route = Screen.Login.route,
            arguments = Screen.Login.arguments
        ) { backStackEntry ->
            val role = backStackEntry.arguments?.getString("role") ?: "ORGANIZER"
            LoginScreen(
                role = role,
                onLoginSuccess = {
                    navController.navigate(Screen.Events.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onMustChangePassword = {
                    navController.navigate(Screen.Events.route) {
                        popUpTo(0) { inclusive = true }
                    }
                    navController.navigate(Screen.Settings.route)
                }
            )
        }

        composable(
            route = Screen.ResetPassword.route,
            arguments = Screen.ResetPassword.arguments
        ) { backStackEntry ->
            val token = backStackEntry.arguments?.getString("token").orEmpty()
            ResetPasswordScreen(
                token = token,
                onSuccess = {
                    navController.navigate(Screen.Login.createRoute("ORGANIZER")) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onCancel = {
                    navController.navigate(Screen.Login.createRoute("ORGANIZER")) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Events.route) {
            EventsScreen(
                onEventSelected = { eventId ->
                    navController.navigate(Screen.Main.createRoute(eventId))
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onLogout = {
                    navController.navigate(Screen.Login.createRoute("ORGANIZER")) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
        
        composable(Screen.GlobalScanner.route) {
            ScannerScreen(eventId = "")
        }

        composable(
            route = Screen.Main.route,
            arguments = Screen.Main.arguments
        ) { backStackEntry ->
            val eventId = backStackEntry.arguments?.getString("eventId") ?: return@composable
            MainScreen(
                eventId = eventId,
                onLogout = {
                    navController.navigate(Screen.Login.createRoute("ORGANIZER")) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onBackToEvents = { navController.popBackStack() }
            )
        }
    }
}

@Composable
private fun MainScreen(eventId: String, onLogout: () -> Unit, onBackToEvents: () -> Unit) {
    val innerNav = rememberNavController()
    val navBackStackEntry by innerNav.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    var eventAccentColor by remember { mutableStateOf<androidx.compose.ui.graphics.Color?>(null) }
    val isDark = isSystemInDarkTheme()

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = if (isDark) androidx.compose.ui.graphics.Color(0xFF1C1C1E)
                    else MaterialTheme.colorScheme.surface
            ) {
                val isDashboard = currentDestination?.route?.startsWith("dashboard") == true
                val accent = if (isDark) androidx.compose.ui.graphics.Color.White
                    else eventAccentColor ?: MaterialTheme.colorScheme.secondary
                val navColors = NavigationBarItemDefaults.colors(
                    selectedIconColor = accent,
                    selectedTextColor = accent,
                    unselectedIconColor = if (isDark) androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f) else accent,
                    unselectedTextColor = if (isDark) androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f) else accent,
                    indicatorColor = if (isDark) androidx.compose.ui.graphics.Color.White.copy(alpha = 0.15f) else accent.copy(alpha = 0.12f)
                )
                NavigationBarItem(
                    icon = {
                        if (isDashboard) Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Lista wydarzeń")
                        else Icon(Icons.Default.Home, contentDescription = "Wydarzenie")
                    },
                    label = { Text(if (isDashboard) "Lista wydarzeń" else "Wydarzenie") },
                    selected = false,
                    colors = navColors,
                    onClick = {
                        if (isDashboard) {
                            onBackToEvents()
                        } else {
                            innerNav.navigate(Screen.Dashboard.createRoute(eventId)) {
                                popUpTo(innerNav.graph.findStartDestination().id)
                                launchSingleTop = true
                            }
                        }
                    }
                )

                // Pozostałe taby — wewnętrzne nawigacja w eventu
                val items = listOf(
                    Triple(Screen.Participants.createRoute(eventId), "Uczestnicy", Icons.Default.Group),
                    Triple(Screen.Scanner.createRoute(eventId), "Skaner", Icons.Default.QrCodeScanner),
                )

                items.forEach { (route, label, icon) ->
                    val baseRoute = route.substringBefore("/")
                    val isSelected = currentDestination?.hierarchy?.any { it.route?.startsWith(baseRoute) == true } == true
                    NavigationBarItem(
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label) },
                        selected = isSelected,
                        colors = navColors,
                        onClick = {
                            innerNav.navigate(route) {
                                popUpTo(innerNav.graph.findStartDestination().id)
                                launchSingleTop = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        // Powrót do Dashboardu z dowolnego ekranu sub-grafu eventu.
        // popBackStack nie zawsze działa — bottom-nav używa popUpTo+saveState, więc
        // bezpośrednie wejście na Participants/Scanner zostawia Dashboard "out of stack"
        // (poprawnie, bo restoreState=true je odtwarza). Strzałka back arrow ma działać
        // jednoznacznie: nawiguj do Dashboardu z tym samym wzorcem co tab "Wydarzenie".
        val goToDashboard: () -> Unit = {
            innerNav.navigate(Screen.Dashboard.createRoute(eventId)) {
                popUpTo(innerNav.graph.findStartDestination().id)
                launchSingleTop = true
            }
        }
        // Stats i MyMentees są zawsze pushowane bezpośrednio z Dashboardu (nie przez bottom-nav),
        // więc popBackStack() jest poprawne — Dashboard jest zawsze w stosie nad nimi.
        val goBack: () -> Unit = { innerNav.popBackStack() }

        NavHost(
            navController = innerNav,
            startDestination = Screen.Dashboard.createRoute(eventId),
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(
                route = Screen.Dashboard.route,
                arguments = Screen.Dashboard.arguments
            ) {
                DashboardScreen(
                    eventId = eventId,
                    onNavigateToScanner = { innerNav.navigate(Screen.Scanner.createRoute(eventId)) },
                    onNavigateToParticipants = { filterType ->
                        innerNav.navigate(Screen.Participants.createRoute(eventId, filterType))
                    },
                    onNavigateToInHub = { /* Disabled */ },
                    onNavigateToStats = { innerNav.navigate(Screen.Stats.createRoute(eventId)) },
                    onNavigateToMyMentees = { innerNav.navigate(Screen.MyMentees.createRoute(eventId)) },
                    onBackToEvents = onBackToEvents,
                    onEventColorLoaded = { color -> eventAccentColor = color }
                )
            }

            composable(
                route = Screen.MyMentees.route,
                arguments = Screen.MyMentees.arguments
            ) {
                MyMenteesScreen(
                    eventId = eventId,
                    onBackClick = goBack,
                    onParticipantClick = { participantId ->
                        innerNav.navigate(Screen.ParticipantDetails.createRoute(participantId))
                    }
                )
            }

            composable(
                route = Screen.Scanner.route,
                arguments = Screen.Scanner.arguments
            ) {
                ScannerScreen(eventId = eventId)
            }

            composable(
                route = Screen.Participants.route,
                arguments = Screen.Participants.arguments
            ) { backStackEntry ->
                val filterType = backStackEntry.arguments?.getString("filterType")
                val ticketClassId = backStackEntry.arguments?.getString("ticketClassId")
                ParticipantsScreen(
                    eventId = eventId,
                    filterType = filterType,
                    ticketClassId = ticketClassId,
                    onBackClick = goToDashboard,
                    onParticipantClick = { participantId ->
                        innerNav.navigate(Screen.ParticipantDetails.createRoute(participantId))
                    }
                )
            }

            composable(
                route = Screen.Stats.route,
                arguments = Screen.Stats.arguments
            ) {
                StatsScreen(
                    eventId = eventId,
                    onBackClick = goBack
                )
            }

            composable(
                route = Screen.ParticipantDetails.route,
                arguments = Screen.ParticipantDetails.arguments
            ) { backStackEntry ->
                val participantId = backStackEntry.arguments?.getLong("participantId") ?: return@composable
                // ParticipantDetails to "drill-down" — popBackStack OK (zawsze ma poprzednika).
                ParticipantDetailsScreen(
                    participantId = participantId,
                    onBackClick = { innerNav.popBackStack() }
                )
            }
        }
    }
}
