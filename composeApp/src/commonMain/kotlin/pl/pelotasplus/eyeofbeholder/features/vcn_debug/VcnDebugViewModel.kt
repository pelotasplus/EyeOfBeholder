package pl.pelotasplus.eyeofbeholder.features.vcn_debug

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
class VcnDebugViewModel(
    private val resourceRepository: ResourceRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    init {
        onEvent(Event.Initialize)
    }

    fun onEvent(event: Event) {
        when (event) {
            Event.Initialize -> onInitialize()
            is Event.OnVcnSelected -> onVcnSelected(event.name)
        }
    }

    private fun onInitialize() {
        viewModelScope.launch {
            resourceRepository.listResources(".VCN")
                .onSuccess { vcnNames ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            allVcns = vcnNames.toImmutableList()
                        )
                    }
                }
                .onFailure {
                    Logger.e(it) { "Error while loading vcn names" }
                }
        }
    }

    private fun onVcnSelected(name: String) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isLoading = false,
                    selectedVcn = name
                )
            }
        }
    }

    sealed class Event {
        data object Initialize : Event()
        data class OnVcnSelected(val name: String) : Event()
    }

    data class State(
        val isLoading: Boolean = true,
        val allVcns: ImmutableList<String> = persistentListOf(),
        val selectedVcn: String? = null
    )
}
