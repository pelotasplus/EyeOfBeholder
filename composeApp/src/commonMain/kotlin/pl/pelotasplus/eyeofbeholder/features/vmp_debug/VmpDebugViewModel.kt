package pl.pelotasplus.eyeofbeholder.features.vmp_debug

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
import pl.pelotasplus.eyeofbeholder.data.model.ViewPort
import pl.pelotasplus.eyeofbeholder.data.model.Vmp
import pl.pelotasplus.eyeofbeholder.data.repository.ResourceRepository
import pl.pelotasplus.eyeofbeholder.data.repository.VmpRepository

@Stable
class VmpDebugViewModel(
    private val resourceRepository: ResourceRepository,
    private val vmpRepository: VmpRepository
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
        }
    }

    private fun onInitialize() {
        viewModelScope.launch {
            resourceRepository.listResources(".VMP")
                .onSuccess { vmpNames ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            allVmps = vmpNames.toImmutableList()
                        )
                    }
                }
                .onFailure {
                    Logger.e(it) { "Error while loading vmp names" }
                }
        }
    }

    private fun onVmpSelected(name: String) {
        viewModelScope.launch {
            vmpRepository.loadVmp(name)
                .onSuccess { vmp ->
                    _state.update {
                        it.copy(
                            isLoading = false,
//                            selectedVmp = vmp
                            selectedTiles = vmp
                        )
                    }
                }
                .onFailure {
                    Logger.e(it) { "Error while loading vmp: $name" }
                }

        }
    }

    sealed class Event {
        data object Initialize : Event()
        data class OnVmpSelected(val name: String) : Event()
    }

    data class State(
        val isLoading: Boolean = true,
        val allVmps: ImmutableList<String> = persistentListOf(),
        val selectedVmp: Vmp? = null,
        val selectedTiles: ViewPort? = null
    )
}
