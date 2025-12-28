package pl.pelotasplus.eyeofbeholder.features.vmp_debug.di

import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import pl.pelotasplus.eyeofbeholder.features.vmp_debug.VmpDebugViewModel

val sharedFeaturesVmpDebugModule = module {
    viewModelOf(::VmpDebugViewModel)
}
