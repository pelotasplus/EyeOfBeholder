package pl.pelotasplus.eyeofbeholder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import org.koin.compose.koinInject
import pl.pelotasplus.eyeofbeholder.data.model.Debugging
import pl.pelotasplus.eyeofbeholder.features.cps_debug.CpsDebugScreen
import pl.pelotasplus.eyeofbeholder.features.levels_debug.LevelsDebugScreen
import pl.pelotasplus.eyeofbeholder.features.main_debug.DebugMenu
import pl.pelotasplus.eyeofbeholder.features.view_cone_debug.ViewConeDebugScreen
import pl.pelotasplus.eyeofbeholder.navigation.Route

@Composable
fun App() = CompositionLocalProvider(LocalPlayFieldFocus provides remember { PlayFieldFocus() }) {
    MaterialTheme {
        val navController = rememberNavController()
        // Held outside the composition because the view reads it as well, to
        // decide whether to write where the party are standing over its corner.
        val debugging: Debugging = koinInject()
        val debugMenuExpanded by debugging.menuIsOpen.collectAsState()
        val playFieldFocus = LocalPlayFieldFocus.current

        // the menu's buttons take the focus and closing them does not give it
        // back, so the screen underneath would stay deaf to the keyboard
        LaunchedEffect(debugMenuExpanded) {
            if (!debugMenuExpanded) playFieldFocus.takeBack()
        }

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
                    val route = entry.toRoute<Route.ViewConeDebug>()
                    ViewConeDebugScreen(
                        level = route.level,
                        startX = route.startX,
                        startY = route.startY,
                        startDirection = route.startFacing,
                    )
                }
                composable<Route.LevelsDebug> {
                    LevelsDebugScreen(
                        onLevelSelected = { level, entryPoint ->
                            navController.navigate(Route.ViewConeDebug(level, entryPoint)) {
                                popUpTo<Route.ViewConeDebug> { inclusive = true }
                            }
                        }
                    )
                }
                composable<Route.CpsDebug> {
                    CpsDebugScreen()
                }
            }

            DebugMenu(
                expanded = debugMenuExpanded,
                onExpandedChange = { debugging.openMenu(it) },
                onDestinationClick = { route ->
                    // The dungeon is not navigated to but returned to. It is
                    // still on the stack and still running underneath, and
                    // arriving at a fresh one would load the level again and
                    // put the party back at its entrance.
                    if (route is Route.ViewConeDebug) {
                        navController.popBackStack<Route.ViewConeDebug>(inclusive = false)
                    } else {
                        navController.navigate(route) {
                            popUpTo<Route.ViewConeDebug>()
                            launchSingleTop = true
                        }
                    }
                },
            )

        }
    }
}
