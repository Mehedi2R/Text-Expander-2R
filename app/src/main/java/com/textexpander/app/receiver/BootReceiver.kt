package com.textexpander.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.textexpander.app.OnboardingActivity
import com.textexpander.app.data.SettingsPreferences
import com.textexpander.app.service.TextExpanderForegroundService
import com.textexpander.app.worker.KeepAliveWorker

/**
 * BroadcastReceiver triggered after device boot, restart, or app update.
 * Automatically recovers and resurrects the foreground service and schedules
 * the WorkManager periodic watchdog without requiring aggressive wake locks.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        try {
            val action = intent?.action ?: return
            if (action == Intent.ACTION_BOOT_COMPLETED ||
                action == "android.intent.action.QUICKBOOT_POWERON" ||
                action == "com.htc.intent.action.QUICKBOOT_POWERON" ||
                action == Intent.ACTION_MY_PACKAGE_REPLACED
            ) {
                // Schedule the periodic watchdog
                KeepAliveWorker.schedule(context)

                // Start companion foreground service if enabled
                val settings = SettingsPreferences.get(context)
                if (settings.enableForegroundService && OnboardingActivity.isAccessibilityAllowed(context)) {
                    TextExpanderForegroundService.startService(context)
                }
            }
        } catch (_: Throwable) {}
    }
}
