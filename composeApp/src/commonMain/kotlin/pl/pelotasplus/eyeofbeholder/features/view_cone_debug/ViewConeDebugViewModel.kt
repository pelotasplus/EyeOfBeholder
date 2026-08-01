package pl.pelotasplus.eyeofbeholder.features.view_cone_debug

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.ImageBitmap
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.Inf
import pl.pelotasplus.eyeofbeholder.data.model.Cps
import pl.pelotasplus.eyeofbeholder.data.model.DialogAnswer
import pl.pelotasplus.eyeofbeholder.data.model.DialogueScene
import pl.pelotasplus.eyeofbeholder.data.model.Font
import pl.pelotasplus.eyeofbeholder.data.model.LevelScriptRunner
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.script.Dialog
import pl.pelotasplus.eyeofbeholder.data.model.script.ScriptOffset
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.PlayField
import pl.pelotasplus.eyeofbeholder.data.model.ScriptEvent
import pl.pelotasplus.eyeofbeholder.data.model.ScriptOutcome
import pl.pelotasplus.eyeofbeholder.data.model.ViewPort
import pl.pelotasplus.eyeofbeholder.data.model.toImageBitmap
import pl.pelotasplus.eyeofbeholder.data.repository.CpsRepository
import pl.pelotasplus.eyeofbeholder.data.repository.DialogueTextRepository
import pl.pelotasplus.eyeofbeholder.data.repository.FontRepository
import pl.pelotasplus.eyeofbeholder.data.repository.ViewConeRepository

@Stable
class ViewConeDebugViewModel(
    private val viewConeRepository: ViewConeRepository,
    private val cpsRepository: CpsRepository,
    private val dialogueTextRepository: DialogueTextRepository,
    private val fontRepository: FontRepository,
) : ViewModel() {

    private var playFieldBackground: Cps? = null
    private var decorations: Cps? = null
    private var dialogueFrame: Cps? = null
    private var font: Font? = null
    private var scriptRunner: LevelScriptRunner? = null

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    fun onEvent(event: Event) {
        when (event) {
            is Event.Initialize -> onInitialize(
                level = event.level,
                startX = event.startX,
                startY = event.startY,
                startDirection = event.startDirection,
            )
            is Event.DialogAnswered -> onDialogAnswered(event.answer)
            is Event.OnLevelSelected -> onVmpSelected(event.name)
            Event.MoveForward -> onMoveForward()
            Event.MoveBackwards -> onMoveBackwards()
            Event.StrafeLeft -> onStrafe(left = true)
            Event.StrafeRight -> onStrafe(left = false)
            Event.RotateRight -> onRotateRight()
            Event.RotateLeft -> onRotateLeft()
        }
    }

    /**
     * [level] is the INF picked from the Levels screen, or null to open the
     * default level at its hardcoded start position. A start coordinate left
     * null keeps the one the party already stands on.
     */
    private fun onInitialize(
        level: String?,
        startX: Int? = null,
        startY: Int? = null,
        startDirection: Direction? = null,
    ) {
        viewModelScope.launch {
            cpsRepository.loadCps(PLAY_FIELD_CPS)
                .onSuccess { playFieldBackground = it }
                .onFailure { Logger.e(it) { "Error while loading $PLAY_FIELD_CPS" } }
            cpsRepository.loadCps(DECORATIONS_CPS)
                .onSuccess { decorations = it }
                .onFailure { Logger.e(it) { "Error while loading $DECORATIONS_CPS" } }
            cpsRepository.loadCps(DIALOGUE_FRAME_CPS)
                .onSuccess { dialogueFrame = it }
                .onFailure { Logger.e(it) { "Error while loading $DIALOGUE_FRAME_CPS" } }
            fontRepository.loadFont(DIALOGUE_FONT)
                .onSuccess { font = it }
                .onFailure { Logger.e(it) { "Error while loading $DIALOGUE_FONT" } }
            renderViewPort()
        }

        if (level != null) {
            onVmpSelected(
                name = level,
                playerX = startX,
                playerY = startY,
                direction = startDirection
            )
            return
        }

        // entrance to the temple, stairs down -- default start
        onVmpSelected(
            DEFAULT_LEVEL,
            playerX = DEFAULT_PLAYER_X,
            playerY = DEFAULT_PLAYER_Y,
            direction = DEFAULT_DIRECTION
        )

        // other scenes worth rendering while debugging:
        // silver tower 1 -- start:      LEVEL7.INF (15, 6) EAST
        //                               LEVEL7.INF (13, 3) SOUTH
        // four guards at (10,20):       LEVEL1.INF (10, 18) SOUTH
        // temple level 2:               LEVEL6.INF (27, 29) NORTH
        // https://gamerwalkthroughs.com/eye-of-the-beholder-2/temple-level-2/
    }

    private fun onVmpSelected(
        name: String,
        playerX: Int? = null,
        playerY: Int? = null,
        direction: Direction? = null
    ) {
        viewModelScope.launch {
            viewConeRepository
                .loadLevel(name = name)
                .onSuccess { inf ->
                    scriptRunner = LevelScriptRunner(inf.script)
                    _state.update {
                        it.copy(
                            inf = inf,
                            playerY = playerY ?: it.playerY,
                            playerX = playerX ?: it.playerX,
                            direction = direction ?: it.direction
                        )
                    }
                    renderViewPort()
                }
                .onFailure {
                    Logger.e(it) { "Error while loading vmp: $name" }
                }
        }
    }

    private fun onPlayerPositionChanged(x: Int? = null, y: Int? = null) {
        check(x != null || y != null) {
            "Either x or y must be non-null"
        }
        val normalizedX = if (x == null) {
            _state.value.playerX
        } else if (x < 0) {
            0
        } else {
            x
        }
        val normalizedY = if (y == null) {
            _state.value.playerY
        } else if (y < 0) {
            0
        } else {
            y
        }
        _state.update {
            it.copy(
                playerX = normalizedX,
                playerY = normalizedY
            )
        }

        if (!runTriggers(Location(normalizedX, normalizedY))) {
            renderViewPort()
        }
    }

    /** @return true when the square's script took over, e.g. by changing level. */
    private fun runTriggers(at: Location): Boolean {
        val inf = _state.value.inf ?: return false
        val runner = scriptRunner ?: return false

        val outcome = runner.onEvent(
            triggers = inf.triggers,
            event = ScriptEvent.PARTY_ENTERED,
            party = PartyState(position = at, facing = _state.value.direction),
        )

        return applyOutcome(outcome, at)
    }

    /** @return true when the outcome took over the screen. */
    private fun applyOutcome(outcome: ScriptOutcome, at: Location): Boolean {
        return when (outcome) {
            is ScriptOutcome.ChangeLevel -> {
                Logger.i(TAG) { "Changing to level ${outcome.level} at ${outcome.location}" }
                onVmpSelected(
                    name = "LEVEL${outcome.level}.INF",
                    playerX = outcome.location.x,
                    playerY = outcome.location.y,
                    direction = outcome.direction
                )
                true
            }

            is ScriptOutcome.MoveParty -> {
                Logger.i(TAG) { "Script moved the party to ${outcome.destination}" }
                _state.update {
                    it.copy(
                        playerX = outcome.destination.x,
                        playerY = outcome.destination.y
                    )
                }
                renderViewPort()
                true
            }

            is ScriptOutcome.AskThePlayer -> {
                Logger.i(TAG) { "Script is asking the player: ${outcome.dialog}" }
                showDialog(outcome, at)
                true
            }

            ScriptOutcome.Nothing -> false
        }
    }

    /**
     * A script stopped to ask something. The speech comes from TEXT.DAT and
     * the button words from the level's own messages.
     */
    private fun showDialog(ask: ScriptOutcome.AskThePlayer, at: Location) {
        val inf = _state.value.inf ?: return
        val messages = inf.messages
        fun button(id: Int) = messages.getOrNull(id)?.takeIf { it.isNotBlank() }

        viewModelScope.launch {
            val text = dialogueTextRepository.text(ask.dialog.textId)
                .onFailure { Logger.e(it) { "No dialogue text ${ask.dialog.textId}" } }
                .getOrNull()
                .orEmpty()

            val labels = listOfNotNull(
                button(ask.dialog.button1),
                button(ask.dialog.button2),
                button(ask.dialog.button3),
            )

            _state.update {
                it.copy(
                    dialog = DialogPrompt(
                        scene = sceneFor(ask.scene, text, labels),
                        resumeAt = ask.resumeAt,
                        askedAt = at,
                    )
                )
            }
            renderViewPort()
        }
    }

    /**
     * Turns the script's drawing instructions into something the play field can
     * put on screen.
     *
     * Speakers are packed four to a file, and the instruction's x and y name the
     * corner to cut out — x in units of eight pixels, as the original counts
     * them. Where it lands is not the script's business: the portrait always
     * goes in the same place inside the frame.
     */
    private suspend fun sceneFor(
        scene: List<Dialog>,
        text: String,
        buttonLabels: List<String>,
    ): DialogueScene {
        val font = font ?: return DialogueScene(null, null, emptyList(), emptyList())

        val instruction = scene.filterIsInstance<Dialog.DisplayPicture>().lastOrNull()
        val portrait = instruction?.let {
            cpsRepository.loadCps("${it.pictureName.uppercase()}.CPS")
                .onFailure { error -> Logger.e(error) { "No picture ${it.pictureName}" } }
                .getOrNull()
                ?.let { cps ->
                    DialogueScene.Picture(
                        cps = cps,
                        sourceLeft = it.x * ViewPort.TILE_SIZE,
                        sourceTop = it.y,
                        width = Cps.PORTRAIT_WIDTH,
                        height = Cps.PORTRAIT_HEIGHT,
                        left = DialogueScene.PORTRAIT_LEFT,
                        top = DialogueScene.PORTRAIT_TOP,
                    )
                }
        }

        return DialogueScene.layout(
            frame = dialogueFrame,
            portrait = portrait,
            text = text,
            buttonLabels = buttonLabels,
            font = font,
        )
    }

    private fun onDialogAnswered(answer: DialogAnswer) {
        val dialog = _state.value.dialog ?: return
        val runner = scriptRunner ?: return

        _state.update { it.copy(dialog = null) }

        val outcome = runner.answer(
            resumeAt = dialog.resumeAt,
            party = PartyState(position = dialog.askedAt, facing = _state.value.direction),
            answer = answer,
        )
        if (!applyOutcome(outcome, dialog.askedAt)) {
            renderViewPort()
        }
    }

    private fun onDirectionChanged(direction: Direction) {
        _state.update { it.copy(direction = direction) }
        renderViewPort()
    }

    private fun renderViewPort() {
        val inf = _state.value.inf ?: return
        val background = playFieldBackground
        val decorations = decorations

        viewModelScope.launch {
            val sublevel = inf.subLevels[0]
            viewConeRepository.renderPosition(
                items = inf.items,
                monsters = inf.monsterInstances,
                sublevel = sublevel,
                playerX = _state.value.playerX,
                playerY = _state.value.playerY,
                direction = _state.value.direction
            ).onSuccess { viewPort ->
                val image = if (background != null && decorations != null) {
                    PlayField(background, decorations, sublevel.palette, font)
                        .render(viewPort, _state.value.direction, _state.value.dialog?.scene)
                        .toImageBitmap()
                } else {
                    // the frame art failed to load; still show the raw view
                    viewPort.toImageBitmap()
                }
                _state.update { it.copy(viewPort = image) }
            }.onFailure {
                Logger.e(it) { "Error while rendering position" }
            }
        }
    }

    private fun onStrafe(left: Boolean) {
        val direction = _state.value.direction
        val sideways = if (left) {
            Direction.entries[(direction.ordinal + 3) % Direction.entries.size]
        } else {
            Direction.entries[(direction.ordinal + 1) % Direction.entries.size]
        }
        when (sideways) {
            Direction.NORTH -> onPlayerPositionChanged(y = _state.value.playerY - 1)
            Direction.EAST -> onPlayerPositionChanged(x = _state.value.playerX + 1)
            Direction.SOUTH -> onPlayerPositionChanged(y = _state.value.playerY + 1)
            Direction.WEST -> onPlayerPositionChanged(x = _state.value.playerX - 1)
        }
    }

    private fun onMoveForward() {
        when (_state.value.direction) {
            Direction.NORTH -> {
                val newY = _state.value.playerY - 1
                onPlayerPositionChanged(y = newY)
            }

            Direction.EAST -> {
                val newX = _state.value.playerX + 1
                onPlayerPositionChanged(x = newX)
            }

            Direction.SOUTH -> {
                val newY = _state.value.playerY + 1
                onPlayerPositionChanged(y = newY)
            }

            Direction.WEST -> {
                val newX = _state.value.playerX - 1
                onPlayerPositionChanged(x = newX)
            }
        }
    }

    private fun onMoveBackwards() {
        when (_state.value.direction) {
            Direction.NORTH -> {
                val newY = _state.value.playerY + 1
                onPlayerPositionChanged(y = newY)
            }

            Direction.EAST -> {
                val newX = _state.value.playerX - 1
                onPlayerPositionChanged(x = newX)
            }

            Direction.SOUTH -> {
                val newY = _state.value.playerY - 1
                onPlayerPositionChanged(y = newY)
            }

            Direction.WEST -> {
                val newX = _state.value.playerX + 1
                onPlayerPositionChanged(x = newX)
            }
        }
    }

    private fun onRotateRight() {
        val currentDirection = _state.value.direction
        val directions = Direction.entries
        val currentIndex = directions.indexOf(currentDirection)
        val newIndex = (currentIndex + 1) % directions.size
        onDirectionChanged(directions[newIndex])
    }

    private fun onRotateLeft() {
        val currentDirection = _state.value.direction
        val directions = Direction.entries
        val currentIndex = directions.indexOf(currentDirection)
        val newIndex = (currentIndex - 1 + directions.size) % directions.size
        onDirectionChanged(directions[newIndex])
    }

    sealed class Event {
        data class Initialize(
            val level: String?,
            val startX: Int? = null,
            val startY: Int? = null,
            val startDirection: Direction? = null,
        ) : Event()
        data class DialogAnswered(val answer: DialogAnswer) : Event()
        data class OnLevelSelected(val name: String) : Event()
        data object MoveForward : Event()
        data object MoveBackwards : Event()
        data object StrafeLeft : Event()
        data object StrafeRight : Event()
        data object RotateRight : Event()
        data object RotateLeft : Event()
    }

    /** A question a script is waiting on, drawn as [scene] over the play field. */
    data class DialogPrompt(
        val scene: DialogueScene,
        val resumeAt: ScriptOffset,
        val askedAt: Location,
    )

    data class State(
        val inf: Inf? = null,
        val dialog: DialogPrompt? = null,

        val viewPort: ImageBitmap? = null,

        val playerX: Int = DEFAULT_PLAYER_X,
        val playerY: Int = DEFAULT_PLAYER_Y,
        val direction: Direction = DEFAULT_DIRECTION
    )

    companion object {
        private const val TAG = "ViewConeDebugViewModel"
        private const val PLAY_FIELD_CPS = "PLAYFLD.CPS"
        private const val DECORATIONS_CPS = "DECORATE.CPS"
        private const val DIALOGUE_FRAME_CPS = "BORDER.CPS"
        private const val DIALOGUE_FONT = "FONT6.FNT"
        private const val DEFAULT_LEVEL = "LEVEL5.INF"
        private const val DEFAULT_PLAYER_X = 14
        private const val DEFAULT_PLAYER_Y = 9
        private val DEFAULT_DIRECTION = Direction.SOUTH
    }
}
