package pl.pelotasplus.eyeofbeholder.features.vmp_debug

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
                val tilesPerRow = 22
                val tileSize = 8

                Canvas(modifier = Modifier.size(176.dp, 120.dp).background(Color.Cyan)) {
                    val scaleFactor = 5f
                    val cellSize = 1f

                    scale(scaleFactor, pivot = Offset.Zero) {
                        state.selectedTiles.forEachIndexed { tileIndex, pixels ->
                            val tileCol = tileIndex % tilesPerRow
                            val tileRow = tileIndex / tilesPerRow

                            pixels.forEachIndexed { pixelIndex, color ->
                                val pixelCol = pixelIndex % tileSize
                                val pixelRow = pixelIndex / tileSize

                                val x = (tileCol * tileSize + pixelCol) * cellSize
                                val y = (tileRow * tileSize + pixelRow) * cellSize

                                drawRect(
                                    color = Color(
                                        color.red.toInt(),
                                        color.green.toInt(),
                                        color.blue.toInt()
                                    ),
                                    topLeft = Offset(x, y),
                                    size = Size(cellSize, cellSize)
                                )
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
