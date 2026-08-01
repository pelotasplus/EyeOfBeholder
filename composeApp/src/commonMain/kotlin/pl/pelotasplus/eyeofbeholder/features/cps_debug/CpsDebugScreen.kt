package pl.pelotasplus.eyeofbeholder.features.cps_debug

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

private val FILE_LIST_WIDTH = 160.dp

@Composable
fun CpsDebugScreen(
    viewModel: CpsDebugViewModel = koinViewModel(),
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    CpsDebugContent(
        modifier = modifier,
        state = state,
        onCpsSelected = {
            viewModel.onEvent(CpsDebugViewModel.Event.OnCpsSelected(it))
        }
    )
}

@Composable
private fun CpsDebugContent(
    state: CpsDebugViewModel.State,
    modifier: Modifier = Modifier,
    onCpsSelected: (String) -> Unit = {},
) {
    if (state.isLoading) {
        CircularProgressIndicator()
    } else {
        Row(modifier = modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .width(FILE_LIST_WIDTH)
                    .fillMaxHeight()
                    .padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(state.cpsNames) { palName ->
                    Button(
                        onClick = { onCpsSelected(palName) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = palName)
                    }
                }
            }

            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(Color.Cyan),
                contentAlignment = Alignment.Center,
            ) {
                if (state.error != null) {
                    Text(
                        text = state.error,
                        color = Color.Red,
                        modifier = Modifier.padding(16.dp),
                    )
                }

                if (state.loadedCps != null && state.loadedPalette != null) {
                    val cps = state.loadedCps
                    val density = LocalDensity.current
                    val availableWidth = with(density) { maxWidth.toPx() }
                    val availableHeight = with(density) { maxHeight.toPx() }
                    // integer scale only, so the pixels stay square
                    val scaleFactor = minOf(
                        availableWidth / cps.width,
                        availableHeight / cps.height
                    ).toInt().coerceAtLeast(1)
                    val cellSize = 1f
                    Canvas(
                        modifier = Modifier.size(
                            width = with(density) { (cps.width * scaleFactor).toDp() },
                            height = with(density) { (cps.height * scaleFactor).toDp() },
                        )
                    ) {
                        scale(scaleFactor.toFloat(), pivot = Offset.Zero) {
                            cps.pixels.forEachIndexed { index, colorIndex ->
                                val x = (index % cps.width) * cellSize
                                val y = (index / cps.width) * cellSize
                                val color = state.loadedPalette.colors[colorIndex.value]
                                if (color.transparent) return@forEachIndexed
                                drawRect(
                                    color = Color(
                                        color.red,
                                        color.green,
                                        color.blue
                                    ),
                                    topLeft = Offset(x, y),
                                    size = Size(cellSize, cellSize)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun PreviewCpsDebugContent() {
    CpsDebugContent(
        state = CpsDebugViewModel.State(
            isLoading = true
        )
    )
}
