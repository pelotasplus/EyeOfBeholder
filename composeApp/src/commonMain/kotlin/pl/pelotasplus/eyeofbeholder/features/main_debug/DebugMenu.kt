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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
 * Always-on-top entry point to the resource debug screens. On the game screen
 * the CAMP button opens it and no anchor is drawn.
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
    showAnchor: Boolean = true,
) {
    Box(modifier = modifier.fillMaxSize()) {
        if (showAnchor && !expanded) {
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

/**
 * The panel itself. Hosted by [DebugMenu] on the debug screens, and directly by
 * the game screen, which opens it from CAMP — an overlay declared as a sibling
 * of the NavHost is either drawn under the screen or misses its input,
 * depending on the order, so it has to live inside the screen's composition.
 */
@Composable
fun DebugMenuPanel(
    onDestinationClick: (Route) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
