package app.utillock.android

import android.app.Application
import app.utillock.android.data.AppContainer

class UtilLockApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

