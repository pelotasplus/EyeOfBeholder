package pl.pelotasplus.eyeofbeholder.data.di

import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.koin.dsl.module
import pl.pelotasplus.eyeofbeholder.data.repository.CpsRepository
import pl.pelotasplus.eyeofbeholder.data.repository.CpsRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.DialogueTextRepository
import pl.pelotasplus.eyeofbeholder.data.repository.DialogueTextRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.FontRepository
import pl.pelotasplus.eyeofbeholder.data.repository.FontRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.OriginalSaveRepository
import pl.pelotasplus.eyeofbeholder.data.repository.OriginalSaveRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.SavedGameRepository
import pl.pelotasplus.eyeofbeholder.data.repository.SavedGameRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.DecRepository
import pl.pelotasplus.eyeofbeholder.data.repository.DcrRepository
import pl.pelotasplus.eyeofbeholder.data.repository.DcrRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.DecRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.InfRepository
import pl.pelotasplus.eyeofbeholder.data.repository.InfRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ItemsRepository
import pl.pelotasplus.eyeofbeholder.data.repository.ItemsRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.MazRepository
import pl.pelotasplus.eyeofbeholder.data.repository.MazRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.PalRepository
import pl.pelotasplus.eyeofbeholder.data.repository.PalRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ResourceRepository
import pl.pelotasplus.eyeofbeholder.data.repository.ResourceRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.VcnRepository
import pl.pelotasplus.eyeofbeholder.data.repository.VcnRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ViewConeRepository
import pl.pelotasplus.eyeofbeholder.data.repository.ViewConeRepositoryImpl
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
    factory<DialogueTextRepository> {
        DialogueTextRepositoryImpl(get())
    }
    factory<FontRepository> {
        FontRepositoryImpl(get())
    }
    factory<OriginalSaveRepository> {
        OriginalSaveRepositoryImpl(get())
    }
    factory<SavedGameRepository> {
        SavedGameRepositoryImpl(get())
    }

    factory<CpsRepository> {
        CpsRepositoryImpl(get())
    }
    factory<ItemsRepository> {
        ItemsRepositoryImpl(get())
    }
    factory<DecRepository> {
        DecRepositoryImpl(get())
    }
    factory<DcrRepository> {
        DcrRepositoryImpl(get())
    }
    factory<InfRepository> {
        InfRepositoryImpl(get(), get(), get(), get(), get(), get(), get())
    }
    factory<MazRepository> {
        MazRepositoryImpl(get())
    }
    factory<VcnRepository> {
        VcnRepositoryImpl(get())
    }
    factory<VmpRepository> {
        VmpRepositoryImpl(get())
    }
    factory<ViewConeRepository> {
        ViewConeRepositoryImpl(get(), get(), get(), get())
    }
}
