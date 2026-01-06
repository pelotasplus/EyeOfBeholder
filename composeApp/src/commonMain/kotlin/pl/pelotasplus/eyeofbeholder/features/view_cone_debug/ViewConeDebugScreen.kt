package pl.pelotasplus.eyeofbeholder.features.view_cone_debug

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ViewConeDebugScreen(
    viewModel: ViewConeDebugViewModel = koinViewModel(),
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ViewConeDebugContent(
        modifier = modifier,
        state = state,
        onVmpSelected = {
            viewModel.onEvent(ViewConeDebugViewModel.Event.OnVmpSelected(it))
        },
        onMoveNorth = {
            viewModel.onEvent(ViewConeDebugViewModel.Event.PlayerMoveNorth)
        },
        onMoveSouth = {
            viewModel.onEvent(ViewConeDebugViewModel.Event.PlayerMoveSouth)
        },
        onRotateEast = {
            viewModel.onEvent(ViewConeDebugViewModel.Event.RotateEast)
        },
        onRotateWest = {
            viewModel.onEvent(ViewConeDebugViewModel.Event.RotateWest)
        }
    )
}

@Composable
private fun ViewConeDebugContent(
    state: ViewConeDebugViewModel.State,
    modifier: Modifier = Modifier,
    onVmpSelected: (String) -> Unit = {},
    onMoveNorth: () -> Unit = {},
    onMoveSouth: () -> Unit = {},
    onRotateEast: () -> Unit = {},
    onRotateWest: () -> Unit = {},
) {
    if (state.isLoading) {
        CircularProgressIndicator()
    } else {
        Column(modifier = modifier) {
            Column(Modifier.weight(0.5f)) {
                state.allVmps.forEach { vmpName ->
                    Button(
                        onClick = { onVmpSelected(vmpName) }
                    ) {
                        Text(text = vmpName)
                    }
                }
            }

            // Player position and direction controls
            Column(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Player position and direction (read-only)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Player: (${state.playerX}, ${state.playerY})")
                    Text("Direction: ${state.direction.name}")
                }

                // Navigation buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(onClick = onRotateWest) {
                        Text("W")
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Button(onClick = onMoveNorth) {
                            Text("N")
                        }
                        Button(onClick = onMoveSouth) {
                            Text("S")
                        }
                    }
                    Button(onClick = onRotateEast) {
                        Text("E")
                    }
                }
            }

            if (state.selectedTiles != null) {
                BoxWithConstraints(modifier = Modifier.weight(0.5f).fillMaxWidth().background(Color.Cyan)) {
                    val density = LocalDensity.current
                    val containerWidthInPixels = with(density) { maxWidth.toPx() }.toInt()
                    val scaleFactor = (containerWidthInPixels / 176f).toInt().coerceAtLeast(1)
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Cyan)
                    ) {
                        scale(scaleFactor.toFloat(), pivot = Offset.Zero) {
                            state.selectedTiles.getRows().forEachIndexed { y, row ->
                                row.forEachIndexed { x, rgb ->
                                    drawRect(
                                        color = Color(rgb.red, rgb.green, rgb.blue),
                                        topLeft = Offset(x.toFloat(), y.toFloat()),
                                        size = Size(1f, 1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
