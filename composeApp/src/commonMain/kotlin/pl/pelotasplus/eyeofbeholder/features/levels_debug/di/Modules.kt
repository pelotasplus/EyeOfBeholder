package pl.pelotasplus.eyeofbeholder.features.levels_debug.di

import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import pl.pelotasplus.eyeofbeholder.features.levels_debug.LevelsDebugViewModel

val sharedFeaturesLevelsDebugModule = module {
    viewModelOf(::LevelsDebugViewModel)
}
