package pl.pelotasplus.eyeofbeholder.features.maz_debug

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
import pl.pelotasplus.eyeofbeholder.data.model.Maz
import pl.pelotasplus.eyeofbeholder.data.repository.MazRepository
import pl.pelotasplus.eyeofbeholder.data.repository.ResourceRepository

@Stable
class MazDebugViewModel(
    private val resourceRepository: ResourceRepository,
    private val mazRepository: MazRepository
) : ViewModel() {

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    init {
        onEvent(Event.Initialize)
    }

    fun onEvent(event: Event) {
        when (event) {
            Event.Initialize -> onInitialize()
            is Event.OnMazSelected -> onMazSelected(event.name)
        }
    }

    private fun onInitialize() {
        viewModelScope.launch {
            resourceRepository.listResources(".MAZ")
                .onSuccess { mazNames ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            allMazs = mazNames.toImmutableList()
                        )
                    }
                }
                .onFailure {
                    Logger.e(it) { "Error while loading maz names" }
                }
        }
    }

    private fun onMazSelected(name: String) {
        viewModelScope.launch {
            mazRepository.loadMaz(name)
                .onSuccess { maz ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            loadedMaz = maz
                        )
                    }
                }.onFailure {
                    Logger.e(it) { "Error while loading maz: $name" }
                }
        }
    }

    sealed class Event {
        data object Initialize : Event()
        data class OnMazSelected(val name: String) : Event()
    }

    data class State(
        val isLoading: Boolean = true,
        val allMazs: ImmutableList<String> = persistentListOf(),
        val loadedMaz: Maz? = null
    )
}
