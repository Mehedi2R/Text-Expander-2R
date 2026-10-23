package com.textexpander.app

import android.app.Application
import com.textexpander.app.data.AppDatabase

class TextExpanderApp : Application() {

    val database: AppDatabase by lazy {
        AppDatabase.getInstance(this)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Schedule periodic WorkManager watchdog safely
        try {
            com.textexpander.app.worker.KeepAliveWorker.schedule(this)
        } catch (_: Throwable) {}
    }

    companion object {
        lateinit var instance: TextExpanderApp
            private set
    }
}
