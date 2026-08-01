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
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ViewConeDebugScreen(
    level: String? = null,
    viewModel: ViewConeDebugViewModel = koinViewModel(),
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(level) {
        viewModel.onEvent(ViewConeDebugViewModel.Event.Initialize(level))
    }

    ViewConeDebugContent(
        modifier = modifier,
        state = state,
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
        }
    )
}

@Composable
private fun ViewConeDebugContent(
    state: ViewConeDebugViewModel.State,
    modifier: Modifier = Modifier,
    onMoveNorth: () -> Unit = {},
    onMoveSouth: () -> Unit = {},
    onRotateEast: () -> Unit = {},
    onRotateWest: () -> Unit = {},
) {
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val screenWidth = this.maxWidth
        val screenHeight = this.maxHeight

        if (state.viewPort != null) {
            Box(
                Modifier.width(screenWidth)
                    .height(screenHeight)
                    .background(Color.Cyan)
            ) {
                val density = LocalDensity.current
                val containerWidthPx = with(density) { screenWidth.toPx() }.toInt()
                val containerHeightPx = with(density) { screenHeight.toPx() }.toInt()
                val scaleByWidth = containerWidthPx / state.viewPort.width
                val scaleByHeight = containerHeightPx / state.viewPort.height
                val scaleFactor = minOf(scaleByWidth, scaleByHeight).coerceAtLeast(1)
                Canvas(
                    modifier = Modifier
                ) {
                    drawImage(
                        image = state.viewPort,
                        dstSize = IntSize(
                            state.viewPort.width * scaleFactor,
                            state.viewPort.height * scaleFactor
                        ),
                        // nearest-neighbor keeps the retro pixels crisp
                        filterQuality = FilterQuality.None
                    )
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

            }
        }
    }
}
