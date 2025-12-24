package pl.pelotasplus.eyeofbeholder.data.di

import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.koin.dsl.module
import pl.pelotasplus.eyeofbeholder.data.repository.PalRepository
import pl.pelotasplus.eyeofbeholder.data.repository.PalRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ResourceRepository
import pl.pelotasplus.eyeofbeholder.data.repository.ResourceRepositoryImpl

@OptIn(ExperimentalResourceApi::class)
val sharedDataModule = module {
    factory<ResourceRepository> {
        ResourceRepositoryImpl()
    }
    factory<PalRepository> {
        PalRepositoryImpl(get())
    }
}
