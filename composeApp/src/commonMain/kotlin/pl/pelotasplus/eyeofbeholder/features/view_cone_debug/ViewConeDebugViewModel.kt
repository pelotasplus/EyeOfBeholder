package pl.pelotasplus.eyeofbeholder.features.view_cone_debug

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.Inf
import pl.pelotasplus.eyeofbeholder.data.model.ViewPort
import pl.pelotasplus.eyeofbeholder.data.repository.ResourceRepository
import pl.pelotasplus.eyeofbeholder.data.repository.ViewConeRepository

@Stable
class ViewConeDebugViewModel(
    private val resourceRepository: ResourceRepository,
    private val viewConeRepository: ViewConeRepository
) : ViewModel() {

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    init {
        onEvent(Event.Initialize)
    }

    fun onEvent(event: Event) {
        when (event) {
            Event.Initialize -> onInitialize()
            is Event.OnLevelSelected -> onVmpSelected(event.name)
            Event.MoveForward -> onMoveForward()
            Event.MoveBackwards -> onMoveBackwards()
            Event.RotateRight -> onRotateRight()
            Event.RotateLeft -> onRotateLeft()
            Event.GoBack -> onGoBack()
        }
    }

    private fun onGoBack() {
        _state.update {
            it.copy(viewPort = null)
        }
    }

    private fun onInitialize() {
        viewModelScope.launch {
            resourceRepository.listResources(".INF")
                .onSuccess { levelNames ->
                    _state.update {
                        it.copy(
                            levels = levelNames.toImmutableList()
                        )
                    }

                    // enter to the temple
//                    onVmpSelected(
//                        "LEVEL5.INF",
//                        playerX = 14,
//                        playerY = 9,
//                        direction = Direction.WEST
//                    )

                    // temple level 2
                    // https://gamerwalkthroughs.com/eye-of-the-beholder-2/temple-level-2/
                    onVmpSelected(
                        "LEVEL6.INF",
                        playerX = 20, //10,
                        playerY = 5, //3,
                        direction = Direction.EAST, // Direction.NORTH
                    )

                    // entrance to the temple, stairs down
//                    onVmpSelected(
//                        "LEVEL1.INF",
//                        playerX = 10,
//                        playerY = 12,
//                        direction = Direction.SOUTH
//                    )
                }
                .onFailure {
                    Logger.e(it) { "Error while loading level names" }
                }
        }
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
                sublevel = _state.value.inf!!.subLevels[0],
                playerX = _state.value.playerX,
                playerY = _state.value.playerY,
                direction = _state.value.direction
            ).onSuccess { viewPort ->
                _state.update { it.copy(viewPort = viewPort) }
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
        data object Initialize : Event()
        data class OnLevelSelected(val name: String) : Event()
        data object MoveForward : Event()
        data object MoveBackwards : Event()
        data object RotateRight : Event()
        data object RotateLeft : Event()
        data object GoBack : Event()
    }

    data class State(
        val inf: Inf? = null,

        val levels: ImmutableList<String> = persistentListOf(),

        val viewPort: ViewPort? = null,

        val playerX: Int = 14,
        val playerY: Int = 9,
        val direction: Direction = Direction.WEST
    )
}
