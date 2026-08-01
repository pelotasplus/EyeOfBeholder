package pl.pelotasplus.eyeofbeholder.features.view_cone_debug

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import pl.pelotasplus.eyeofbeholder.features.main_debug.DebugMenuPanel
import pl.pelotasplus.eyeofbeholder.navigation.Route

@Composable
fun ViewConeDebugScreen(
    level: String? = null,
    onDebugDestinationClick: (Route) -> Unit = {},
    viewModel: ViewConeDebugViewModel = koinViewModel(),
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var campMenuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(level) {
        viewModel.onEvent(ViewConeDebugViewModel.Event.Initialize(level))
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

                PlayFieldControl.CAMP -> campMenuOpen = !campMenuOpen
            }
        },
        campMenuOpen = campMenuOpen,
        onDebugDestinationClick = onDebugDestinationClick,
        onCampMenuDismiss = { campMenuOpen = false },
    )
}

@Composable
private fun ViewConeDebugContent(
    state: ViewConeDebugViewModel.State,
    modifier: Modifier = Modifier,
    onControlClick: (PlayFieldControl) -> Unit = {},
    campMenuOpen: Boolean = false,
    onDebugDestinationClick: (Route) -> Unit = {},
    onCampMenuDismiss: () -> Unit = {},
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
                .pointerInput(scaleFactor) {
                    detectTapGestures { offset ->
                        PlayFieldControl.at(
                            screenX = (offset.x / scaleFactor).toInt(),
                            screenY = (offset.y / scaleFactor).toInt(),
                        )?.let(onControlClick)
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

        if (campMenuOpen) {
            DebugMenuPanel(
                onDestinationClick = onDebugDestinationClick,
                onDismiss = onCampMenuDismiss,
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
    }
}
