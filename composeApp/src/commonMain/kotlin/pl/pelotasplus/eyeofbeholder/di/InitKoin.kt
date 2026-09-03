package pl.pelotasplus.eyeofbeholder.di

import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration
import pl.pelotasplus.eyeofbeholder.data.di.sharedDataModule
import pl.pelotasplus.eyeofbeholder.features.cps_debug.di.sharedFeaturesCpsDebugModule
import pl.pelotasplus.eyeofbeholder.features.levels_debug.di.sharedFeaturesLevelsDebugModule
import pl.pelotasplus.eyeofbeholder.features.view_cone_debug.di.sharedFeaturesViewConeDebugModule

fun initKoin(config: KoinAppDeclaration? = null) {
    startKoin {
        config?.invoke(this)
        modules(
            platformModule(),
            sharedDataModule,
            sharedFeaturesCpsDebugModule,
            sharedFeaturesLevelsDebugModule,
            sharedFeaturesViewConeDebugModule,
        )
    }
}
