package pl.pelotasplus.eyeofbeholder.features.cps_debug

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
import pl.pelotasplus.eyeofbeholder.data.model.Cps
import pl.pelotasplus.eyeofbeholder.data.model.Palette
import pl.pelotasplus.eyeofbeholder.data.repository.CpsRepository
import pl.pelotasplus.eyeofbeholder.data.repository.PalRepository

@Stable
class CpsDebugViewModel(
    private val cpsRepository: CpsRepository,
    private val palRepository: PalRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    init {
        onEvent(Event.Initialize)
    }

    fun onEvent(event: Event) {
        when (event) {
            Event.Initialize -> onInitialize()
            is Event.OnCpsSelected -> onCpsSelected(event.name)
        }
    }

    private fun onInitialize() {
        viewModelScope.launch {
            cpsRepository.getAllCpsNames()
                .onSuccess { cpsNames ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            cpsNames = cpsNames.toImmutableList()
                        )
                    }
                }
                .onFailure {
                    Logger.e(it) { "Error while loading pal names" }
                }
        }

        viewModelScope.launch {
            palRepository.loadPal("AZURE.PAL")
                .onSuccess { pal ->
                    _state.update {
                        it.copy(
                            loadedPalette = pal,
                        )
                    }
                }
                .onFailure {
                    Logger.e(it) { "Error while loading PAL" }
                }
        }
    }

    private fun onCpsSelected(name: String) {
        viewModelScope.launch {
            cpsRepository.loadCps(name)
                .onSuccess { cps ->
                    palRepository.loadPal(name.replace(".CPS", ".PAL"))
                        .onSuccess { pal ->
                            _state.update {
                                it.copy(
                                    loadedPalette = pal
                                )
                            }
                        }

                    _state.update {
                        it.copy(
                            isLoading = false,
                            loadedCps = cps
                        )
                    }
                }
                .onFailure { exception ->
                    Logger.e(exception) { "Error while loading CPS: $name" }
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
        data class OnCpsSelected(val name: String) : Event()
    }

    data class State(
        val isLoading: Boolean = true,
        val loadedCps: Cps? = null,
        val loadedPalette: Palette? = null,
        val cpsNames: ImmutableList<String> = persistentListOf()
    )
}
