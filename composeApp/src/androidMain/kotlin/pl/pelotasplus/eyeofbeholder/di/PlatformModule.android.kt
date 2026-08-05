package pl.pelotasplus.eyeofbeholder.di

import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module
import pl.pelotasplus.eyeofbeholder.data.repository.AudioSink
import pl.pelotasplus.eyeofbeholder.data.repository.AudioTrackAudioSink
import pl.pelotasplus.eyeofbeholder.data.repository.FileSaveStore
import pl.pelotasplus.eyeofbeholder.data.repository.SaveStore
import java.io.File

actual fun platformModule(): Module = module {
    single<SaveStore> { FileSaveStore(File(androidContext().filesDir, "saves")) }
    single<AudioSink> { AudioTrackAudioSink() }
}
