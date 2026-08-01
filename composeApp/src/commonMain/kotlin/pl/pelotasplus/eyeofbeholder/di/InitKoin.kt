package pl.pelotasplus.eyeofbeholder.di

import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration
import pl.pelotasplus.eyeofbeholder.data.di.sharedDataModule
import pl.pelotasplus.eyeofbeholder.features.cps_debug.di.sharedFeaturesCpsDebugModule
import pl.pelotasplus.eyeofbeholder.features.dec_debug.di.sharedFeaturesDecDebugModule
import pl.pelotasplus.eyeofbeholder.features.inf_debug.di.sharedFeaturesInfDebugModule
import pl.pelotasplus.eyeofbeholder.features.levels_debug.di.sharedFeaturesLevelsDebugModule
import pl.pelotasplus.eyeofbeholder.features.maz_debug.di.sharedFeaturesMazDebugModule
import pl.pelotasplus.eyeofbeholder.features.pal_debug.di.sharedFeaturesPalDebugModule
import pl.pelotasplus.eyeofbeholder.features.vcn_debug.di.sharedFeaturesVcnDebugModule
import pl.pelotasplus.eyeofbeholder.features.view_cone_debug.di.sharedFeaturesViewConeDebugModule

fun initKoin(config: KoinAppDeclaration? = null) {
    startKoin {
        config?.invoke(this)
        modules(
            sharedDataModule,
            sharedFeaturesPalDebugModule,
            sharedFeaturesCpsDebugModule,
            sharedFeaturesDecDebugModule,
            sharedFeaturesInfDebugModule,
            sharedFeaturesLevelsDebugModule,
            sharedFeaturesMazDebugModule,
            sharedFeaturesVcnDebugModule,
            sharedFeaturesViewConeDebugModule,
        )
    }
}
