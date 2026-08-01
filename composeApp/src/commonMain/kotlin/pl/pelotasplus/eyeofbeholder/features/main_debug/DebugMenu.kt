package pl.pelotasplus.eyeofbeholder.features.main_debug

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import pl.pelotasplus.eyeofbeholder.navigation.Route

private data class DebugDestination(val label: String, val route: Route)

private val debugDestinations = listOf(
    DebugDestination("Levels", Route.LevelsDebug),
    DebugDestination("CPS Debug", Route.CpsDebug),
    DebugDestination("DEC Debug", Route.DecDebug),
    DebugDestination("PAL Debug", Route.PalDebug),
    DebugDestination("INF Debug", Route.InfDebug),
    DebugDestination("MAZ Debug", Route.MazDebug),
    DebugDestination("VCN Debug", Route.VcnDebug),
)

/**
 * Always-on-top entry point to the resource debug screens. Lives in the top right
 * corner of the app so the game view can be the start destination.
 */
@Composable
fun DebugMenu(
    onDestinationClick: (Route) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier.padding(8.dp)) {
        Button(onClick = { expanded = true }) {
            // plain ASCII: the web build's default font has no glyph for "▾"
            Text("Debug")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            debugDestinations.forEach { destination ->
                DropdownMenuItem(
                    text = { Text(destination.label) },
                    onClick = {
                        expanded = false
                        onDestinationClick(destination.route)
                    },
                )
            }
        }
    }
}
