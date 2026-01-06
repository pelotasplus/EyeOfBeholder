package pl.pelotasplus.eyeofbeholder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import org.jetbrains.compose.ui.tooling.preview.Preview
import pl.pelotasplus.eyeofbeholder.features.cps_debug.CpsDebugScreen
import pl.pelotasplus.eyeofbeholder.features.inf_debug.InfDebugScreen
import pl.pelotasplus.eyeofbeholder.features.main_debug.MainDebugScreen
import pl.pelotasplus.eyeofbeholder.features.maz_debug.MazDebugScreen
import pl.pelotasplus.eyeofbeholder.features.pal_debug.PalDebugScreen
import pl.pelotasplus.eyeofbeholder.features.vcn_debug.VcnDebugScreen
import pl.pelotasplus.eyeofbeholder.features.view_cone_debug.ViewConeDebugScreen
import pl.pelotasplus.eyeofbeholder.navigation.Route

@Composable
@Preview
fun App() {
    MaterialTheme {
        val navController = rememberNavController()
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.primaryContainer)
                .safeContentPadding()
                .fillMaxSize(),
        ) {
            NavHost(
                navController = navController,
                startDestination = Route.DebugGraph,
            ) {
                navigation<Route.DebugGraph>(
                    startDestination = Route.MainDebug,
                ) {
                    composable<Route.MainDebug> {
                        MainDebugScreen(
                            onCpsDebugClick = {
                                navController.navigate(Route.CpsDebug)
                            },
                            onPalDebugClick = {
                                navController.navigate(Route.PalDebug)
                            },
                            onInfDebugClick = {
                                navController.navigate(Route.InfDebug)
                            },
                            onMazDebugClick = {
                                navController.navigate(Route.MazDebug)
                            },
                            onVcnDebugClick = {
                                navController.navigate(Route.VcnDebug)
                            },
                            onViewConeDebugClick = {
                                navController.navigate(Route.ViewConeDebug)
                            }
                        )
                    }
                    composable<Route.CpsDebug> {
                        CpsDebugScreen()
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
                    composable<Route.ViewConeDebug> {
                        ViewConeDebugScreen()
                    }
                }
            }
        }
    }
}
