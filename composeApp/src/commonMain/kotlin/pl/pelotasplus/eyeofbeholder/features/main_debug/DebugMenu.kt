package pl.pelotasplus.eyeofbeholder.features.main_debug

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import pl.pelotasplus.eyeofbeholder.data.model.Debugging
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
 * Always-on-top entry point to the resource debug screens.
 *
 * Deliberately not a DropdownMenu: in the Wasm build a Popup's items never
 * received clicks, so the panel is drawn inline in the same composition.
 */
@Composable
fun DebugMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onDestinationClick: (Route) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        if (!expanded) {
            Button(
                onClick = { onExpandedChange(true) },
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            ) {
                Text("Debug")
            }
        }

        if (expanded) {
            DebugMenuPanel(
                onDestinationClick = onDestinationClick,
                onDismiss = { onExpandedChange(false) },
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
    }
}

@Composable
private fun DebugMenuPanel(
    onDestinationClick: (Route) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    debugging: Debugging = koinInject(),
) {
    val wallsArePassable by debugging.wallsArePassable.collectAsState()
    val monstersMayWalk by debugging.monstersMayWalk.collectAsState()
    val showingMap by debugging.showingMap.collectAsState()

    Column(
        modifier = modifier
            .padding(8.dp)
            .width(180.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text("Close")
        }

        Button(
            onClick = { debugging.passWalls(!wallsArePassable) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (wallsArePassable) "Walls: pass" else "Walls: solid")
        }

        Button(
            onClick = { debugging.letMonstersWalk(!monstersMayWalk) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (monstersMayWalk) "Monsters: hunt" else "Monsters: rooted")
        }

        Button(
            onClick = { debugging.showMap(!showingMap) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (showingMap) "Map: on" else "Map: off")
        }

        debugDestinations.forEach { destination ->
            Button(
                onClick = {
                    onDismiss()
                    onDestinationClick(destination.route)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(destination.label)
            }
        }
    }
}
