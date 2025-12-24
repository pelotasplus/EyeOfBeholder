package pl.pelotasplus.eyeofbeholder.features.cps_debug.di

import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import pl.pelotasplus.eyeofbeholder.features.cps_debug.CpsDebugViewModel
import pl.pelotasplus.eyeofbeholder.features.pal_debug.PalDebugViewModel

val sharedFeaturesCpsDebugModule = module {
    viewModelOf(::CpsDebugViewModel)
}
