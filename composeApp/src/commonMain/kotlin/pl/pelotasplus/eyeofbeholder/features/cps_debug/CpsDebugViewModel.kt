package pl.pelotasplus.eyeofbeholder.features.cps_debug

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import pl.pelotasplus.eyeofbeholder.data.model.Pal
import pl.pelotasplus.eyeofbeholder.data.repository.PalRepository

class CpsDebugViewModel(
    palRepository: PalRepository
) : ViewModel() {

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    init {
        val ret = palRepository.loadPal("foobar")
        _state.update {
            it.copy(
                loadedPal = ret
            )
        }
        println("XXX ret $ret")
    }

    fun onEvent(event: Event) {
        when (event) {
            Event.NoOp -> TODO()
        }
    }

    sealed class Event {
        data object NoOp : Event()
    }

    data class State(
        val isLoading: Boolean = true,
        val loadedPal: Pal? = null
    )
}
