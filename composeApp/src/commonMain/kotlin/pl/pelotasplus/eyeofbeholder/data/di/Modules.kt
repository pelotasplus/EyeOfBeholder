package pl.pelotasplus.eyeofbeholder.data.di

import org.koin.dsl.module
import pl.pelotasplus.eyeofbeholder.data.repository.PalRepository
import pl.pelotasplus.eyeofbeholder.data.repository.PalRepositoryImpl

val sharedDataModule = module {
    factory<PalRepository> {
        PalRepositoryImpl()
    }
}
