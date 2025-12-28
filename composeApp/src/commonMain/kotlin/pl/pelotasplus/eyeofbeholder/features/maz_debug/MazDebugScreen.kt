package pl.pelotasplus.eyeofbeholder.features.maz_debug

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import pl.pelotasplus.eyeofbeholder.data.model.isBlocked

@Composable
fun MazDebugScreen(
    viewModel: MazDebugViewModel = koinViewModel(),
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    MazDebugContent(
        modifier = modifier,
        state = state,
        onMazSelected = {
            viewModel.onEvent(MazDebugViewModel.Event.OnMazSelected(it))
        }
    )
}

@Composable
private fun MazDebugContent(
    state: MazDebugViewModel.State,
    modifier: Modifier = Modifier,
    onMazSelected: (String) -> Unit = {},
) {
    if (state.isLoading) {
        CircularProgressIndicator()
    } else {
        Column(modifier = modifier) {
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                items(state.allMazs) { mazName ->
                    Button(
                        onClick = { onMazSelected(mazName) }
                    ) {
                        Text(text = mazName)
                    }
                }
            }

            if (state.loadedMaz != null) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(8.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "File: ${state.loadedMaz.name}",
                        style = MaterialTheme.typography.titleMedium
                    )

                    val mazWidth = state.loadedMaz.width

                    val blockedColor = Color(55, 55, 55)
                    val wallColor = blockedColor

                    BoxWithConstraints {
                        Canvas(modifier = Modifier.size(maxWidth)) {
                            val cellSize = size.width / mazWidth
                            state.loadedMaz.squares.forEachIndexed { index, square ->
                                val x = (index % mazWidth) * cellSize
                                val y = (index / mazWidth) * cellSize
                                if (square.blockedAllSides) {
                                    drawRect(
                                        color = blockedColor,
                                        topLeft = Offset(x, y),
                                        size = Size(cellSize, cellSize)
                                    )
                                } else {
                                    if (square.north.isBlocked()) {
                                        drawLine(
                                            color = wallColor,
                                            start = Offset(x, y),
                                            end = Offset(x + cellSize, y)
                                        )
                                    }
                                    if (square.east.isBlocked()) {
                                        drawLine(
                                            color = wallColor,
                                            start = Offset(x + cellSize, y),
                                            end = Offset(x + cellSize, y + cellSize)
                                        )
                                    }
                                    if (square.south.isBlocked()) {
                                        drawLine(
                                            color = wallColor,
                                            start = Offset(x, y + cellSize),
                                            end = Offset(x + cellSize, y + cellSize)
                                        )
                                    }
                                    if (square.west.isBlocked()) {
                                        drawLine(
                                            color = wallColor,
                                            start = Offset(x, y),
                                            end = Offset(x, y + cellSize)
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
}

@Preview
@Composable
private fun PreviewMazDebugContent() {
    MazDebugContent(
        state = MazDebugViewModel.State()
    )
}
