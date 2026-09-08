package pl.pelotasplus.eyeofbeholder.features.view_cone_debug

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import pl.pelotasplus.eyeofbeholder.LocalPlayFieldFocus
import pl.pelotasplus.eyeofbeholder.data.model.Debugging
import pl.pelotasplus.eyeofbeholder.data.model.DialogAnswer
import pl.pelotasplus.eyeofbeholder.data.model.DialogueScene
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.Maz
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.Typing
import pl.pelotasplus.eyeofbeholder.data.model.ViewPort
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.WallSide
import pl.pelotasplus.eyeofbeholder.data.model.getWall

@Composable
fun ViewConeDebugScreen(
    level: String? = null,
    startX: Int? = null,
    startY: Int? = null,
    startDirection: Direction? = null,
    viewModel: ViewConeDebugViewModel = koinViewModel(),
    debugging: Debugging = koinInject(),
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val showingMap by debugging.showingMap.collectAsState()
    val debugMenuOpen by debugging.menuIsOpen.collectAsState()

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

                PlayFieldControl.CAMP ->
                    viewModel.onEvent(ViewConeDebugViewModel.Event.Camp)
            }
        },
        onDialogAnswer = { answer ->
            viewModel.onEvent(ViewConeDebugViewModel.Event.DialogAnswered(answer))
        },
        onViewClick = { x, y ->
            viewModel.onEvent(ViewConeDebugViewModel.Event.ClickedTheView(x, y))
        },
        onUseClick = { x, y ->
            viewModel.onEvent(ViewConeDebugViewModel.Event.UsedWhatIsAt(x, y))
        },
        onFrontRankStrike = {
            viewModel.onEvent(ViewConeDebugViewModel.Event.FrontRankStrikes)
        },
        onTyping = { viewModel.onEvent(ViewConeDebugViewModel.Event.Typed(it)) },
        showingMap = showingMap,
        debugMenuOpen = debugMenuOpen,
    )
}

@Composable
private fun ViewConeDebugContent(
    state: ViewConeDebugViewModel.State,
    modifier: Modifier = Modifier,
    onControlClick: (PlayFieldControl) -> Unit = {},
    onDialogAnswer: (DialogAnswer) -> Unit = {},
    onViewClick: (x: Int, y: Int) -> Unit = { _, _ -> },
    onUseClick: (x: Int, y: Int) -> Unit = { _, _ -> },
    onFrontRankStrike: () -> Unit = {},
    onTyping: (Typing) -> Unit = {},
    showingMap: Boolean = true,
    debugMenuOpen: Boolean = false,
) {
    val keyboard = remember { FocusRequester() }
    val playFieldFocus = LocalPlayFieldFocus.current

    DisposableEffect(playFieldFocus, keyboard) {
        playFieldFocus.goesTo(keyboard)
        onDispose { playFieldFocus.noLongerGoesTo(keyboard) }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(keyboard)
            // key events only reach a node that can hold focus
            .focusable()
            .onKeyEvent { event ->
                // a held key repeats as more KeyDowns, which is how walking
                // holds up; KeyUp would walk a second square on release
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false

                // while a save is being named the keys spell rather than steer
                if (state.menu?.naming != null) {
                    typingFor(event.key, event.utf16CodePoint.toChar())?.let(onTyping)
                    return@onKeyEvent true
                }

                // not one of the game's keys: the bench's, for swinging the
                // whole front rank without clicking each slot in turn
                if (event.key == Key.Spacebar) {
                    onFrontRankStrike()
                    return@onKeyEvent true
                }

                val control = playFieldControlFor(event.key) ?: return@onKeyEvent false
                onControlClick(control)
                true
            },
        contentAlignment = Alignment.Center,
    ) {
        LaunchedEffect(Unit) { keyboard.requestFocus() }

        val image = state.viewPort ?: return@BoxWithConstraints

        val density = LocalDensity.current
        val availableWidth = with(density) { maxWidth.toPx() }
        val availableHeight = with(density) { maxHeight.toPx() }
        val scaleFactor = minOf(
            availableWidth / image.width,
            availableHeight / image.height
        ).toInt().coerceAtLeast(1)

        // where the pointer is, so that whatever is being held can follow it
        var pointer by remember { mutableStateOf(Offset.Unspecified) }

        Box(
            modifier = Modifier
                .size(
                    width = with(density) { (image.width * scaleFactor).toDp() },
                    height = with(density) { (image.height * scaleFactor).toDp() },
                )
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            pointer = event.changes.firstOrNull()?.position
                                ?: Offset.Unspecified
                        }
                    }
                }
                // The second mouse button uses what a slot holds rather than
                // picking it up. It is taken on the initial pass and consumed,
                // so the tap detector below never sees it and a page being read
                // is not also an item being taken out of a hand.
                .pointerInput(scaleFactor, state.dialog, state.menu) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.type != PointerEventType.Press) continue
                            if (!event.buttons.isSecondaryPressed) continue

                            val at = event.changes.firstOrNull()?.position ?: continue
                            event.changes.forEach { it.consume() }

                            use(at, scaleFactor, state, onUseClick)
                        }
                    }
                }
                .pointerInput(scaleFactor, state.dialog, state.menu) {
                    // a touch has no second button, so holding is what uses
                    // what a slot holds there
                    detectTapGestures(
                        onLongPress = { offset -> use(offset, scaleFactor, state, onUseClick) },
                    ) { offset ->
                        // the Debug menu takes focus and does not give it back,
                        // so touching the play field claims the keys again
                        keyboard.requestFocus()

                        val x = (offset.x / scaleFactor).toInt()
                        val y = (offset.y / scaleFactor).toInt()

                        // a question owns the screen until it is answered
                        val scene = state.dialog?.scene
                        val menu = state.menu

                        if (scene != null) {
                            // a picture has nothing to press and is put away by
                            // a click anywhere
                            if (scene.readOff == DialogueScene.ReadOff.APictureAlone) {
                                onDialogAnswer(DialogAnswer.UNASKED)
                            } else {
                                scene.buttons.indexOfFirst { it.contains(x, y) }
                                    .takeIf { it >= 0 }
                                    ?.let { onDialogAnswer(DialogAnswer.forButton(it)) }
                            }
                        } else if (menu != null) {
                            // the menu box reaches below the view window, so a
                            // click anywhere on it is the menu's, not the
                            // world's. Camp still answers, to shut the menu.
                            if (PlayFieldControl.at(x, y) == PlayFieldControl.CAMP) {
                                onControlClick(PlayFieldControl.CAMP)
                            } else {
                                onViewClick(x, y)
                            }
                        } else {
                            // whatever no button claims is the world's: the
                            // view, the party's faces, or an open page
                            val control = PlayFieldControl.at(screenX = x, screenY = y)
                            if (control != null) onControlClick(control) else onViewClick(x, y)
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

                // What is being held is the pointer, so it is drawn over
                // everything and centred on it rather than laid out anywhere.
                val held = state.held ?: return@Canvas
                if (pointer == Offset.Unspecified) return@Canvas

                val width = held.width * scaleFactor
                val height = held.height * scaleFactor
                drawImage(
                    image = held,
                    dstOffset = IntOffset(
                        x = (pointer.x - width / 2).toInt(),
                        y = (pointer.y - height / 2).toInt(),
                    ),
                    dstSize = IntSize(width, height),
                    filterQuality = FilterQuality.None,
                )
            }
        }

        // The little map of where the party have been, in the true bottom-right
        // corner of the window rather than of the game area. It carries no
        // pointer handler of its own, so a click on it falls through to
        // whatever game control sits under it.
        if (showingMap) {
            state.inf?.subLevels?.getOrNull(state.subLevel)?.maz?.let { maz ->
                AutoMap(
                    maz = maz,
                    visited = state.visited,
                    party = state.game.party,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(with(density) { (Debugging.MAP_SIDE * scaleFactor).toDp() }),
                )
            }
        }

        // Where the party are standing, for whoever is building the game. It
        // comes and goes with the panel of switches rather than sitting over
        // the corner of the dungeon for a player who never asked for it.
        state.inf?.takeIf { debugMenuOpen }?.let { inf ->
            Text(
                text = with(state.game.party) {
                    "${inf.name.removeSuffix(".INF")}  ${position.x}x${position.y}  $facing"
                },
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

/**
 * A map of the squares the party have stood on, and the walls around them.
 *
 * Fog of war: only [visited] squares are drawn, each with the walls that face
 * it, and the party as a dot with a tail for the way they face. The whole 32×32
 * maze is fitted to the box, so it is small — enough to place oneself by.
 */
@Composable
private fun AutoMap(
    maz: Maz,
    visited: Set<Location>,
    party: PartyState,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val cell = minOf(size.width / maz.width, size.height / maz.height)
        val originX = size.width - cell * maz.width
        val originY = size.height - cell * maz.height

        drawRect(Color(0xE6101018))

        val wallColour = Color(0xFFC8C8D8)
        val stroke = maxOf(1f, cell * 0.18f)

        visited.forEach { at ->
            val left = originX + at.x * cell
            val top = originY + at.y * cell
            drawRect(Color(0xFF33333F), topLeft = Offset(left, top), size = Size(cell, cell))

            val square = maz.square(at)
            if (square.getWall(WallSide.NORTH) != Maz.WallType.NoWall) {
                drawLine(wallColour, Offset(left, top), Offset(left + cell, top), stroke)
            }
            if (square.getWall(WallSide.SOUTH) != Maz.WallType.NoWall) {
                drawLine(wallColour, Offset(left, top + cell), Offset(left + cell, top + cell), stroke)
            }
            if (square.getWall(WallSide.WEST) != Maz.WallType.NoWall) {
                drawLine(wallColour, Offset(left, top), Offset(left, top + cell), stroke)
            }
            if (square.getWall(WallSide.EAST) != Maz.WallType.NoWall) {
                drawLine(wallColour, Offset(left + cell, top), Offset(left + cell, top + cell), stroke)
            }
        }

        val hereX = originX + (party.position.x + 0.5f) * cell
        val hereY = originY + (party.position.y + 0.5f) * cell
        val (dx, dy) = party.facing.transformCoordinates(0, -1)
        drawCircle(Color(0xFFFFD54F), radius = cell * 0.35f, center = Offset(hereX, hereY))
        drawLine(
            Color(0xFFFFD54F),
            Offset(hereX, hereY),
            Offset(hereX + dx * cell * 0.7f, hereY + dy * cell * 0.7f),
            maxOf(1f, cell * 0.25f),
        )
    }
}

/**
 * Using what a slot holds, from whichever gesture asked for it.
 *
 * A box waiting to be answered owns the screen, and so does an open menu;
 * neither is a moment to be reading something else.
 */
private fun use(
    at: Offset,
    scaleFactor: Int,
    state: ViewConeDebugViewModel.State,
    onUseClick: (x: Int, y: Int) -> Unit,
) {
    if (state.dialog != null || state.menu != null) return

    onUseClick((at.x / scaleFactor).toInt(), (at.y / scaleFactor).toInt())
}
