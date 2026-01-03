package pl.pelotasplus.eyeofbeholder.features.view_cone_debug

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
        }
    )
}

@Composable
private fun ViewConeDebugContent(
    state: ViewConeDebugViewModel.State,
    modifier: Modifier = Modifier,
    onVmpSelected: (String) -> Unit = {},
) {
    if (state.isLoading) {
        CircularProgressIndicator()
    } else {
        Column(modifier = modifier) {
            Column {
                state.allVmps.forEach { vmpName ->
                    Button(
                        onClick = { onVmpSelected(vmpName) }
                    ) {
                        Text(text = vmpName)
                    }
                }
            }

            if (state.selectedTiles != null) {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth().background(Color.Cyan)) {
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
