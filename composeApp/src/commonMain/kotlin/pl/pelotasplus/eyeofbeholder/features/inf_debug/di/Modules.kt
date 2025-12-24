package pl.pelotasplus.eyeofbeholder.features.inf_debug.di

import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import pl.pelotasplus.eyeofbeholder.features.inf_debug.InfDebugViewModel

val sharedFeaturesInfDebugModule = module {
    viewModelOf(::InfDebugViewModel)
}
