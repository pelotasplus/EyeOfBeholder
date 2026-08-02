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
import pl.pelotasplus.eyeofbeholder.data.model.DialogueScene.Companion.MORE
import pl.pelotasplus.eyeofbeholder.data.model.DialogueText
import pl.pelotasplus.eyeofbeholder.data.model.Font
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.levelNumber
import pl.pelotasplus.eyeofbeholder.data.model.LevelScriptRunner
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.script.Dialog
import pl.pelotasplus.eyeofbeholder.data.model.script.ScriptOffset
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.PlayField
import pl.pelotasplus.eyeofbeholder.data.model.ScriptEvent
import pl.pelotasplus.eyeofbeholder.data.model.ScriptRun
import pl.pelotasplus.eyeofbeholder.data.model.ScriptStop
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
    private var speaker: DialogueScene.Picture? = null

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
                    scriptRunner = LevelScriptRunner(inf.script, levelNumber(inf.name))
                    _state.update {
                        val was = it.game.party
                        it.copy(
                            inf = inf,
                            game = it.game.copy(
                                party = was.copy(
                                    position = Location(
                                        x = playerX ?: was.position.x,
                                        y = playerY ?: was.position.y,
                                    ),
                                    facing = direction ?: was.facing,
                                ),
                                monsters = inf.monsterInstances,
                            ),
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
        val steppedTo = Location(
            x = (x ?: party.position.x).coerceAtLeast(0),
            y = (y ?: party.position.y).coerceAtLeast(0),
        )
        _state.update { it.copy(game = it.game.partyMovedTo(steppedTo)) }

        if (!runTriggers(steppedTo)) {
            renderViewPort()
        }
    }

    private val party get() = _state.value.game.party

    /** @return true when the square's script took over, e.g. by changing level. */
    private fun runTriggers(at: Location): Boolean {
        val inf = _state.value.inf ?: return false
        val runner = scriptRunner ?: return false

        return applyRun(
            runner.onEvent(
                triggers = inf.triggers,
                event = ScriptEvent.PARTY_ENTERED,
                state = _state.value.game,
            )
        )
    }

    /**
     * @param answeredWith the answer the script is still running under, when
     *   this came out of [onDialogAnswered] rather than a fresh square.
     * @return true when the script took over the screen.
     */
    private fun applyRun(
        run: ScriptRun,
        answeredWith: DialogAnswer? = null,
    ): Boolean {
        _state.update { it.copy(game = run.state) }

        return when (val stop = run.stoppedTo) {
            is ScriptStop.ChangeLevel -> {
                Logger.i(TAG) { "Changing to level ${stop.level} at ${stop.location}" }
                onVmpSelected(
                    name = "LEVEL${stop.level}.INF",
                    playerX = stop.location.x,
                    playerY = stop.location.y,
                    direction = stop.direction
                )
                true
            }

            is ScriptStop.AskThePlayer -> {
                Logger.i(TAG) { "Script is showing text ${stop.textId} with ${stop.buttons}" }
                showDialog(stop, party.position, answeredWith = answeredWith)
                true
            }

            null -> false
        }
    }

    /**
     * A script stopped to ask something. The speech comes from TEXT.DAT and
     * the button words from the level's own messages.
     */
    private fun showDialog(
        ask: ScriptStop.AskThePlayer,
        at: Location,
        answeredWith: DialogAnswer?,
    ) {
        val inf = _state.value.inf ?: return

        viewModelScope.launch {
            val speech = dialogueTextRepository.text(ask.textId)
                .onFailure { Logger.e(it) { "No dialogue text ${ask.textId}" } }
                .getOrNull()
                ?: DialogueText.EMPTY

            val labels = ask.buttons.mapNotNull { inf.message(it) }
            val unread = speech.pages.drop(1)

            // the party's own line goes in the box above what it answers
            val spoken = (ask.said.mapNotNull { inf.message(it) } + speech.first)
                .filter { it.isNotBlank() }
                .joinToString("\n") { party.fillIn(it) }

            _state.update {
                it.copy(
                    dialog = DialogPrompt(
                        scene = sceneFor(
                            scene = ask.scene,
                            text = spoken,
                            buttonLabels = if (unread.isEmpty()) labels else listOf(MORE),
                            waitsToBeRead = unread.isNotEmpty() || ask.waitsToBeRead,
                        ),
                        resumeAt = ask.resumeAt,
                        askedAt = at,
                        answeredWith = answeredWith,
                        unread = unread,
                        buttons = labels,
                        waitsToBeRead = ask.waitsToBeRead,
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
        waitsToBeRead: Boolean,
    ): DialogueScene {
        val font = font ?: return DialogueScene(null, null, emptyList(), emptyList())

        val instruction = scene.filterIsInstance<Dialog.DisplayPicture>().lastOrNull()
        val portrait = if (instruction == null) {
            // a reply draws no one: whoever is speaking stays up while they talk
            speaker
        } else {
            cpsRepository.loadCps("${instruction.pictureName.uppercase()}.CPS")
                .onFailure { error -> Logger.e(error) { "No picture ${instruction.pictureName}" } }
                .getOrNull()
                ?.let { cps ->
                    DialogueScene.Picture(
                        cps = cps,
                        sourceLeft = instruction.x * ViewPort.TILE_SIZE,
                        sourceTop = instruction.y,
                        width = Cps.PORTRAIT_WIDTH,
                        height = Cps.PORTRAIT_HEIGHT,
                        left = DialogueScene.PORTRAIT_LEFT,
                        top = DialogueScene.PORTRAIT_TOP,
                    )
                }
        }
        speaker = portrait

        return DialogueScene.layout(
            frame = dialogueFrame,
            portrait = portrait,
            text = text,
            buttonLabels = buttonLabels,
            font = font,
            waitsToBeRead = waitsToBeRead,
        )
    }

    /** Puts the next part of a speech up, without letting the script move on. */
    private fun turnThePage(dialog: DialogPrompt) {
        val unread = dialog.unread.drop(1)

        viewModelScope.launch {
            _state.update {
                it.copy(
                    dialog = dialog.copy(
                        scene = sceneFor(
                            scene = emptyList(),
                            text = dialog.unread.first(),
                            buttonLabels = if (unread.isEmpty()) dialog.buttons else listOf(MORE),
                            waitsToBeRead = unread.isNotEmpty() || dialog.waitsToBeRead,
                        ),
                        unread = unread,
                    )
                )
            }
            renderViewPort()
        }
    }

    private fun onDialogAnswered(answer: DialogAnswer) {
        val dialog = _state.value.dialog ?: return
        val runner = scriptRunner ?: return

        if (dialog.unread.isNotEmpty()) {
            turnThePage(dialog)
            return
        }

        _state.update { it.copy(dialog = null) }

        val answered = dialog.answeredWith ?: answer
        val result = runner.answer(
            resumeAt = dialog.resumeAt,
            state = _state.value.game,
            answer = answered,
        )
        if (!applyRun(result, answeredWith = answered)) {
            speaker = null
            renderViewPort()
        }
    }

    private fun onDirectionChanged(direction: Direction) {
        _state.update { it.copy(game = it.game.partyTurnedTo(direction)) }
        renderViewPort()
    }

    private fun renderViewPort() {
        val inf = _state.value.inf ?: return
        val background = playFieldBackground
        val decorations = decorations

        viewModelScope.launch {
            val sublevel = inf.subLevels[PLAYED_SUBLEVEL]
            viewConeRepository.renderPosition(
                items = inf.items,
                monsters = _state.value.game.monsters,
                sublevel = sublevel,
                playerX = party.position.x,
                playerY = party.position.y,
                direction = party.facing
            ).onSuccess { viewPort ->
                val image = if (background != null && decorations != null) {
                    PlayField(background, decorations, sublevel.palette, font)
                        .render(viewPort, party.facing, _state.value.dialog?.scene)
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
        val direction = party.facing
        val sideways = if (left) {
            Direction.entries[(direction.ordinal + 3) % Direction.entries.size]
        } else {
            Direction.entries[(direction.ordinal + 1) % Direction.entries.size]
        }
        when (sideways) {
            Direction.NORTH -> onPlayerPositionChanged(y = party.position.y - 1)
            Direction.EAST -> onPlayerPositionChanged(x = party.position.x + 1)
            Direction.SOUTH -> onPlayerPositionChanged(y = party.position.y + 1)
            Direction.WEST -> onPlayerPositionChanged(x = party.position.x - 1)
        }
    }

    private fun onMoveForward() {
        when (party.facing) {
            Direction.NORTH -> {
                val newY = party.position.y - 1
                onPlayerPositionChanged(y = newY)
            }

            Direction.EAST -> {
                val newX = party.position.x + 1
                onPlayerPositionChanged(x = newX)
            }

            Direction.SOUTH -> {
                val newY = party.position.y + 1
                onPlayerPositionChanged(y = newY)
            }

            Direction.WEST -> {
                val newX = party.position.x - 1
                onPlayerPositionChanged(x = newX)
            }
        }
    }

    private fun onMoveBackwards() {
        when (party.facing) {
            Direction.NORTH -> {
                val newY = party.position.y + 1
                onPlayerPositionChanged(y = newY)
            }

            Direction.EAST -> {
                val newX = party.position.x - 1
                onPlayerPositionChanged(x = newX)
            }

            Direction.SOUTH -> {
                val newY = party.position.y - 1
                onPlayerPositionChanged(y = newY)
            }

            Direction.WEST -> {
                val newX = party.position.x + 1
                onPlayerPositionChanged(x = newX)
            }
        }
    }

    private fun onRotateRight() {
        val currentDirection = party.facing
        val directions = Direction.entries
        val currentIndex = directions.indexOf(currentDirection)
        val newIndex = (currentIndex + 1) % directions.size
        onDirectionChanged(directions[newIndex])
    }

    private fun onRotateLeft() {
        val currentDirection = party.facing
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
        /**
         * Set while the script is reading a reply back to the player. The
         * script is still inside the branch [answeredWith] chose, so clicking
         * "ok" has to hand it that same answer rather than the first button's.
         */
        val answeredWith: DialogAnswer? = null,
        /**
         * What the speaker has not said yet. While there is more, the button
         * turns the page instead of letting the script carry on.
         */
        val unread: List<String> = emptyList(),
        /** The real buttons, kept aside until the last page is on screen. */
        val buttons: List<String> = emptyList(),
        /** True while the speech is being read rather than answered. */
        val waitsToBeRead: Boolean = false,
    )

    data class State(
        val inf: Inf? = null,
        val dialog: DialogPrompt? = null,

        val viewPort: ImageBitmap? = null,

        /**
         * The world, as the scripts see it. There is one, and it is this: a
         * script is handed it and hands back what it changed.
         */
        val game: GameState = GameState(
            party = PartyState(
                position = Location(DEFAULT_PLAYER_X, DEFAULT_PLAYER_Y),
                facing = DEFAULT_DIRECTION,
            )
        ),
    )

    companion object {
        private const val TAG = "ViewConeDebugViewModel"
        private const val PLAY_FIELD_CPS = "PLAYFLD.CPS"
        private const val DECORATIONS_CPS = "DECORATE.CPS"
        private const val DIALOGUE_FRAME_CPS = "BORDER.CPS"
        private const val DIALOGUE_FONT = "FONT6.FNT"
        // side areas are not reachable yet, so only the main floor is played
        private const val PLAYED_SUBLEVEL = 0
        private const val DEFAULT_LEVEL = "LEVEL5.INF"
        private const val DEFAULT_PLAYER_X = 14
        private const val DEFAULT_PLAYER_Y = 9
        private val DEFAULT_DIRECTION = Direction.WEST
    }
}
