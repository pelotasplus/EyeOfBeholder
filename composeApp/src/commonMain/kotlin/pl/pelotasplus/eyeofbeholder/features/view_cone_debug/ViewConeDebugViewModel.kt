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
import pl.pelotasplus.eyeofbeholder.data.model.LevelScriptRunner
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.PlayField
import pl.pelotasplus.eyeofbeholder.data.model.ScriptEvent
import pl.pelotasplus.eyeofbeholder.data.model.ScriptOutcome
import pl.pelotasplus.eyeofbeholder.data.model.toImageBitmap
import pl.pelotasplus.eyeofbeholder.data.repository.CpsRepository
import pl.pelotasplus.eyeofbeholder.data.repository.ViewConeRepository

@Stable
class ViewConeDebugViewModel(
    private val viewConeRepository: ViewConeRepository,
    private val cpsRepository: CpsRepository,
) : ViewModel() {

    private var playFieldBackground: Cps? = null
    private var decorations: Cps? = null
    private var scriptRunner: LevelScriptRunner? = null

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    fun onEvent(event: Event) {
        when (event) {
            is Event.Initialize -> onInitialize(event.level)
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
     * default level at its hardcoded start position.
     */
    private fun onInitialize(level: String?) {
        viewModelScope.launch {
            cpsRepository.loadCps(PLAY_FIELD_CPS)
                .onSuccess { playFieldBackground = it }
                .onFailure { Logger.e(it) { "Error while loading $PLAY_FIELD_CPS" } }
            cpsRepository.loadCps(DECORATIONS_CPS)
                .onSuccess { decorations = it }
                .onFailure { Logger.e(it) { "Error while loading $DECORATIONS_CPS" } }
            renderViewPort()
        }

        if (level != null) {
            onVmpSelected(level)
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

        return when (val outcome = runner.onEvent(inf.triggers, at, ScriptEvent.PARTY_ENTERED)) {
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

            ScriptOutcome.Nothing -> false
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
                    PlayField(background, decorations, sublevel.palette)
                        .render(viewPort, _state.value.direction)
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
        data class Initialize(val level: String?) : Event()
        data class OnLevelSelected(val name: String) : Event()
        data object MoveForward : Event()
        data object MoveBackwards : Event()
        data object StrafeLeft : Event()
        data object StrafeRight : Event()
        data object RotateRight : Event()
        data object RotateLeft : Event()
    }

    data class State(
        val inf: Inf? = null,

        val viewPort: ImageBitmap? = null,

        val playerX: Int = DEFAULT_PLAYER_X,
        val playerY: Int = DEFAULT_PLAYER_Y,
        val direction: Direction = DEFAULT_DIRECTION
    )

    companion object {
        private const val TAG = "ViewConeDebugViewModel"
        private const val PLAY_FIELD_CPS = "PLAYFLD.CPS"
        private const val DECORATIONS_CPS = "DECORATE.CPS"
        /** Where the real game starts (DarkMoonEngine::startupNew). */
        private const val DEFAULT_LEVEL = "LEVEL4.INF"
        private const val DEFAULT_PLAYER_X = 11
        private const val DEFAULT_PLAYER_Y = 5
        private val DEFAULT_DIRECTION = Direction.SOUTH
    }
}
