package pl.pelotasplus.eyeofbeholder.di

import org.koin.core.module.Module
import org.koin.dsl.module
import pl.pelotasplus.eyeofbeholder.data.repository.FileSaveStore
import pl.pelotasplus.eyeofbeholder.data.repository.SaveStore
import java.io.File

actual fun platformModule(): Module = module {
    single<SaveStore> { FileSaveStore(File(saveDirectory())) }
}

/** Where the desktop keeps its saves, following whatever each system expects. */
private fun saveDirectory(): String {
    val home = System.getProperty("user.home")
    val os = System.getProperty("os.name").orEmpty().lowercase()

    return when {
        os.contains("mac") -> "$home/Library/Application Support/EyeOfBeholder/saves"
        os.contains("win") ->
            "${System.getenv("APPDATA") ?: "$home/AppData/Roaming"}/EyeOfBeholder/saves"

        else -> "${System.getenv("XDG_DATA_HOME") ?: "$home/.local/share"}/EyeOfBeholder/saves"
    }
}
