package pl.pelotasplus.eyeofbeholder.features.cps_debug

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel

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
        BoxWithConstraints(modifier = modifier) {
            val isLandscape = maxWidth > maxHeight

            val palList: @Composable (Modifier) -> Unit = { listModifier ->
                LazyColumn(listModifier) {
                    items(state.cpsNames) { palName ->
                        Button(
                            onClick = { onCpsSelected(palName) }
                        ) {
                            Text(text = palName)
                        }
                    }
                }
            }

            val palCanvas: @Composable (Modifier) -> Unit = { canvasModifier ->
                if (state.loadedCps != null && state.loadedPalette != null) {
                    BoxWithConstraints(modifier = canvasModifier) {
                        val canvasSize = if (isLandscape) maxHeight else maxWidth
                        Canvas(modifier = Modifier.size(canvasSize)) {
                            val cellSize = maxWidth / 320
                            state.loadedCps.pixels.forEachIndexed { index, colorIndex ->
                                val x = (index % 320) * cellSize.toPx()
                                val y = (index / 320) * cellSize.toPx()
                                val color = state.loadedPalette.colors[colorIndex.toInt()]
                                drawRect(
                                    color = Color(
                                        color.red.toInt(),
                                        color.green.toInt(),
                                        color.blue.toInt()
                                    ),
                                    topLeft = Offset(x, y),
                                    size = Size(cellSize.toPx(), cellSize.toPx())
                                )
                            }
                        }
                    }
                }
            }

            if (isLandscape) {
                Row {
                    palList(Modifier.weight(0.3f).fillMaxHeight())
                    palCanvas(Modifier.weight(0.7f).fillMaxWidth().background(Color.Red))
                }
            } else {
                Column {
                    palList(Modifier.weight(1f).fillMaxWidth())
                    palCanvas(Modifier)
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
