package pl.pelotasplus.eyeofbeholder.features.vmp_debug

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun VmpDebugScreen(
    viewModel: VmpDebugViewModel = koinViewModel(),
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    VmpDebugContent(
        modifier = modifier,
        state = state,
        onVmpSelected = {
            viewModel.onEvent(VmpDebugViewModel.Event.OnVmpSelected(it))
        }
    )
}

@Composable
private fun VmpDebugContent(
    state: VmpDebugViewModel.State,
    modifier: Modifier = Modifier,
    onVmpSelected: (String) -> Unit = {},
) {
    if (state.isLoading) {
        CircularProgressIndicator()
    } else {
        Column(modifier = modifier) {
            Row {
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

            if (state.selectedVmp != null) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(8.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "File: ${state.selectedVmp.name}",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Text(
                        text = "Tiles count: ${state.selectedVmp.tileIndexes.size}",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Text(
                        text = "Wall types count: ${state.selectedVmp.wallTypesCount}",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}


@Preview
@Composable
private fun PreviewVmpDebugContent() {
    VmpDebugContent(
        state = VmpDebugViewModel.State()
    )
}
