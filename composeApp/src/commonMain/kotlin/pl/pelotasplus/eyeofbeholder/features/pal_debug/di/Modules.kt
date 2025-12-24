package pl.pelotasplus.eyeofbeholder.features.pal_debug.di

import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import pl.pelotasplus.eyeofbeholder.features.pal_debug.PalDebugViewModel

val sharedFeaturesPalDebugModule = module {
    viewModelOf(::PalDebugViewModel)
}
