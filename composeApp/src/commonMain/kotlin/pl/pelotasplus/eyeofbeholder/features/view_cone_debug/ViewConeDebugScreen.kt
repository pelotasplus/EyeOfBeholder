package pl.pelotasplus.eyeofbeholder.features.view_cone_debug

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import pl.pelotasplus.eyeofbeholder.data.model.DialogAnswer
import pl.pelotasplus.eyeofbeholder.data.model.Direction

@Composable
fun ViewConeDebugScreen(
    level: String? = null,
    startX: Int? = null,
    startY: Int? = null,
    startDirection: Direction? = null,
    viewModel: ViewConeDebugViewModel = koinViewModel(),
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(level, startX, startY, startDirection) {
        viewModel.onEvent(
            ViewConeDebugViewModel.Event.Initialize(level, startX, startY, startDirection)
        )
    }

    ViewConeDebugContent(
        modifier = modifier,
        state = state,
        onControlClick = { control ->
            when (control) {
                PlayFieldControl.FORWARD ->
                    viewModel.onEvent(ViewConeDebugViewModel.Event.MoveForward)

                PlayFieldControl.BACKWARD ->
                    viewModel.onEvent(ViewConeDebugViewModel.Event.MoveBackwards)

                PlayFieldControl.TURN_LEFT ->
                    viewModel.onEvent(ViewConeDebugViewModel.Event.RotateLeft)

                PlayFieldControl.TURN_RIGHT ->
                    viewModel.onEvent(ViewConeDebugViewModel.Event.RotateRight)

                PlayFieldControl.STRAFE_LEFT ->
                    viewModel.onEvent(ViewConeDebugViewModel.Event.StrafeLeft)

                PlayFieldControl.STRAFE_RIGHT ->
                    viewModel.onEvent(ViewConeDebugViewModel.Event.StrafeRight)

                // no camp screen yet
                PlayFieldControl.CAMP -> Unit
            }
        },
        onDialogAnswer = { answer ->
            viewModel.onEvent(ViewConeDebugViewModel.Event.DialogAnswered(answer))
        },
    )
}

@Composable
private fun ViewConeDebugContent(
    state: ViewConeDebugViewModel.State,
    modifier: Modifier = Modifier,
    onControlClick: (PlayFieldControl) -> Unit = {},
    onDialogAnswer: (DialogAnswer) -> Unit = {},
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        val image = state.viewPort ?: return@BoxWithConstraints

        val density = LocalDensity.current
        val availableWidth = with(density) { maxWidth.toPx() }
        val availableHeight = with(density) { maxHeight.toPx() }
        val scaleFactor = minOf(
            availableWidth / image.width,
            availableHeight / image.height
        ).toInt().coerceAtLeast(1)

        Box(
            modifier = Modifier
                .size(
                    width = with(density) { (image.width * scaleFactor).toDp() },
                    height = with(density) { (image.height * scaleFactor).toDp() },
                )
                .pointerInput(scaleFactor, state.dialog) {
                    detectTapGestures { offset ->
                        val x = (offset.x / scaleFactor).toInt()
                        val y = (offset.y / scaleFactor).toInt()

                        // a question owns the screen until it is answered
                        val buttons = state.dialog?.scene?.buttons
                        if (buttons != null) {
                            buttons.indexOfFirst { it.contains(x, y) }
                                .takeIf { it >= 0 }
                                ?.let { onDialogAnswer(DialogAnswer.forButton(it)) }
                        } else {
                            PlayFieldControl.at(screenX = x, screenY = y)?.let(onControlClick)
                        }
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawImage(
                    image = image,
                    dstSize = IntSize(image.width * scaleFactor, image.height * scaleFactor),
                    // nearest-neighbor keeps the retro pixels crisp
                    filterQuality = FilterQuality.None
                )
            }
        }

        state.inf?.let { inf ->
            Text(
                text = "${inf.name.removeSuffix(".INF")}  ${state.playerX}x${state.playerY}  ${state.direction}",
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    // clears the Debug button above it
                    .padding(top = 56.dp, end = 12.dp)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}
