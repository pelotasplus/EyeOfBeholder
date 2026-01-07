package pl.pelotasplus.eyeofbeholder.features.dec_debug

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
import pl.pelotasplus.eyeofbeholder.data.model.Dec
import pl.pelotasplus.eyeofbeholder.data.repository.DecRepository

@Stable
class DecDebugViewModel(
    private val decRepository: DecRepository
) : ViewModel() {

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    init {
        onEvent(Event.Initialize)
    }

    fun onEvent(event: Event) {
        when (event) {
            Event.Initialize -> onInitialize()
            is Event.OnDecSelected -> onDecSelected(event.name)
        }
    }

    private fun onInitialize() {
        viewModelScope.launch {
            decRepository.getAllDecNames()
                .onSuccess { decNames ->
                    _state.update {
                        it.copy(
                            allDecs = decNames.toImmutableList()
                        )
                    }
                }
                .onFailure {
                    Logger.e(it) { "Error while loading dec names" }
                }
        }
    }

    private fun onDecSelected(name: String) {
        viewModelScope.launch {
            decRepository.loadDec(name)
                .onSuccess { dec ->
                    _state.update {
                        it.copy(
                            loadedDec = dec
                        )
                    }
                }
                .onFailure { exception ->
                    Logger.e(exception) { "Error while loading dec: $name" }
                }
        }
    }

    sealed class Event {
        data object Initialize : Event()
        data class OnDecSelected(val name: String) : Event()
    }

    data class State(
        val loadedDec: Dec? = null,
        val allDecs: ImmutableList<String> = persistentListOf()
    )
}
