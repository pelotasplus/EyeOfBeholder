package pl.pelotasplus.eyeofbeholder.features.levels_debug

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
import pl.pelotasplus.eyeofbeholder.data.repository.ResourceRepository

@Stable
class LevelsDebugViewModel(
    private val resourceRepository: ResourceRepository
) : ViewModel() {

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    init {
        onEvent(Event.Initialize)
    }

    fun onEvent(event: Event) {
        when (event) {
            Event.Initialize -> onInitialize()
        }
    }

    private fun onInitialize() {
        viewModelScope.launch {
            resourceRepository.listResources(".INF")
                .onSuccess { levelNames ->
                    _state.update { it.copy(levels = levelNames.toImmutableList()) }
                }
                .onFailure {
                    Logger.e(it) { "Error while loading level names" }
                }
        }
    }

    sealed class Event {
        data object Initialize : Event()
    }

    data class State(
        val levels: ImmutableList<String> = persistentListOf()
    )
}
