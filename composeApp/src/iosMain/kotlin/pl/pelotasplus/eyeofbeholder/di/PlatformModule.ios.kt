package pl.pelotasplus.eyeofbeholder.di

import org.koin.core.module.Module
import org.koin.dsl.module
import pl.pelotasplus.eyeofbeholder.data.repository.AudioSink
import pl.pelotasplus.eyeofbeholder.data.repository.AvAudioSink
import pl.pelotasplus.eyeofbeholder.data.repository.DocumentsSaveStore
import pl.pelotasplus.eyeofbeholder.data.repository.SaveStore

actual fun platformModule(): Module = module {
    single<SaveStore> { DocumentsSaveStore() }
    single<AudioSink> { AvAudioSink() }
}
