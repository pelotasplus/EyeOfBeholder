package pl.pelotasplus.eyeofbeholder.features.view_cone_debug.di

import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import pl.pelotasplus.eyeofbeholder.features.view_cone_debug.ViewConeDebugViewModel

val sharedFeaturesViewConeDebugModule = module {
    viewModelOf(::ViewConeDebugViewModel)
}
