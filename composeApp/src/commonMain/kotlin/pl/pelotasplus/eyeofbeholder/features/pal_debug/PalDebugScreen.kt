package pl.pelotasplus.eyeofbeholder.features.pal_debug

import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

private val FILE_LIST_WIDTH = 160.dp

/** The 256 palette entries are laid out as a 16x16 grid. */
private const val GRID_COLUMNS = 16

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
    Row(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .width(FILE_LIST_WIDTH)
                .fillMaxHeight()
                .padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(state.allPals) { palName ->
                Button(
                    onClick = { onPalSelected(palName) },
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
                .padding(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (state.loadedPalette != null) {
                // the grid is square, so fit it to the smaller side
                val side = minOf(maxWidth, maxHeight)
                Canvas(modifier = Modifier.size(side)) {
                    val cellSize = size.width / GRID_COLUMNS
                    state.loadedPalette.colors.forEachIndexed { index, color ->
                        val x = (index % GRID_COLUMNS) * cellSize
                        val y = (index / GRID_COLUMNS) * cellSize
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
