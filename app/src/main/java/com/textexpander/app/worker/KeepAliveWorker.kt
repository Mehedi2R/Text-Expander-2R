package com.textexpander.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.textexpander.app.data.SettingsPreferences
import com.textexpander.app.service.TextExpanderAccessibilityService
import com.textexpander.app.service.TextExpanderForegroundService
import java.util.concurrent.TimeUnit

/**
 * Periodic WorkManager watchdog that verifies service health and restarts
 * the companion Foreground Service if killed by the OS or battery managers.
 * Operates purely on standard Android job scheduling without holding wake locks.
 */
class KeepAliveWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "TextExpanderKeepAliveWatchdog"

        fun schedule(context: Context) {
            try {
                val workRequest = PeriodicWorkRequestBuilder<KeepAliveWorker>(
                    15, TimeUnit.MINUTES
                ).build()

                WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    workRequest
                )
            } catch (_: Throwable) {}
        }
    }

    override suspend fun doWork(): Result {
        val settings = SettingsPreferences.get(applicationContext)

        if (settings.enableForegroundService) {
            if (!TextExpanderForegroundService.isRunning) {
                TextExpanderForegroundService.startService(applicationContext)
            }
            TextExpanderAccessibilityService.reloadShortcuts()
        }

        return Result.success()
    }
}
