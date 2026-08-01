package pl.pelotasplus.eyeofbeholder.features.maz_debug

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import pl.pelotasplus.eyeofbeholder.data.model.Maz

private val FILE_LIST_WIDTH = 160.dp

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
        Row(modifier = modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .width(FILE_LIST_WIDTH)
                    .fillMaxHeight()
                    .padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(state.allMazs) { mazName ->
                    Button(
                        onClick = { onMazSelected(mazName) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = mazName)
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (state.loadedMaz != null) {
                    Text(
                        text = "File: ${state.loadedMaz.name}",
                        style = MaterialTheme.typography.titleMedium
                    )

                    val mazWidth = state.loadedMaz.width

                    val blockedColor = Color(200, 200, 200)
                    val wallColor = Color(55, 55, 55)
                    val textMeasurer = rememberTextMeasurer()

                    BoxWithConstraints(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        val density = LocalDensity.current
                        val availableWidth = with(density) { maxWidth.toPx() }
                        val availableHeight = with(density) { maxHeight.toPx() }
                        val cellSize = 8f
                        // integer scale that fits the whole map in both directions
                        val scaleFactor = minOf(
                            availableWidth / (state.loadedMaz.width * cellSize),
                            availableHeight / (state.loadedMaz.height * cellSize)
                        ).toInt().coerceAtLeast(1)
                        Canvas(
                            modifier = Modifier.size(
                                width = with(density) {
                                    (state.loadedMaz.width * cellSize * scaleFactor).toDp()
                                },
                                height = with(density) {
                                    (state.loadedMaz.height * cellSize * scaleFactor).toDp()
                                },
                            )
                        ) {
                            scale(scaleFactor.toFloat(), pivot = Offset.Zero) {
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
                                        if (square.north is Maz.WallType.FixedWall) {
                                            drawLine(
                                                color = wallColor,
                                                start = Offset(x, y),
                                                end = Offset(x + cellSize, y)
                                            )
                                        }
                                        if (square.east is Maz.WallType.FixedWall) {
                                            drawLine(
                                                color = wallColor,
                                                start = Offset(x + cellSize, y),
                                                end = Offset(x + cellSize, y + cellSize)
                                            )
                                        }
                                        if (square.south is Maz.WallType.FixedWall) {
                                            drawLine(
                                                color = wallColor,
                                                start = Offset(x, y + cellSize),
                                                end = Offset(x + cellSize, y + cellSize)
                                            )
                                        }
                                        if (square.west is Maz.WallType.FixedWall) {
                                            drawLine(
                                                color = wallColor,
                                                start = Offset(x, y),
                                                end = Offset(x, y + cellSize)
                                            )
                                        }
                                    }
                                }
                            }
                            // Draw coordinates outside scale block for readable text
                            state.loadedMaz.squares.forEach { square ->
                                val x = square.x * cellSize * scaleFactor
                                val y = square.y * cellSize * scaleFactor
                                if (!square.blockedAllSides) {
                                    drawText(
                                        textMeasurer = textMeasurer,
                                        text = "${square.x},${square.y}",
                                        topLeft = Offset(x + 2f, y + 2f),
                                        style = TextStyle(
                                            fontSize = 4.sp,
                                            color = Color.Black
                                        )
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
