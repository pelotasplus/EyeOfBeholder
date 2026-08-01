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
import pl.pelotasplus.eyeofbeholder.data.model.LevelEntryPoint
import pl.pelotasplus.eyeofbeholder.data.model.entryPoints
import pl.pelotasplus.eyeofbeholder.data.model.levelFileName
import pl.pelotasplus.eyeofbeholder.data.model.levelNumber
import pl.pelotasplus.eyeofbeholder.data.repository.InfRepository
import pl.pelotasplus.eyeofbeholder.data.repository.ResourceRepository

@Stable
class LevelsDebugViewModel(
    private val resourceRepository: ResourceRepository,
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
        }
    }

    private fun onInitialize() {
        viewModelScope.launch {
            val names = resourceRepository.listResources(".INF")
                .onFailure { Logger.e(it) { "Error while loading level names" } }
                .getOrElse { return@launch }

            // the names alone are worth showing while the scripts are read
            _state.update { state ->
                state.copy(levels = names.map { Level(name = it) }.toImmutableList())
            }

            val byLevel = names
                .flatMap { name ->
                    infRepository.loadScript(name)
                        .onFailure { Logger.e(it) { "Error while loading script of $name" } }
                        .getOrDefault(emptyList())
                        .entryPoints(fromLevel = levelNumber(name))
                }
                .groupBy { levelFileName(it.level) }

            _state.update { state ->
                state.copy(
                    levels = state.levels.map { level ->
                        level.copy(
                            entryPoints = byLevel[level.name].orEmpty()
                                .sortedWith(compareBy({ it.fromLevel }, { it.location.y }, { it.location.x }))
                                .toImmutableList()
                        )
                    }.toImmutableList()
                )
            }
        }
    }

    sealed class Event {
        data object Initialize : Event()
    }

    data class Level(
        val name: String,
        val entryPoints: ImmutableList<LevelEntryPoint> = persistentListOf()
    )

    data class State(
        val levels: ImmutableList<Level> = persistentListOf()
    )
}
