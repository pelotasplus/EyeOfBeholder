package pl.pelotasplus.eyeofbeholder.features.pal_debug

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
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
    Column(modifier = modifier) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(state.allPals) { palName ->
                Button(
                    onClick = { onPalSelected(palName) }
                ) {
                    Text(text = palName)
                }
            }
        }

        if (state.loadedPalette != null) {
            BoxWithConstraints {
                Canvas(modifier = Modifier.size(maxWidth)) {
                    val cellSize = size.width / 16
                    state.loadedPalette.colors.forEachIndexed { index, color ->
                        val x = (index % 16) * cellSize
                        val y = (index / 16) * cellSize
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

@Preview
@Composable
private fun PreviewCpsDebugContent() {
    PalDebugContent(
        state = PalDebugViewModel.State()
    )
}
