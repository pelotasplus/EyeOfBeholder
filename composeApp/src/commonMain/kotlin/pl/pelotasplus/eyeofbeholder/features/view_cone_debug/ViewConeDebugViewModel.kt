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
import pl.pelotasplus.eyeofbeholder.data.repository.VmpRepository

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
            is Event.PlayerXChanged -> onPlayerXChanged(event.x)
            is Event.PlayerYChanged -> onPlayerYChanged(event.y)
            is Event.DirectionChanged -> onDirectionChanged(event.direction)
            Event.PlayerMoveNorth -> onPlayerMoveNorth()
            Event.PlayerMoveSouth -> onPlayerMoveSouth()
            Event.RotateEast -> onRotateEast()
            Event.RotateWest -> onRotateWest()
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

                    onVmpSelected("LEVEL4.MAZ")
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

    private fun onPlayerXChanged(x: Int) {
        _state.update { it.copy(playerX = x) }
        // Reload viewport with new position
        _state.value.selectedMazName?.let { mazName ->
            onVmpSelected(mazName)
        }
    }

    private fun onPlayerYChanged(y: Int) {
        _state.update { it.copy(playerY = y) }
        // Reload viewport with new position
        _state.value.selectedMazName?.let { mazName ->
            onVmpSelected(mazName)
        }
    }

    private fun onDirectionChanged(direction: Direction) {
        _state.update { it.copy(direction = direction) }
        // Reload viewport with new direction
        _state.value.selectedMazName?.let { mazName ->
            onVmpSelected(mazName)
        }
    }

    private fun onPlayerMoveNorth() {
        val newY = _state.value.playerY - 1
        if (newY >= 0) {
            onPlayerYChanged(newY)
        }
    }

    private fun onPlayerMoveSouth() {
        val newY = _state.value.playerY + 1
        // Allow any positive value, maze will handle bounds checking
        onPlayerYChanged(newY)
    }

    private fun onRotateEast() {
        val currentDirection = _state.value.direction
        val directions = Direction.entries
        val currentIndex = directions.indexOf(currentDirection)
        val newIndex = (currentIndex + 1) % directions.size
        onDirectionChanged(directions[newIndex])
    }

    private fun onRotateWest() {
        val currentDirection = _state.value.direction
        val directions = Direction.entries
        val currentIndex = directions.indexOf(currentDirection)
        val newIndex = (currentIndex - 1 + directions.size) % directions.size
        onDirectionChanged(directions[newIndex])
    }

    sealed class Event {
        data object Initialize : Event()
        data class OnVmpSelected(val name: String) : Event()
        data class PlayerXChanged(val x: Int) : Event()
        data class PlayerYChanged(val y: Int) : Event()
        data class DirectionChanged(val direction: Direction) : Event()
        data object PlayerMoveNorth : Event()
        data object PlayerMoveSouth : Event()
        data object RotateEast : Event()
        data object RotateWest : Event()
    }

    data class State(
        val isLoading: Boolean = true,
        val allVmps: ImmutableList<String> = persistentListOf(),
        val selectedMazName: String? = null,
        val selectedVmp: Vmp? = null,
        val selectedTiles: ViewPort? = null,
        val playerX: Int = 11,
        val playerY: Int = 15,
        val direction: Direction = Direction.NORTH
    )
}
