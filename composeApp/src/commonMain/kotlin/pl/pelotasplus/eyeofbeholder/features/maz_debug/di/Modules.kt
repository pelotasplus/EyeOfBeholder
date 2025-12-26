package pl.pelotasplus.eyeofbeholder.features.maz_debug.di

import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import pl.pelotasplus.eyeofbeholder.features.maz_debug.MazDebugViewModel

val sharedFeaturesMazDebugModule = module {
    viewModelOf(::MazDebugViewModel)
}
