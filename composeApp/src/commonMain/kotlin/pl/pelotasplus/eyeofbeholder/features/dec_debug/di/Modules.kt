package pl.pelotasplus.eyeofbeholder.features.dec_debug.di

import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import pl.pelotasplus.eyeofbeholder.features.dec_debug.DecDebugViewModel

val sharedFeaturesDecDebugModule = module {
    viewModelOf(::DecDebugViewModel)
}
