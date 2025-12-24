package pl.pelotasplus.eyeofbeholder.features.pal_debug

import androidx.compose.foundation.Canvas
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
fun PalDebugScreen(
    viewModel: PalDebugViewModel = koinViewModel(),
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    PalDebugContent(
        modifier = modifier,
        state = state,
        onPalSelected = {
            viewModel.onEvent(PalDebugViewModel.Event.OnPalSelected(it))
        }
    )
}

@Composable
private fun PalDebugContent(
    state: PalDebugViewModel.State,
    modifier: Modifier = Modifier,
    onPalSelected: (String) -> Unit = {},
) {
    if (state.isLoading) {
        CircularProgressIndicator()
    } else {
        BoxWithConstraints(modifier = modifier) {
            val isLandscape = maxWidth > maxHeight

            val palList: @Composable (Modifier) -> Unit = { listModifier ->
                LazyColumn(listModifier) {
                    items(state.allPals) { palName ->
                        Button(
                            onClick = { onPalSelected(palName) }
                        ) {
                            Text(text = palName)
                        }
                    }
                }
            }

            val palCanvas: @Composable (Modifier) -> Unit = { canvasModifier ->
                if (state.loadedPal != null) {
                    BoxWithConstraints(modifier = canvasModifier) {
                        val canvasSize = if (isLandscape) maxHeight else maxWidth
                        Canvas(modifier = Modifier.size(canvasSize)) {
                            val cellSize = size.width / 16
                            state.loadedPal.colors.forEachIndexed { index, color ->
                                val x = (index % 16) * cellSize
                                val y = (index / 16) * cellSize
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

            if (isLandscape) {
                Row {
                    palList(Modifier.weight(1f).fillMaxHeight())
                    palCanvas(Modifier)
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
    PalDebugContent(
        state = PalDebugViewModel.State(
            isLoading = true
        )
    )
}
