package pl.pelotasplus.eyeofbeholder.features.cps_debug.di

import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import pl.pelotasplus.eyeofbeholder.features.cps_debug.CpsDebugViewModel

val sharedFeaturesCpsDebugModule = module {
    viewModelOf(::CpsDebugViewModel)
}
