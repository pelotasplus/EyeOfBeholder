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
import pl.pelotasplus.eyeofbeholder.data.model.toImageBitmap
import pl.pelotasplus.eyeofbeholder.data.repository.ViewConeRepository

@Stable
class ViewConeDebugViewModel(
    private val viewConeRepository: ViewConeRepository
) : ViewModel() {

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    fun onEvent(event: Event) {
        when (event) {
            is Event.Initialize -> onInitialize(event.level)
            is Event.OnLevelSelected -> onVmpSelected(event.name)
            Event.MoveForward -> onMoveForward()
            Event.MoveBackwards -> onMoveBackwards()
            Event.RotateRight -> onRotateRight()
            Event.RotateLeft -> onRotateLeft()
        }
    }

    /**
     * [level] is the INF picked from the Levels screen, or null to open the
     * default level at its hardcoded start position.
     */
    private fun onInitialize(level: String?) {
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
        renderViewPort()
    }

    private fun onDirectionChanged(direction: Direction) {
        _state.update { it.copy(direction = direction) }
        renderViewPort()
    }

    private fun renderViewPort() {
        viewModelScope.launch {
            viewConeRepository.renderPosition(
                items = _state.value.inf!!.items,
                monsters = _state.value.inf!!.monsterInstances,
                sublevel = _state.value.inf!!.subLevels[0],
                playerX = _state.value.playerX,
                playerY = _state.value.playerY,
                direction = _state.value.direction
            ).onSuccess { viewPort ->
                _state.update { it.copy(viewPort = viewPort.toImageBitmap()) }
            }
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
        private const val DEFAULT_LEVEL = "LEVEL1.INF"
        private const val DEFAULT_PLAYER_X = 10
        private const val DEFAULT_PLAYER_Y = 12
        private val DEFAULT_DIRECTION = Direction.SOUTH
    }
}
