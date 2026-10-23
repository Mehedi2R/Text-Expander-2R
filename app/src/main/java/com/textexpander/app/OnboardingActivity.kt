package com.textexpander.app

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.textexpander.app.databinding.ActivityOnboardingBinding
import com.textexpander.app.service.TextExpanderAccessibilityService

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private var forceShow = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        forceShow = intent.getBooleanExtra("force_show", false)

        // If not forced and both permissions are already granted, immediately launch MainActivity!
        if (!forceShow && areAllPermissionsGranted(this)) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
        updatePermissionStatus()
    }

    override fun onResume() {
        super.onResume()
        if (::binding.isInitialized) {
            updatePermissionStatus()

            // If user just granted both permissions in phone settings, transition to MainActivity automatically!
            if (!forceShow && areAllPermissionsGranted(this)) {
                binding.root.postDelayed({
                    if (!isFinishing && !isDestroyed) {
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    }
                }, 400)
            }
        }
    }

    @SuppressLint("BatteryLife")
    private fun setupListeners() {
        binding.cardAllowBackground.setOnClickListener {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                }
            } catch (_: Exception) {
                try {
                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                } catch (_: Exception) {
                    try {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:$packageName")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        startActivity(intent)
                    } catch (_: Exception) {}
                }
            }
        }

        binding.cardEnableAccessibility.setOnClickListener {
            if (isAccessibilityAllowed(this)) {
                try {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                } catch (_: Exception) {}
            } else {
                showAccessibilityDisclosureDialog()
            }
        }

        var isRestrictedGuideExpanded = false
        binding.btnToggleRestrictedGuide.setOnClickListener {
            isRestrictedGuideExpanded = !isRestrictedGuideExpanded
            binding.layoutRestrictedDetails.visibility = if (isRestrictedGuideExpanded) android.view.View.VISIBLE else android.view.View.GONE
            binding.imgArrowRestricted.setImageResource(
                if (isRestrictedGuideExpanded) R.drawable.ic_chevron_up else R.drawable.ic_chevron_down
            )
        }

        binding.btnOpenAppInfo.setOnClickListener {
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            } catch (_: Exception) {}
        }

        binding.btnContinue.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    private fun showAccessibilityDisclosureDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Accessibility Permission & Privacy")
            .setMessage("Text Expander 2R requires Accessibility Service permission solely to detect when you type your configured shortcut keywords and automatically replace them with your expanded text snippets.\n\n" +
                    "• Core Purpose: Detect typed shortcut keywords and perform in-place text expansion.\n" +
                    "• What We Access: Text input changes solely to recognize your saved shortcut keywords.\n" +
                    "• Privacy Guarantee: We NEVER collect, record, log, or transmit personal data, keystrokes, passwords, or credit card numbers.\n" +
                    "• 100% Offline: This app operates completely on-device with ZERO internet permission.")
            .setPositiveButton("Agree & Proceed") { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                } catch (_: Exception) {}
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updatePermissionStatus() {
        // 1. Check Background / Battery Optimization
        val isBgAllowed = isBackgroundAllowed(this)
        if (isBgAllowed) {
            binding.imgArrowBg.setImageResource(R.drawable.ic_check_circle)
            binding.imgArrowBg.setColorFilter(ContextCompat.getColor(this, R.color.success))
            binding.tvSubtitleBg.text = "Active • Service won't be stopped in background"
        }

        // 2. Check Accessibility Service
        val isAccEnabled = isAccessibilityAllowed(this)
        if (isAccEnabled) {
            binding.imgArrowAcc.setImageResource(R.drawable.ic_check_circle)
            binding.imgArrowAcc.setColorFilter(ContextCompat.getColor(this, R.color.success))
            binding.tvSubtitleAcc.text = "Active • Ready to expand keywords everywhere"
            binding.layoutRestrictedDetails.visibility = android.view.View.GONE
            binding.imgArrowRestricted.setImageResource(R.drawable.ic_chevron_down)
        }
    }

    companion object {
        fun isBackgroundAllowed(context: Context): Boolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                return pm?.isIgnoringBatteryOptimizations(context.packageName) == true
            }
            return true
        }

        fun isAccessibilityAllowed(context: Context): Boolean {
            return isAccessibilityServiceEnabled(context, TextExpanderAccessibilityService::class.java)
        }

        fun areAllPermissionsGranted(context: Context): Boolean {
            return isAccessibilityAllowed(context)
        }

        fun isAccessibilityServiceEnabled(
            context: Context,
            service: Class<out AccessibilityService>
        ): Boolean {
            val expectedComponentName = ComponentName(context, service)
            val enabledServicesSetting = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            val colonSplitter = TextUtils.SimpleStringSplitter(':')
            colonSplitter.setString(enabledServicesSetting)

            while (colonSplitter.hasNext()) {
                val componentNameString = colonSplitter.next()
                val enabledComponent = ComponentName.unflattenFromString(componentNameString)
                if (enabledComponent != null && enabledComponent == expectedComponentName) {
                    return true
                }
            }
            return false
        }
    }
}
