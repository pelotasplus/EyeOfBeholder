package pl.pelotasplus.eyeofbeholder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import pl.pelotasplus.eyeofbeholder.features.cps_debug.CpsDebugScreen
import pl.pelotasplus.eyeofbeholder.features.dec_debug.DecDebugScreen
import pl.pelotasplus.eyeofbeholder.features.inf_debug.InfDebugScreen
import pl.pelotasplus.eyeofbeholder.features.levels_debug.LevelsDebugScreen
import pl.pelotasplus.eyeofbeholder.features.main_debug.DebugMenu
import pl.pelotasplus.eyeofbeholder.features.maz_debug.MazDebugScreen
import pl.pelotasplus.eyeofbeholder.features.pal_debug.PalDebugScreen
import pl.pelotasplus.eyeofbeholder.features.vcn_debug.VcnDebugScreen
import pl.pelotasplus.eyeofbeholder.features.view_cone_debug.ViewConeDebugScreen
import pl.pelotasplus.eyeofbeholder.navigation.Route

@Composable
fun App() {
    MaterialTheme {
        val navController = rememberNavController()
        var debugMenuExpanded by remember { mutableStateOf(false) }
        Box(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.primaryContainer)
                .safeContentPadding()
                .fillMaxSize(),
        ) {
            NavHost(
                navController = navController,
                startDestination = Route.ViewConeDebug(),
            ) {
                composable<Route.ViewConeDebug> { entry ->
                    ViewConeDebugScreen(
                        level = entry.toRoute<Route.ViewConeDebug>().level,
                        onDebugDestinationClick = { route ->
                            navController.navigate(route) {
                                popUpTo<Route.ViewConeDebug>()
                                launchSingleTop = true
                            }
                        },
                    )
                }
                composable<Route.LevelsDebug> {
                    LevelsDebugScreen(
                        onLevelSelected = { level ->
                            navController.navigate(Route.ViewConeDebug(level)) {
                                popUpTo<Route.ViewConeDebug> { inclusive = true }
                            }
                        }
                    )
                }
                composable<Route.CpsDebug> {
                    CpsDebugScreen()
                }
                composable<Route.DecDebug> {
                    DecDebugScreen()
                }
                composable<Route.PalDebug> {
                    PalDebugScreen()
                }
                composable<Route.InfDebug> {
                    InfDebugScreen()
                }
                composable<Route.MazDebug> {
                    MazDebugScreen()
                }
                composable<Route.VcnDebug> {
                    VcnDebugScreen()
                }
            }

            DebugMenu(
                expanded = debugMenuExpanded,
                onExpandedChange = { debugMenuExpanded = it },
                onDestinationClick = { route ->
                    navController.navigate(route) {
                        popUpTo<Route.ViewConeDebug>()
                        launchSingleTop = true
                    }
                },
            )

        }
    }
}
