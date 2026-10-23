package com.textexpander.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.textexpander.app.MainActivity
import com.textexpander.app.OnboardingActivity
import com.textexpander.app.R
import com.textexpander.app.data.SettingsPreferences

/**
 * Lightweight, battery-friendly companion Foreground Service for Text Expander 2R.
 * Secures high process priority without aggressive wake locks, keeping shortcut
 * detection alive through deep sleep and system idle states.
 */
class TextExpanderForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "textexpander_service_channel"
        const val NOTIFICATION_ID = 2001
        const val ACTION_START = "com.textexpander.app.service.ACTION_START"
        const val ACTION_STOP = "com.textexpander.app.service.ACTION_STOP"

        @Volatile
        var isRunning: Boolean = false
            private set

        fun startService(context: Context) {
            try {
                val settings = SettingsPreferences.get(context)
                if (!settings.enableForegroundService) return
                if (!OnboardingActivity.isAccessibilityAllowed(context)) return

                val intent = Intent(context, TextExpanderForegroundService::class.java).apply {
                    action = ACTION_START
                }
                ContextCompat.startForegroundService(context, intent)
            } catch (_: Throwable) {}
        }

        fun stopService(context: Context) {
            try {
                val intent = Intent(context, TextExpanderForegroundService::class.java).apply {
                    action = ACTION_STOP
                }
                context.stopService(intent)
            } catch (_: Throwable) {}
        }
    }

    override fun onCreate() {
        super.onCreate()
        try {
            createNotificationChannel()
        } catch (_: Throwable) {}
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            isRunning = false
            try {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } catch (_: Throwable) {}
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            val settings = SettingsPreferences.get(this)
            if (!settings.enableForegroundService || !OnboardingActivity.isAccessibilityAllowed(this)) {
                isRunning = false
                try {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } catch (_: Throwable) {}
                stopSelf()
                return START_NOT_STICKY
            }

            val notification = buildForegroundNotification()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                try {
                    ServiceCompat.startForeground(
                        this,
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    )
                } catch (_: Throwable) {
                    startForeground(NOTIFICATION_ID, notification)
                }
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }

            isRunning = true

            // Ensure accessibility shortcuts are loaded in memory
            try {
                TextExpanderAccessibilityService.reloadShortcuts()
            } catch (_: Throwable) {}

            return START_STICKY
        } catch (_: Throwable) {
            isRunning = false
            stopSelf()
            return START_NOT_STICKY
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isRunning = false
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Text Expander Background Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps Text Expander 2R shortcut detection active"
                setShowBadge(false)
                enableVibration(false)
                enableLights(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, launchIntent, flags)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Text Expander 2R Active")
            .setContentText("Shortcut detection is active in background")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setAutoCancel(false)
            .build()
    }
}
