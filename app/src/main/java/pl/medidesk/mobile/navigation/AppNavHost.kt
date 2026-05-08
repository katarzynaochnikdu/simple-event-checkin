package pl.medidesk.mobile.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.Modifier
import pl.medidesk.mobile.feature.auth.presentation.screen.LoginScreen
import pl.medidesk.mobile.feature.dashboard.presentation.screen.DashboardScreen
import pl.medidesk.mobile.feature.dashboard.presentation.screen.StatsScreen
import pl.medidesk.mobile.feature.events.presentation.screen.EventsScreen
import pl.medidesk.mobile.feature.participants.presentation.screen.ParticipantDetailsScreen
import pl.medidesk.mobile.feature.participants.presentation.screen.ParticipantsScreen
import pl.medidesk.mobile.feature.scanner.presentation.screen.ScannerScreen

@Composable
fun AppNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.Login.createRoute("ORGANIZER"), // Bypass role selection
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
                        popUpTo(0) { inclusive = true } // Clear backstack after login
                    }
                }
            )
        }

        composable(Screen.Events.route) {
            EventsScreen(
                onEventSelected = { eventId ->
                    navController.navigate(Screen.Main.createRoute(eventId))
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

    Scaffold(
        bottomBar = {
            NavigationBar {
                val isDashboard = currentDestination?.route?.startsWith("dashboard") == true
                NavigationBarItem(
                    icon = {
                        if (isDashboard) Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Lista")
                        else Icon(Icons.Default.Home, contentDescription = "Wydarzenie")
                    },
                    label = { Text(if (isDashboard) "Lista" else "Wydarzenie") },
                    selected = false,
                    onClick = {
                        if (isDashboard) {
                            onBackToEvents()
                        } else {
                            innerNav.navigate(Screen.Dashboard.createRoute(eventId)) {
                                popUpTo(innerNav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
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
                        onClick = {
                            innerNav.navigate(route) {
                                popUpTo(innerNav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
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
                    onBackToEvents = onBackToEvents
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
                    onBackClick = { innerNav.popBackStack() },
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
                    onBackClick = { innerNav.popBackStack() }
                )
            }
            
            composable(
                route = Screen.ParticipantDetails.route,
                arguments = Screen.ParticipantDetails.arguments
            ) { backStackEntry ->
                val participantId = backStackEntry.arguments?.getLong("participantId") ?: return@composable
                ParticipantDetailsScreen(
                    participantId = participantId,
                    onBackClick = { innerNav.popBackStack() }
                )
            }
        }
    }
}
