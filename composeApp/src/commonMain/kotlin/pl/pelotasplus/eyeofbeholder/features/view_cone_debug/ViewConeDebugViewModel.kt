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
import pl.pelotasplus.eyeofbeholder.data.model.ViewPort
import pl.pelotasplus.eyeofbeholder.data.model.Vmp
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
            is Event.OnVmpSelected -> onVmpSelected(event.name)
            Event.MoveForward -> onMoveForward()
            Event.MoveBackwards -> onMoveBackwards()
            Event.RotateRight -> onRotateRight()
            Event.RotateLeft -> onRotateLeft()
        }
    }

    private fun onInitialize() {
        viewModelScope.launch {
            resourceRepository.listResources(".MAZ")
                .onSuccess { vmpNames ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            allVmps = vmpNames.toImmutableList()
                        )
                    }

                    onVmpSelected("LEVEL5.MAZ")
                }
                .onFailure {
                    Logger.e(it) { "Error while loading vmp names" }
                }
        }
    }

    private fun onVmpSelected(name: String) {
        viewModelScope.launch {
            val currentState = _state.value
            viewConeRepository.loadVmp(
                name = name,
                playerX = currentState.playerX,
                playerY = currentState.playerY,
                direction = currentState.direction
            )
                .onSuccess { vmp ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            selectedMazName = name,
                            selectedTiles = vmp
                        )
                    }
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
        _state.value.selectedMazName
            ?.let { mazName -> onVmpSelected(mazName) }
    }

    private fun onDirectionChanged(direction: Direction) {
        _state.update { it.copy(direction = direction) }
        _state.value.selectedMazName?.let { mazName -> onVmpSelected(mazName) }
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
        data class OnVmpSelected(val name: String) : Event()
        data object MoveForward : Event()
        data object MoveBackwards : Event()
        data object RotateRight : Event()
        data object RotateLeft : Event()
    }

    data class State(
        val isLoading: Boolean = true,
        val allVmps: ImmutableList<String> = persistentListOf(),
        val selectedMazName: String? = null,
        val selectedVmp: Vmp? = null,
        val selectedTiles: ViewPort? = null,
        val playerX: Int = 23,
        val playerY: Int = 25,
        val direction: Direction = Direction.NORTH
    )
}
