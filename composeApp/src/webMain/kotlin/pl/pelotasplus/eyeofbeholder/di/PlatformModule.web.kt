package pl.pelotasplus.eyeofbeholder.di

import org.koin.core.module.Module
import org.koin.dsl.module
import pl.pelotasplus.eyeofbeholder.data.repository.AudioSink
import pl.pelotasplus.eyeofbeholder.data.repository.LocalStorageSaveStore
import pl.pelotasplus.eyeofbeholder.data.repository.SaveStore
import pl.pelotasplus.eyeofbeholder.data.repository.WebAudioSink

actual fun platformModule(): Module = module {
    single<SaveStore> { LocalStorageSaveStore() }
    single<AudioSink> { WebAudioSink() }
}
