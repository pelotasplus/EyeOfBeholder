package pl.pelotasplus.eyeofbeholder.features.inf_debug

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
import pl.pelotasplus.eyeofbeholder.data.model.Inf
import pl.pelotasplus.eyeofbeholder.data.repository.InfRepository

@Stable
class InfDebugViewModel(
    private val infRepository: InfRepository
) : ViewModel() {

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    init {
        onEvent(Event.Initialize)
    }

    fun onEvent(event: Event) {
        when (event) {
            Event.Initialize -> onInitialize()
            is Event.OnInfSelected -> onInfSelected(event.name)
        }
    }

    private fun onInitialize() {
        viewModelScope.launch {
            infRepository.getAllInfNames()
                .onSuccess { infNames ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            allInfs = infNames.toImmutableList()
                        )
                    }
                }
                .onFailure {
                    Logger.e(it) { "Error while loading inf names" }
                }
        }
    }

    private fun onInfSelected(name: String) {
        viewModelScope.launch {
            infRepository.loadInf(name)
                .onSuccess { inf ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            loadedInf = inf
                        )
                    }
                }
                .onFailure { exception ->
                    Logger.e(exception) { "Error while loading inf: $name" }
                    _state.update {
                        it.copy(
                            isLoading = false,
                        )
                    }
                }
        }
    }

    sealed class Event {
        data object Initialize : Event()
        data class OnInfSelected(val name: String) : Event()
    }

    data class State(
        val isLoading: Boolean = true,
        val loadedInf: Inf? = null,
        val allInfs: ImmutableList<String> = persistentListOf()
    )
}
