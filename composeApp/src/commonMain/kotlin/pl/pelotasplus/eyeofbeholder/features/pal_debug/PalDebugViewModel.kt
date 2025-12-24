package pl.pelotasplus.eyeofbeholder.features.pal_debug

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
import pl.pelotasplus.eyeofbeholder.data.model.Pal
import pl.pelotasplus.eyeofbeholder.data.repository.PalRepository

@Stable
class PalDebugViewModel(
    private val palRepository: PalRepository
) : ViewModel() {

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    init {
        onEvent(Event.Initialize)
    }

    fun onEvent(event: Event) {
        when (event) {
            Event.Initialize -> onInitialize()
            is Event.OnPalSelected -> onPalSelected(event.name)
        }
    }

    private fun onInitialize() {
        viewModelScope.launch {
            palRepository.getAllPalNames()
                .onSuccess { palNames ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            allPals = palNames.toImmutableList()
                        )
                    }
                }
                .onFailure {
                    Logger.e(it) { "Error while loading pal names" }
                }
        }
    }

    private fun onPalSelected(name: String) {
        viewModelScope.launch {
            palRepository.loadPal(name)
                .onSuccess { pal ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            loadedPal = pal
                        )
                    }
                }
                .onFailure { exception ->
                    Logger.e(exception) { "Error while loading pal: $name" }
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
        data class OnPalSelected(val name: String) : Event()
    }

    data class State(
        val isLoading: Boolean = true,
        val loadedPal: Pal? = null,
        val allPals: ImmutableList<String> = persistentListOf()
    )
}
