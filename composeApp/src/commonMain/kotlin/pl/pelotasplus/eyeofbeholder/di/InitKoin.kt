package pl.pelotasplus.eyeofbeholder.di

import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration
import pl.pelotasplus.eyeofbeholder.data.di.sharedDataModule
import pl.pelotasplus.eyeofbeholder.features.pal_debug.di.sharedFeaturesCpsDebugModule

fun initKoin(config: KoinAppDeclaration? = null) {
    startKoin {
        config?.invoke(this)
        modules(
            sharedDataModule,
            sharedFeaturesCpsDebugModule
        )
    }
}
