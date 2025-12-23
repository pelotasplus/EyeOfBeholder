package pl.pelotasplus.eyeofbeholder

import android.app.Application
import org.koin.android.ext.koin.androidContext
import pl.pelotasplus.eyeofbeholder.di.initKoin

class EobApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        initKoin {
            androidContext(this@EobApplication)
        }
    }
}
