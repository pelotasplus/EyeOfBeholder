package pl.pelotasplus.eyeofbeholder.features.vcn_debug.di

import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import pl.pelotasplus.eyeofbeholder.features.vcn_debug.VcnDebugViewModel

val sharedFeaturesVcnDebugModule = module {
    viewModelOf(::VcnDebugViewModel)
}
