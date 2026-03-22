package pl.pelotasplus.eyeofbeholder.features.view_cone_debug

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
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
        onLevelSelected = {
            viewModel.onEvent(ViewConeDebugViewModel.Event.OnLevelSelected(it))
        },
        onMoveNorth = {
            viewModel.onEvent(ViewConeDebugViewModel.Event.MoveForward)
        },
        onMoveSouth = {
            viewModel.onEvent(ViewConeDebugViewModel.Event.MoveBackwards)
        },
        onRotateEast = {
            viewModel.onEvent(ViewConeDebugViewModel.Event.RotateRight)
        },
        onRotateWest = {
            viewModel.onEvent(ViewConeDebugViewModel.Event.RotateLeft)
        },
        onGoBack = {
            viewModel.onEvent(ViewConeDebugViewModel.Event.GoBack)
        }
    )
}

@Composable
private fun ViewConeDebugContent(
    state: ViewConeDebugViewModel.State,
    modifier: Modifier = Modifier,
    onLevelSelected: (String) -> Unit = {},
    onMoveNorth: () -> Unit = {},
    onMoveSouth: () -> Unit = {},
    onRotateEast: () -> Unit = {},
    onGoBack: () -> Unit = {},
    onRotateWest: () -> Unit = {},
) {
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val screenWidth = this.maxWidth
        val screenHeight = this.maxHeight

        if (state.viewPort == null) {
            LazyColumn {
                items(state.levels) { vmpName ->
                    Button(
                        onClick = { onLevelSelected(vmpName) }
                    ) {
                        Text(text = vmpName)
                    }
                }
            }
        }

        if (state.viewPort != null) {
            Box(
                Modifier.width(screenWidth)
                    .height(screenHeight)
                    .background(Color.Cyan)
            ) {
                val density = LocalDensity.current
                val containerWidthPx = with(density) { screenWidth.toPx() }.toInt()
                val containerHeightPx = with(density) { screenHeight.toPx() }.toInt()
                val scaleByWidth = containerWidthPx / 176
                val scaleByHeight = containerHeightPx / 120
                val scaleFactor = minOf(scaleByWidth, scaleByHeight).coerceAtLeast(1)
                Canvas(
                    modifier = Modifier
                ) {
                    scale(scaleFactor.toFloat(), pivot = Offset.Zero) {
                        state.viewPort.getRows().forEachIndexed { y, row ->
                            row.forEachIndexed { x, rgb ->
                                if (rgb.transparent) return@forEachIndexed
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

        // Player position and direction controls
        if (state.viewPort != null) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(8.dp).align(Alignment.BottomCenter),
            ) {
                // Player position and direction (read-only)
                Row(Modifier.background(Color.White).align(Alignment.BottomStart)) {
                    Text("${state.playerX}x${state.playerY} ${state.direction.name}")
                }

                // Navigation buttons
                Row(
                    modifier = Modifier.fillMaxWidth().align(Alignment.Center),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.Bottom
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

                Button(onClick = onGoBack, modifier = Modifier.align(Alignment.BottomEnd)) {
                    Text("<")
                }
            }
        }
    }
}
