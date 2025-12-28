package pl.pelotasplus.eyeofbeholder.data.di

import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.koin.dsl.module
import pl.pelotasplus.eyeofbeholder.data.repository.CpsRepository
import pl.pelotasplus.eyeofbeholder.data.repository.CpsRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.InfRepository
import pl.pelotasplus.eyeofbeholder.data.repository.InfRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.MazRepository
import pl.pelotasplus.eyeofbeholder.data.repository.MazRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.PalRepository
import pl.pelotasplus.eyeofbeholder.data.repository.PalRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ResourceRepository
import pl.pelotasplus.eyeofbeholder.data.repository.ResourceRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.VcnRepository
import pl.pelotasplus.eyeofbeholder.data.repository.VcnRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.VmpRepository
import pl.pelotasplus.eyeofbeholder.data.repository.VmpRepositoryImpl

@OptIn(ExperimentalResourceApi::class)
val sharedDataModule = module {
    factory<ResourceRepository> {
        ResourceRepositoryImpl()
    }
    factory<PalRepository> {
        PalRepositoryImpl(get())
    }
    factory<CpsRepository> {
        CpsRepositoryImpl(get())
    }
    factory<InfRepository> {
        InfRepositoryImpl(get())
    }
    factory<MazRepository> {
        MazRepositoryImpl(get())
    }
    factory<VcnRepository> {
        VcnRepositoryImpl(get(), get())
    }
    factory<VmpRepository> {
        VmpRepositoryImpl(get())
    }
}
