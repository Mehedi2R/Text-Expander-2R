package com.textexpander.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.ImageView
import android.widget.TextView
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.FrameLayout
import android.graphics.Typeface
import androidx.core.content.ContextCompat
import androidx.appcompat.view.ContextThemeWrapper
import androidx.appcompat.widget.SwitchCompat
import androidx.core.widget.NestedScrollView
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.textexpander.app.R
import com.textexpander.app.TextExpanderApp
import com.textexpander.app.data.AppDatabase
import com.textexpander.app.data.ClipboardEntity
import com.textexpander.app.data.ClipboardRepository
import com.textexpander.app.data.SettingsPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class TextExpanderAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "TextExpanderService"
        private const val EXPANSION_COOLDOWN_MS = 250L

        @Volatile
        var isServiceRunning: Boolean = false
            private set

        @Volatile
        private var activeServiceInstance: TextExpanderAccessibilityService? = null

        fun reloadShortcuts() {
            activeServiceInstance?.loadShortcutsSync()
        }
    }

    private data class ShortcutData(
        val id: Long,
        val key: String,
        val text: String
    )

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mainHandler = Handler(Looper.getMainLooper())

    // Memory-efficient shortcut index: key (lowercase/normalized) -> ShortcutData
    private val shortcutIndex = ConcurrentHashMap<String, ShortcutData>()
    @Volatile
    private var maxShortcutLength: Int = 10
    private var pendingDetectionRunnable: Runnable? = null
    private val isCurrentlyExpanding = AtomicBoolean(false)

    private lateinit var clipboardManager: ClipboardManager
    private var windowManager: WindowManager? = null

    // Clipboard tracking state
    private var lastSelectedText: String? = null
    private var lastSelectedTime: Long = 0L
    private var lastLongClickedText: String? = null
    private var lastLongClickedTime: Long = 0L
    private var lastCapturedClipboardText: String = ""

    private val clipChangedListener = ClipboardManager.OnPrimaryClipChangedListener {
        handleClipboardChanged()
    }

    // Floating suggestion card state
    private var floatingView: View? = null
    private var tvSuggestionText: TextView? = null
    private var tvSuggestionKey: TextView? = null
    private var scrollSuggestionText: NestedScrollView? = null
    private var switchHumanTyping: SwitchCompat? = null
    private var isFloatingViewAdded = false

    // Floating Quick Box overlay state (/box or box)
    private var floatingBoxView: View? = null
    private var etBoxInput: EditText? = null
    private var switchBoxHumanTyping: SwitchCompat? = null
    private var isFloatingBoxAdded = false

    // Floating Variable input dialog state
    private var floatingVarView: View? = null
    private var switchVarHumanTyping: SwitchCompat? = null
    private var layoutVarContainer: LinearLayout? = null
    private var isFloatingVarAdded = false
    private var pendingVariableTemplate: String = ""
    private var pendingVariableTargetNode: AccessibilityNodeInfo? = null
    private var pendingVariableFullText: String = ""
    private var pendingVariableStart: Int = -1
    private var pendingVariableEnd: Int = -1

    private var currentMatchedKey: String = ""
    private var currentExpansionText: String = ""
    private var currentMatchStart: Int = -1
    private var currentMatchEnd: Int = -1
    private var lastActiveEditableNode: AccessibilityNodeInfo? = null
    private var lastActiveAppPackage: String = ""
    private var lastKnownKeyboardHeight: Int = 0
    private var lastExpandedTimestamp: Long = 0L
    private var lastExpandedKey: String = ""

    override fun onCreate() {
        super.onCreate()
        activeServiceInstance = this
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        // Avoid reading primaryClip on service creation to prevent Android 12+ clipboard access notice
        try {
            clipboardManager.addPrimaryClipChangedListener(clipChangedListener)
        } catch (e: Exception) {
            Log.e(TAG, "Error adding clip listener: ${e.message}")
        }
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        loadShortcutsSync()
        observeShortcutsDatabase()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        activeServiceInstance = this
        isServiceRunning = true
        Log.i(TAG, "Text Expander 2R Service Connected")

        serviceInfo = (serviceInfo ?: AccessibilityServiceInfo()).apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 20
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        }

        loadShortcutsSync()
    }

    fun loadShortcutsSync() {
        serviceScope.launch(Dispatchers.IO) {
            try {
                val dao = (applicationContext as TextExpanderApp).database.shortcutDao()
                val list = dao.allShortcutsSync
                val map = ConcurrentHashMap<String, ShortcutData>()
                for (s in list) {
                    val raw = s.shortcutKey.trim()
                    if (raw.isNotEmpty()) {
                        val cleanKey = Normalizer.normalize(raw.lowercase(Locale.ROOT), Normalizer.Form.NFC)
                        map[cleanKey] = ShortcutData(
                            id = s.id,
                            key = raw,
                            text = s.expandedText
                        )
                    }
                }
                shortcutIndex.clear()
                shortcutIndex.putAll(map)
                maxShortcutLength = (map.keys.maxOfOrNull { it.length } ?: 10).coerceAtLeast(3)
                Log.d(TAG, "Loaded shortcuts sync count: ${shortcutIndex.size}, maxLen: $maxShortcutLength")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading shortcuts sync", e)
            }
        }
    }

    private fun observeShortcutsDatabase() {
        mainHandler.post {
            try {
                val dao = (applicationContext as TextExpanderApp).database.shortcutDao()
                dao.allShortcutsLiveData.observeForever { shortcuts ->
                    serviceScope.launch(Dispatchers.IO) {
                        val map = ConcurrentHashMap<String, ShortcutData>()
                        for (s in shortcuts) {
                            val raw = s.shortcutKey.trim()
                            if (raw.isNotEmpty()) {
                                val cleanKey = Normalizer.normalize(raw.lowercase(Locale.ROOT), Normalizer.Form.NFC)
                                map[cleanKey] = ShortcutData(
                                    id = s.id,
                                    key = raw,
                                    text = s.expandedText
                                )
                            }
                        }
                        shortcutIndex.clear()
                        shortcutIndex.putAll(map)
                        maxShortcutLength = (map.keys.maxOfOrNull { it.length } ?: 10).coerceAtLeast(3)
                        Log.d(TAG, "Database observed update: ${shortcutIndex.size} shortcuts, maxLen: $maxShortcutLength")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Database observer failed", e)
            }
        }
    }

    private var lastHardwareKeyCode: Int = KeyEvent.KEYCODE_UNKNOWN
    private var lastHardwareEventTime: Long = 0L

    override fun onKeyEvent(event: KeyEvent): Boolean {
        handleHardwareKeyEvent(event)
        return super.onKeyEvent(event)
    }

    private fun handleHardwareKeyEvent(event: KeyEvent) {
        val settings = SettingsPreferences.get(this)
        if (!settings.enableStatistics) return

        // 1. Only process key press DOWN (prevents duplicate counting on UP or MULTIPLE)
        if (event.action != KeyEvent.ACTION_DOWN) return

        // 2. Ignore auto-repeat events when a key is held down
        if (event.repeatCount > 0) return

        // 3. Prevent duplicate dispatches (debouncing within 30ms for the exact same key)
        val now = SystemClock.uptimeMillis()
        if (event.keyCode == lastHardwareKeyCode && (now - lastHardwareEventTime < 30L)) {
            return
        }
        lastHardwareKeyCode = event.keyCode
        lastHardwareEventTime = now

        // 4. Verify that this is purely a physical hardware keystroke (ignore soft/virtual keyboards)
        if ((event.flags and KeyEvent.FLAG_SOFT_KEYBOARD) != 0) return
        if ((event.flags and KeyEvent.FLAG_VIRTUAL_HARD_KEY) != 0) return

        val device = event.device ?: InputDevice.getDevice(event.deviceId)
        if (device != null && device.isVirtual) return

        val source = event.source
        val isKeyboardSource = (source and InputDevice.SOURCE_KEYBOARD) == InputDevice.SOURCE_KEYBOARD ||
                (device != null && (device.sources and InputDevice.SOURCE_KEYBOARD) == InputDevice.SOURCE_KEYBOARD)

        if (!isKeyboardSource) return

        // 5. Ignore copy-paste and system command shortcuts (Ctrl, Meta/Command, Alt combinations)
        if (event.isCtrlPressed || event.isMetaPressed || event.isAltPressed) {
            return
        }

        // 6. Ignore modifier keys pressed individually
        when (event.keyCode) {
            KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT,
            KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT,
            KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT,
            KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_META_RIGHT,
            KeyEvent.KEYCODE_CAPS_LOCK, KeyEvent.KEYCODE_NUM_LOCK,
            KeyEvent.KEYCODE_SCROLL_LOCK, KeyEvent.KEYCODE_FUNCTION,
            KeyEvent.KEYCODE_SYM -> return
        }

        // 7. Ignore system commands, volume, power, media, navigation, and function keys
        when (event.keyCode) {
            // Volume & Audio
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE,
            KeyEvent.KEYCODE_MUTE, KeyEvent.KEYCODE_HEADSETHOOK,

            // Media Controls
            KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_STOP,
            KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_PREVIOUS,
            KeyEvent.KEYCODE_MEDIA_REWIND, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
            KeyEvent.KEYCODE_MEDIA_RECORD,

            // Power, Sleep, Wake
            KeyEvent.KEYCODE_POWER, KeyEvent.KEYCODE_SLEEP, KeyEvent.KEYCODE_WAKEUP,

            // Navigation & Hardware Buttons
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_APP_SWITCH,
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_SEARCH, KeyEvent.KEYCODE_ASSIST,
            KeyEvent.KEYCODE_VOICE_ASSIST, KeyEvent.KEYCODE_CAMERA,
            KeyEvent.KEYCODE_BRIGHTNESS_DOWN, KeyEvent.KEYCODE_BRIGHTNESS_UP,
            KeyEvent.KEYCODE_WINDOW, KeyEvent.KEYCODE_ESCAPE,

            // Function Keys (F1 - F12)
            KeyEvent.KEYCODE_F1, KeyEvent.KEYCODE_F2, KeyEvent.KEYCODE_F3, KeyEvent.KEYCODE_F4,
            KeyEvent.KEYCODE_F5, KeyEvent.KEYCODE_F6, KeyEvent.KEYCODE_F7, KeyEvent.KEYCODE_F8,
            KeyEvent.KEYCODE_F9, KeyEvent.KEYCODE_F10, KeyEvent.KEYCODE_F11, KeyEvent.KEYCODE_F12 -> return
        }

        // 8. Accurately validate typing keys (letters, numbers, punctuation, spaces, enters, tabs, backspace/del)
        val isTypingKey = event.isPrintingKey ||
                event.keyCode == KeyEvent.KEYCODE_SPACE ||
                event.keyCode == KeyEvent.KEYCODE_ENTER ||
                event.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER ||
                event.keyCode == KeyEvent.KEYCODE_TAB ||
                event.keyCode == KeyEvent.KEYCODE_DEL ||
                event.keyCode == KeyEvent.KEYCODE_FORWARD_DEL

        if (!isTypingKey) return

        // 9. Legitimate, non-duplicate hardware typing keystroke confirmed!
        settings.recordHardwareKeystrokes(1)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || isCurrentlyExpanding.get()) return

        val settings = SettingsPreferences.get(this)
        if (!settings.enableDetection) {
            hideFloatingSuggestion()
            return
        }

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                handleTextChangedEvent(event)
            }
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED -> {
                handleTextSelectionChanged(event)
            }
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> {
                handleViewLongClicked(event)
            }
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                handleViewClicked(event)
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                handleWindowStateChanged(event)
            }
            else -> {}
        }
    }

    private fun isKeyboardPackage(pkg: String): Boolean {
        if (pkg.isEmpty()) return false
        val lower = pkg.lowercase(Locale.ROOT)
        return lower.contains("inputmethod") ||
                lower.contains("keyboard") ||
                lower.contains("honeyboard") ||
                lower.contains("latin") ||
                lower.contains("ime") ||
                lower.contains("gboard") ||
                lower.contains("swiftkey") ||
                lower.contains("florisboard") ||
                lower.contains("ridmik")
    }

    private fun handleWindowStateChanged(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return

        if (isKeyboardPackage(pkg)) {
            return
        }

        if (lastActiveAppPackage.isNotEmpty() && pkg != lastActiveAppPackage) {
            hideFloatingSuggestion()
        }
        lastActiveAppPackage = pkg
    }

    private fun handleTextSelectionChanged(event: AccessibilityEvent) {
        val from = event.fromIndex
        val to = event.toIndex
        if (from == to) return

        val textList = event.text
        if (!textList.isNullOrEmpty()) {
            val fullText = textList.joinToString("")
            val start = minOf(from, to)
            val end = maxOf(from, to)
            val selected = if (start in 0..fullText.length && end in 0..fullText.length && start < end) {
                fullText.substring(start, end).trim()
            } else {
                fullText.trim()
            }
            if (selected.isNotEmpty()) {
                lastSelectedText = selected
                lastSelectedTime = System.currentTimeMillis()
            }
        }

        val source = event.source
        if (source != null) {
            val nodeText = source.text?.toString()
            if (!nodeText.isNullOrEmpty()) {
                val start = minOf(from, to)
                val end = maxOf(from, to)
                val selected = if (start in 0..nodeText.length && end in 0..nodeText.length && start < end) {
                    nodeText.substring(start, end).trim()
                } else if (source.isFocused || source.isSelected) {
                    nodeText.trim()
                } else null

                if (!selected.isNullOrEmpty()) {
                    lastSelectedText = selected
                    lastSelectedTime = System.currentTimeMillis()
                }
            }
            try {
                @Suppress("DEPRECATION")
                source.recycle()
            } catch (_: Exception) {}
        }
    }

    private fun handleViewLongClicked(event: AccessibilityEvent) {
        val textList = event.text
        var captured: String? = null
        if (!textList.isNullOrEmpty()) {
            captured = textList.joinToString("\n").trim()
        }
        if (captured.isNullOrEmpty()) {
            val source = event.source
            if (source != null) {
                val nodeText = source.text?.toString()?.trim()
                val desc = source.contentDescription?.toString()?.trim()
                captured = when {
                    !nodeText.isNullOrEmpty() -> nodeText
                    !desc.isNullOrEmpty() -> desc
                    else -> null
                }
                try {
                    @Suppress("DEPRECATION")
                    source.recycle()
                } catch (_: Exception) {}
            }
        }
        if (!captured.isNullOrEmpty()) {
            lastLongClickedText = captured
            lastLongClickedTime = System.currentTimeMillis()
        }
    }

    private fun handleViewClicked(event: AccessibilityEvent) {
        val source = event.source ?: return
        val text = source.text?.toString() ?: ""
        val desc = source.contentDescription?.toString() ?: ""
        val viewId = source.viewIdResourceName ?: ""

        val isCopyOrCut = isCopyAction(text, desc, viewId)

        if (isCopyOrCut) {
            if (lastSelectedText.isNullOrBlank() || (System.currentTimeMillis() - lastSelectedTime > 30000)) {
                tryExtractSelectedTextFromWindow()
            }
            mainHandler.postDelayed({
                handleClipboardChanged()
            }, 150)
        }
        try {
            @Suppress("DEPRECATION")
            source.recycle()
        } catch (_: Exception) {}
    }

    private fun isCopyAction(text: String, desc: String, viewId: String): Boolean {
        val combined = "$text $desc".lowercase(Locale.ROOT)
        val idLower = viewId.lowercase(Locale.ROOT)

        val copyKeywords = arrayOf(
            "copy", "cut", "কপি", "কাট", "कॉपी", "कट",
            "copiar", "cortar", "copier", "couper", "kopieren",
            "ausschneiden", "копировать", "نسخ", "复制", "コピー"
        )
        for (kw in copyKeywords) {
            if (combined.contains(kw)) return true
        }

        val idKeywords = arrayOf(
            "copy", "cut", "action_mode_close_button", "menu_copy",
            "action_copy", "btn_copy", "copy_button", "selection_copy",
            "button_copy"
        )
        for (kw in idKeywords) {
            if (idLower.contains(kw)) return true
        }
        return false
    }

    private fun tryExtractSelectedTextFromWindow() {
        try {
            val root = rootInActiveWindow ?: return
            val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (focused != null) {
                val fullText = focused.text?.toString()
                val selStart = focused.textSelectionStart
                val selEnd = focused.textSelectionEnd
                if (!fullText.isNullOrEmpty() && selStart >= 0 && selEnd <= fullText.length && selStart < selEnd) {
                    val sel = fullText.substring(selStart, selEnd).trim()
                    if (sel.isNotEmpty()) {
                        lastSelectedText = sel
                        lastSelectedTime = System.currentTimeMillis()
                        try {
                            @Suppress("DEPRECATION")
                            focused.recycle()
                        } catch (_: Exception) {}
                        return
                    }
                }
                try {
                    @Suppress("DEPRECATION")
                    focused.recycle()
                } catch (_: Exception) {}
            }

            // Fallback: check if lastLongClickedText is recent (within 30 seconds)
            if (!lastLongClickedText.isNullOrBlank() && (System.currentTimeMillis() - lastLongClickedTime < 30000)) {
                lastSelectedText = lastLongClickedText
                lastSelectedTime = System.currentTimeMillis()
            }
        } catch (e: Exception) {
            Log.d(TAG, "Window text extraction: ${e.message}")
        }
    }

    private fun handleClipboardChanged() {
        val settings = SettingsPreferences.get(this)
        if (!settings.enableClipboard) return

        // 1. Direct read if primaryClip is available (Always prioritize genuine system clipboard)
        var clipText: String? = null
        try {
            if (clipboardManager.hasPrimaryClip()) {
                val clip = clipboardManager.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    val t = clip.getItemAt(0)?.coerceToText(this)?.toString()
                    if (!t.isNullOrBlank()) {
                        clipText = t
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Cannot read primaryClip in background: ${e.message}")
        }

        if (!clipText.isNullOrBlank()) {
            lastSelectedText = null
            lastLongClickedText = null
            saveCapturedClipboard(clipText)
            return
        }

        // 2. Fallback: Accessibility capture from selection or long-click ONLY if primaryClip was blocked by OS
        val recent = lastSelectedText
        if (!recent.isNullOrBlank() && (System.currentTimeMillis() - lastSelectedTime < 15000)) {
            lastSelectedText = null
            saveCapturedClipboard(recent)
            return
        }

        val recentLong = lastLongClickedText
        if (!recentLong.isNullOrBlank() && (System.currentTimeMillis() - lastLongClickedTime < 15000)) {
            lastLongClickedText = null
            saveCapturedClipboard(recentLong)
            return
        }
    }

    private fun saveCapturedClipboard(text: String) {
        val clean = text.trim()
        if (clean.isBlank()) return
        if (clean == currentExpansionText) return
        if (clean == lastCapturedClipboardText) return
        lastCapturedClipboardText = clean

        serviceScope.launch(Dispatchers.IO) {
            ClipboardRepository.recordCopiedText(
                this@TextExpanderAccessibilityService,
                clean,
                currentExpansionText
            )
        }
    }

    private fun isAppAllowedForDetection(
        event: AccessibilityEvent,
        sourceNode: AccessibilityNodeInfo?,
        settings: SettingsPreferences
    ): Boolean {
        val pkg = event.packageName?.toString() ?: sourceNode?.packageName?.toString() ?: ""
        if (pkg.isEmpty()) return true
        if (pkg == packageName) return false
        if (isKeyboardPackage(pkg)) return false

        // Passwords should not be intercepted for security
        if (sourceNode?.isPassword == true) return false

        // Feature: Detect in all apps
        if (settings.detectInAllApps) {
            // Allows detection everywhere: webviews, browsers, custom views, chats, docs, etc.
            return true
        }

        // When detectInAllApps is disabled (standard mode):
        // Allow text input everywhere except strictly non-editable static labels
        if (sourceNode != null && !sourceNode.isEditable) {
            val isWebOrInput = sourceNode.isFocused || sourceNode.isFocusable ||
                    sourceNode.className?.contains("EditText", ignoreCase = true) == true ||
                    sourceNode.className?.contains("UrlBar", ignoreCase = true) == true ||
                    sourceNode.className?.contains("WebView", ignoreCase = true) == true ||
                    sourceNode.className?.contains("Chrome", ignoreCase = true) == true ||
                    pkg.contains("chrome", ignoreCase = true) ||
                    pkg.contains("browser", ignoreCase = true)
            if (!isWebOrInput) return false
        }

        return true
    }

    private fun isLanguageAllowed(candidate: String, settings: SettingsPreferences): Boolean {
        if (settings.detectInAllLanguages) {
            return true
        }
        // Strict English mode (ASCII only: codes <= 127)
        for (c in candidate) {
            if (c.code > 127) {
                return false
            }
        }
        return true
    }

    private fun isWordCharacter(c: Char, detectInAllLanguages: Boolean): Boolean {
        if (c.isWhitespace() || c in "\n\r") return false
        if (!detectInAllLanguages) {
            // ASCII alphanumeric and safe prefix characters
            return (c in 'a'..'z') || (c in 'A'..'Z') || (c in '0'..'9') ||
                    c == '_' || c == '-' || c == '.' || c == '@' || c == '/' || c == '#' || c == '!'
        }
        // Multi-language / Unicode mode:
        // Support Unicode letters, digits, and combining marks (Bengali matras / diacritics / vowels)
        val type = Character.getType(c)
        return Character.isLetterOrDigit(c) ||
                type == Character.NON_SPACING_MARK.toInt() ||
                type == Character.COMBINING_SPACING_MARK.toInt() ||
                type == Character.CONNECTOR_PUNCTUATION.toInt() ||
                c == '_' || c == '-' || c == '.' || c == '@' || c == '/' || c == '#' || c == '!'
    }

    private fun isAtWordBoundary(fullText: String, startIndex: Int, detectInAllLanguages: Boolean): Boolean {
        if (startIndex <= 0) return true
        val prevChar = fullText[startIndex - 1]
        if (prevChar.isWhitespace() || prevChar in "\n\r.,!?;:()[]{}<>\"'`~^&*+=|\\") {
            return true
        }
        return !isWordCharacter(prevChar, detectInAllLanguages)
    }

    private fun handleTextChangedEvent(event: AccessibilityEvent) {
        if (isCurrentlyExpanding.get()) return

        // If user is deleting / backspacing, immediately hide popup and DO NOT trigger matching
        if (event.removedCount > 0 && event.addedCount == 0) {
            hideFloatingSuggestion()
            return
        }

        val settings = SettingsPreferences.get(this)
        // Feature 1: Enable detection
        if (!settings.enableDetection) {
            hideFloatingSuggestion()
            return
        }

        if (shortcutIndex.isEmpty()) {
            loadShortcutsSync()
        }

        val sourceNode = findActiveEditableNode(event, settings)
        if (!isAppAllowedForDetection(event, sourceNode, settings)) {
            hideFloatingSuggestion()
            return
        }

        val textFromNode = sourceNode?.text?.toString() ?: ""
        val textFromEvent = if (!event.text.isNullOrEmpty()) event.text.joinToString("") else ""
        val fullText = if (textFromNode.isNotEmpty()) textFromNode else textFromEvent
        if (fullText.isEmpty()) {
            hideFloatingSuggestion()
            return
        }

        var cursorIndex = if (sourceNode != null) {
            getCursorPosition(sourceNode, event, fullText)
        } else {
            fullText.length
        }
        if (cursorIndex <= 0 || cursorIndex > fullText.length) {
            cursorIndex = fullText.length
        }

        // Cache the active editable node so user tap on the floating card always knows the target
        if (sourceNode != null) {
            try {
                @Suppress("DEPRECATION")
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                    lastActiveEditableNode?.recycle()
                    lastActiveEditableNode = AccessibilityNodeInfo.obtain(sourceNode)
                } else {
                    lastActiveEditableNode = sourceNode
                }
            } catch (_: Exception) {}
        }

        // Feature 2: Use optimized detection
        if (settings.useOptimizedDetection) {
            pendingDetectionRunnable?.let { mainHandler.removeCallbacks(it) }
            val capturedNode = sourceNode?.let {
                try {
                    @Suppress("DEPRECATION")
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) AccessibilityNodeInfo.obtain(it) else it
                } catch (_: Exception) { null }
            }
            val runnable = Runnable {
                try {
                    processDetection(capturedNode, fullText, cursorIndex, settings, textFromEvent)
                } finally {
                    @Suppress("DEPRECATION")
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                        try { capturedNode?.recycle() } catch (_: Exception) {}
                    }
                }
            }
            pendingDetectionRunnable = runnable
            mainHandler.postDelayed(runnable, 20L)
        } else {
            // Immediate deep-scan mode
            processDetection(sourceNode, fullText, cursorIndex, settings, textFromEvent)
        }
    }

    private fun processDetection(
        sourceNode: AccessibilityNodeInfo?,
        fullText: String,
        cursorIndex: Int,
        settings: SettingsPreferences,
        textFromEvent: String = ""
    ) {
        if (cursorIndex <= 0 || cursorIndex > fullText.length) return

        val lastChar = fullText[cursorIndex - 1]

        // Helper to check for a shortcut matching at the end of a given string slice
        fun tryFindMatch(endPos: Int, textSource: String): Pair<ShortcutData, Int>? {
            if (endPos <= 0 || endPos > textSource.length) return null

            // If the cursor is followed immediately by another word character, we are in the middle of a word (e.g. inside "Bkash")
            if (endPos < textSource.length) {
                val nextChar = textSource[endPos]
                if (isWordCharacter(nextChar, settings.detectInAllLanguages)) {
                    return null
                }
            }

            // 1. Direct word check
            var tokenStart = endPos
            while (tokenStart > 0 && isWordCharacter(textSource[tokenStart - 1], settings.detectInAllLanguages)) {
                tokenStart--
            }
            if (tokenStart < endPos) {
                val typedToken = textSource.substring(tokenStart, endPos).trim()
                if (isLanguageAllowed(typedToken, settings)) {
                    val keyToLookup = Normalizer.normalize(typedToken.lowercase(Locale.ROOT), Normalizer.Form.NFC)
                    val directMatch = shortcutIndex[keyToLookup]
                    if (directMatch != null) {
                        if (!settings.matchFromBeginning || isAtWordBoundary(textSource, tokenStart, settings.detectInAllLanguages)) {
                            return Pair(directMatch, tokenStart)
                        }
                    }
                }
            }

            // 2. Suffix scan (for shortcuts with punctuation prefixes e.g. @@email, ;addr, /cmd)
            val scanLen = if (settings.useOptimizedDetection) {
                minOf(maxShortcutLength, endPos)
            } else {
                minOf(30, endPos)
            }
            for (len in 1..scanLen) {
                val s = endPos - len
                val candidate = textSource.substring(s, endPos).trim()
                if (!isLanguageAllowed(candidate, settings)) continue
                val keyToLookup = Normalizer.normalize(candidate.lowercase(Locale.ROOT), Normalizer.Form.NFC)
                val m = shortcutIndex[keyToLookup]
                if (m != null) {
                    if (settings.matchFromBeginning && !isAtWordBoundary(textSource, s, settings.detectInAllLanguages)) {
                        continue
                    }
                    return Pair(m, s)
                }
            }
            return null
        }

        // Mode 1: Delimiter auto-expand (user typed space or enter after shortcut)
        if (lastChar.isWhitespace()) {
            hideFloatingSuggestion()
            val candidateEnd = cursorIndex - 1
            val res = tryFindMatch(candidateEnd, fullText)
            if (res != null) {
                // If matched key is /box, show Quick Box
                if (res.first.key.equals("/box", ignoreCase = true)) {
                    showFloatingBox(sourceNode, fullText, res.second, cursorIndex)
                    return
                }
                performDirectExpansion(sourceNode, fullText, res.second, cursorIndex, res.first.text)
                return
            }
        } else {
            // Mode 2: In-progress typing -> Check for "/box" or double comma ",," trigger
            // 1. Double comma ",," trigger (2 characters)
            if (cursorIndex >= 2 && fullText.substring(cursorIndex - 2, cursorIndex) == ",,") {
                hideFloatingSuggestion()
                showFloatingBox(sourceNode, fullText, cursorIndex - 2, cursorIndex)
                return
            }
            // 2. "/box" trigger (4 characters)
            if (cursorIndex >= 4) {
                val candidate = fullText.substring(cursorIndex - 4, cursorIndex)
                if (candidate.equals("/box", ignoreCase = true)) {
                    val wordStart = cursorIndex - 4
                    if (wordStart == 0 || isAtWordBoundary(fullText, wordStart, settings.detectInAllLanguages)) {
                        hideFloatingSuggestion()
                        showFloatingBox(sourceNode, fullText, wordStart, cursorIndex)
                        return
                    }
                }
            }

            var matched: ShortcutData? = null
            var matchStart = -1
            var matchEnd = -1

            // Check A: Standard cursor position
            val res1 = tryFindMatch(cursorIndex, fullText)
            if (res1 != null) {
                matched = res1.first
                matchStart = res1.second
                matchEnd = cursorIndex
            }

            // Check B: Selection start (for Chrome Omnibox with autocomplete tail)
            if (matched == null && sourceNode != null) {
                val selStart = sourceNode.textSelectionStart
                if (selStart in 1 until fullText.length && selStart != cursorIndex) {
                    val res2 = tryFindMatch(selStart, fullText)
                    if (res2 != null) {
                        matched = res2.first
                        matchStart = res2.second
                        matchEnd = selStart
                    }
                }
            }

            // Check C: Text from event fallback (for Chrome & WebViews)
            if (matched == null && textFromEvent.isNotEmpty()) {
                val res3 = tryFindMatch(textFromEvent.length, textFromEvent)
                if (res3 != null) {
                    matched = res3.first
                    matchStart = res3.second
                    matchEnd = textFromEvent.length
                }
            }

            if (matched != null && matchStart != -1 && matchEnd != -1) {
                if (!settings.enableSuggestionOverlay) {
                    performDirectExpansion(sourceNode, fullText, matchStart, matchEnd, matched.text)
                } else {
                    Log.d(TAG, "Shortcut matched: ${matched.key} -> showing popup above keyboard")
                    showFloatingSuggestion(sourceNode, matched.key, matched.text, matchStart, matchEnd)
                }
            } else {
                hideFloatingSuggestion()
            }
        }
    }

    private fun initFloatingView() {
        if (floatingView != null) return
        try {
            val contextWrapper = ContextThemeWrapper(this, R.style.Theme_TextExpander)
            val inflater = LayoutInflater.from(contextWrapper)
            val view = inflater.inflate(R.layout.layout_floating_suggestion, null)
            floatingView = view
            tvSuggestionKey = view.findViewById(R.id.tvSuggestionKey)
            tvSuggestionText = view.findViewById(R.id.tvSuggestionText)
            scrollSuggestionText = view.findViewById(R.id.scrollSuggestionText)
            switchHumanTyping = view.findViewById(R.id.switchHumanTyping)

            val layoutHumanTyping = view.findViewById<View>(R.id.layoutHumanTyping)
            layoutHumanTyping?.setOnClickListener {
                switchHumanTyping?.let { sw ->
                    sw.isChecked = !sw.isChecked
                }
            }
            switchHumanTyping?.setOnClickListener {
                // Consume touch event within toggle switch, preventing popup tap
            }

            val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
            var downY = 0f
            var downX = 0f
            var isScrolling = false

            tvSuggestionText?.isClickable = false
            tvSuggestionText?.isFocusable = false

            scrollSuggestionText?.setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downY = event.rawY
                        downX = event.rawX
                        isScrolling = false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dy = kotlin.math.abs(event.rawY - downY)
                        val dx = kotlin.math.abs(event.rawX - downX)
                        if (dy > touchSlop * 1.5f || dx > touchSlop * 1.5f) {
                            isScrolling = true
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        val dy = kotlin.math.abs(event.rawY - downY)
                        val dx = kotlin.math.abs(event.rawX - downX)
                        if (!isScrolling && dy < touchSlop * 2f && dx < touchSlop * 2f) {
                            // User selected/tapped text -> input with vibration!
                            onFloatingSuggestionTapped()
                            return@setOnTouchListener true
                        }
                    }
                }
                v.onTouchEvent(event)
            }

            tvSuggestionKey?.setOnClickListener {
                onFloatingSuggestionTapped()
            }
            view.findViewById<View>(R.id.layoutSuggestionGrip)?.setOnClickListener {
                onFloatingSuggestionTapped()
            }
            view.findViewById<View>(R.id.rootSuggestionBar)?.setOnClickListener {
                onFloatingSuggestionTapped()
            }

            view.setOnTouchListener { _, touchEvent ->
                if (touchEvent.action == MotionEvent.ACTION_OUTSIDE) {
                    hideFloatingSuggestion()
                    true
                } else {
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init floating view", e)
        }
    }

    private fun getKeyboardTopOnScreen(screenHeight: Int): Int? {
        try {
            val windowList = windows
            if (!windowList.isNullOrEmpty()) {
                for (win in windowList) {
                    if (win.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                        val rect = Rect()
                        win.getBoundsInScreen(rect)
                        // Soft keyboards typically occupy the bottom 20% to 65% of screen
                        if (rect.top in (screenHeight * 0.25).toInt()..(screenHeight - 40)) {
                            val kbHeight = screenHeight - rect.top
                            if (kbHeight > (screenHeight * 0.18)) {
                                lastKnownKeyboardHeight = kbHeight
                            }
                            return rect.top
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying keyboard window bounds", e)
        }
        if (lastKnownKeyboardHeight > (screenHeight * 0.18)) {
            return screenHeight - lastKnownKeyboardHeight
        }
        return null
    }

    private fun getRealScreenDimensions(): Pair<Int, Int> {
        val wm = windowManager ?: getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            Pair(bounds.width(), bounds.height())
        } else {
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(dm)
            Pair(dm.widthPixels, dm.heightPixels)
        }
    }

    private fun showFloatingSuggestion(
        node: AccessibilityNodeInfo?,
        matchedKey: String,
        expansionText: String,
        startIndex: Int,
        endIndex: Int
    ) {
        currentMatchedKey = matchedKey
        currentExpansionText = expansionText
        currentMatchStart = startIndex
        currentMatchEnd = endIndex

        if (node != null) {
            try {
                @Suppress("DEPRECATION")
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                    lastActiveEditableNode?.recycle()
                    lastActiveEditableNode = AccessibilityNodeInfo.obtain(node)
                } else {
                    lastActiveEditableNode = node
                }
            } catch (_: Exception) {}
        }

        mainHandler.post {
            try {
                initFloatingView()
                val fView = floatingView ?: return@post

                tvSuggestionKey?.text = matchedKey
                tvSuggestionText?.text = expansionText.trim()
                tvSuggestionText?.maxLines = Int.MAX_VALUE
                switchHumanTyping?.isChecked = false

                val density = resources.displayMetrics.density
                val maxAllowedHeight = (120 * density).toInt()

                scrollSuggestionText?.scrollTo(0, 0)
                scrollSuggestionText?.layoutParams = scrollSuggestionText?.layoutParams?.apply {
                    height = ViewGroup.LayoutParams.WRAP_CONTENT
                }
                scrollSuggestionText?.post {
                    val contentHeight = tvSuggestionText?.measuredHeight ?: 0
                    if (contentHeight > maxAllowedHeight) {
                        scrollSuggestionText?.layoutParams = scrollSuggestionText?.layoutParams?.apply {
                            height = maxAllowedHeight
                        }
                    }
                }

                val (screenWidth, screenHeight) = getRealScreenDimensions()

                val marginPx = (14 * density).toInt()
                val cardWidth = screenWidth - (marginPx * 2)
                val clearancePx = (10 * density).toInt()

                // Detect actual top boundary of the soft keyboard
                val detectedKbTop = getKeyboardTopOnScreen(screenHeight)

                // Fallback keyboard top estimation if window info unavailable
                val kbTop = detectedKbTop ?: if (lastKnownKeyboardHeight > (screenHeight * 0.18)) {
                    screenHeight - lastKnownKeyboardHeight
                } else {
                    (screenHeight * 0.62).toInt() // Standard keyboard top ~62% of screen
                }

                val keyboardHeight = screenHeight - kbTop
                val bottomMargin = keyboardHeight + clearancePx

                val windowType = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY

                val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH

                val lp = WindowManager.LayoutParams(
                    cardWidth,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    windowType,
                    flags,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    // Exact same position right above the soft keyboard across ALL apps!
                    gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                    y = bottomMargin
                }

                if (!isFloatingViewAdded) {
                    windowManager?.addView(fView, lp)
                    isFloatingViewAdded = true
                } else {
                    windowManager?.updateViewLayout(fView, lp)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error displaying floating suggestion", e)
            }
        }
    }

    private fun hideFloatingSuggestion() {
        mainHandler.post {
            if (isFloatingViewAdded && floatingView != null) {
                try {
                    windowManager?.removeView(floatingView)
                } catch (e: Exception) {
                    Log.e(TAG, "Error removing floating view", e)
                }
                isFloatingViewAdded = false
            }
        }
    }

    private fun showFloatingBox(
        sourceNode: AccessibilityNodeInfo?,
        fullText: String,
        startIndex: Int,
        endIndex: Int
    ) {
        lastActiveEditableNode = sourceNode
        mainHandler.post {
            try {
                if (floatingBoxView == null) {
                    initFloatingBoxView()
                }
                val boxView = floatingBoxView ?: return@post

                etBoxInput?.setText("")
                switchBoxHumanTyping?.isChecked = false

                val boxDensity = resources.displayMetrics.density
                val (screenWidth, screenHeight) = getRealScreenDimensions()
                val marginPx = (14 * boxDensity).toInt()
                val cardWidth = screenWidth - (marginPx * 2)

                val detectedKbTop = getKeyboardTopOnScreen(screenHeight)
                val kbTop = detectedKbTop ?: if (lastKnownKeyboardHeight > (screenHeight * 0.18)) {
                    screenHeight - lastKnownKeyboardHeight
                } else {
                    (screenHeight * 0.60).toInt()
                }
                val bottomMargin = (screenHeight - kbTop) + (10 * boxDensity).toInt()

                val windowType = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                // Needs focusable for EditText input
                val flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH

                val lp = WindowManager.LayoutParams(
                    cardWidth,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    windowType,
                    flags,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                    y = bottomMargin
                }

                if (!isFloatingBoxAdded) {
                    windowManager?.addView(boxView, lp)
                    isFloatingBoxAdded = true
                } else {
                    windowManager?.updateViewLayout(boxView, lp)
                }

                etBoxInput?.postDelayed({
                    etBoxInput?.requestFocus()
                }, 100L)

                // Store target parameters for expansion on Send
                pendingVariableTargetNode = sourceNode
                pendingVariableFullText = fullText
                pendingVariableStart = startIndex
                pendingVariableEnd = endIndex

            } catch (e: Exception) {
                Log.e(TAG, "Error displaying floating box", e)
            }
        }
    }

    private fun initFloatingBoxView() {
        if (floatingBoxView != null) return
        val contextWrapper = ContextThemeWrapper(this, R.style.Theme_TextExpander)
        val inflater = LayoutInflater.from(contextWrapper)
        val view = inflater.inflate(R.layout.layout_floating_box, null)
        floatingBoxView = view

        etBoxInput = view.findViewById(R.id.etBoxInput)
        switchBoxHumanTyping = view.findViewById(R.id.switchBoxHumanTyping)

        val layoutHuman = view.findViewById<View>(R.id.layoutBoxHumanTyping)
        layoutHuman?.setOnClickListener {
            switchBoxHumanTyping?.let { sw -> sw.isChecked = !sw.isChecked }
        }

        view.findViewById<View>(R.id.btnBoxClose)?.setOnClickListener {
            hideFloatingBox()
        }

        view.findViewById<View>(R.id.btnBoxSend)?.setOnClickListener {
            val textToInsert = etBoxInput?.text?.toString().orEmpty()
            val isHuman = switchBoxHumanTyping?.isChecked == true
            hideFloatingBox()

            if (textToInsert.isNotBlank()) {
                val node = pendingVariableTargetNode ?: lastActiveEditableNode
                val fText = pendingVariableFullText
                val start = pendingVariableStart
                val end = pendingVariableEnd

                val autoResolved = com.textexpander.app.util.VariableHelper.resolveAutoVariables(textToInsert)
                val userVars = com.textexpander.app.util.VariableHelper.extractUserInputVariables(autoResolved)

                if (userVars.isNotEmpty()) {
                    showVariableDialog(node, fText, start, end, autoResolved, userVars, isHuman)
                } else {
                    if (isHuman) {
                        performHumanTypingExpansion(node, fText, start, end, autoResolved)
                    } else {
                        performDirectExpansion(node, fText, start, end, autoResolved, fromPopup = true)
                    }
                }
            }
        }

        view.setOnTouchListener { _, touchEvent ->
            if (touchEvent.action == MotionEvent.ACTION_OUTSIDE) {
                hideFloatingBox()
                true
            } else {
                false
            }
        }
    }

    private fun hideFloatingBox() {
        mainHandler.post {
            if (isFloatingBoxAdded && floatingBoxView != null) {
                try {
                    windowManager?.removeView(floatingBoxView)
                } catch (e: Exception) {
                    Log.e(TAG, "Error removing floating box", e)
                }
                isFloatingBoxAdded = false
            }
        }
    }

    private fun showVariableDialog(
        sourceNode: AccessibilityNodeInfo?,
        fullText: String,
        startIndex: Int,
        endIndex: Int,
        template: String,
        variables: List<String>,
        isHumanTyping: Boolean
    ) {
        pendingVariableTargetNode = sourceNode
        pendingVariableFullText = fullText
        pendingVariableStart = startIndex
        pendingVariableEnd = endIndex
        pendingVariableTemplate = template

        mainHandler.post {
            try {
                if (floatingVarView == null) {
                    initFloatingVarView()
                }
                val varView = floatingVarView ?: return@post

                switchVarHumanTyping?.isChecked = isHumanTyping
                layoutVarContainer?.removeAllViews()

                val density = resources.displayMetrics.density
                val fieldMap = mutableMapOf<String, EditText>()

                for (v in variables) {
                    val fieldLayout = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        val mb = (6 * density).toInt()
                        setPadding(0, 0, 0, mb)
                    }

                    val label = TextView(this).apply {
                        text = v.replace("{", "").replace("}", "").uppercase(Locale.ROOT)
                        textSize = 10.5f
                        setTextColor(ContextCompat.getColor(this@TextExpanderAccessibilityService, R.color.text_tertiary))
                        typeface = Typeface.DEFAULT_BOLD
                    }
                    fieldLayout.addView(label)

                    val input = EditText(this).apply {
                        setBackgroundResource(R.drawable.bg_noir_card)
                        val p = (8 * density).toInt()
                        setPadding(p, p, p, p)
                        setTextColor(ContextCompat.getColor(this@TextExpanderAccessibilityService, R.color.text_primary))
                        setHintTextColor(ContextCompat.getColor(this@TextExpanderAccessibilityService, R.color.text_tertiary))
                        hint = "Enter ${v.replace("{", "").replace("}", "")}"
                        textSize = 13f
                        isSingleLine = true
                    }
                    fieldLayout.addView(input)
                    layoutVarContainer?.addView(fieldLayout)
                    fieldMap[v] = input
                }

                varView.findViewById<View>(R.id.btnVarInsert)?.setOnClickListener {
                    val values = mutableMapOf<String, String>()
                    for ((k, et) in fieldMap) {
                        values[k] = et.text?.toString().orEmpty()
                    }
                    val replaced = com.textexpander.app.util.VariableHelper.replaceUserVariables(pendingVariableTemplate, values)
                    val isHuman = switchVarHumanTyping?.isChecked == true
                    hideVariableDialog()

                    val node = pendingVariableTargetNode ?: lastActiveEditableNode
                    val fText = pendingVariableFullText
                    val start = pendingVariableStart
                    val end = pendingVariableEnd

                    if (isHuman) {
                        performHumanTypingExpansion(node, fText, start, end, replaced)
                    } else {
                        performDirectExpansion(node, fText, start, end, replaced, fromPopup = true)
                    }
                }

                val boxDensity = resources.displayMetrics.density
                val (screenWidth, screenHeight) = getRealScreenDimensions()
                val marginPx = (14 * boxDensity).toInt()
                val cardWidth = screenWidth - (marginPx * 2)

                val detectedKbTop = getKeyboardTopOnScreen(screenHeight)
                val kbTop = detectedKbTop ?: if (lastKnownKeyboardHeight > (screenHeight * 0.18)) {
                    screenHeight - lastKnownKeyboardHeight
                } else {
                    (screenHeight * 0.60).toInt()
                }
                val bottomMargin = (screenHeight - kbTop) + (10 * boxDensity).toInt()

                val windowType = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                val flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH

                val lp = WindowManager.LayoutParams(
                    cardWidth,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    windowType,
                    flags,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                    y = bottomMargin
                }

                if (!isFloatingVarAdded) {
                    windowManager?.addView(varView, lp)
                    isFloatingVarAdded = true
                } else {
                    windowManager?.updateViewLayout(varView, lp)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error displaying variable dialog", e)
            }
        }
    }

    private fun initFloatingVarView() {
        if (floatingVarView != null) return
        val contextWrapper = ContextThemeWrapper(this, R.style.Theme_TextExpander)
        val inflater = LayoutInflater.from(contextWrapper)
        val view = inflater.inflate(R.layout.layout_floating_variables, null)
        floatingVarView = view

        layoutVarContainer = view.findViewById(R.id.layoutVariableFieldsContainer)
        switchVarHumanTyping = view.findViewById(R.id.switchVarHumanTyping)

        val layoutHuman = view.findViewById<View>(R.id.layoutVarHumanTyping)
        layoutHuman?.setOnClickListener {
            switchVarHumanTyping?.let { sw -> sw.isChecked = !sw.isChecked }
        }

        view.findViewById<View>(R.id.btnVarClose)?.setOnClickListener {
            hideVariableDialog()
        }

        view.setOnTouchListener { _, touchEvent ->
            if (touchEvent.action == MotionEvent.ACTION_OUTSIDE) {
                hideVariableDialog()
                true
            } else {
                false
            }
        }
    }

    private fun hideVariableDialog() {
        mainHandler.post {
            if (isFloatingVarAdded && floatingVarView != null) {
                try {
                    windowManager?.removeView(floatingVarView)
                } catch (e: Exception) {
                    Log.e(TAG, "Error removing variable dialog", e)
                }
                isFloatingVarAdded = false
            }
        }
    }

    private fun onFloatingSuggestionTapped() {
        val expansion = currentExpansionText
        val key = currentMatchedKey
        val targetStart = currentMatchStart
        val targetEnd = currentMatchEnd
        val isHumanTyping = switchHumanTyping?.isChecked == true

        hideFloatingSuggestion()
        if (expansion.isEmpty() || key.isEmpty()) return

        val settings = SettingsPreferences.get(this)
        val activeNode = lastActiveEditableNode ?: findActiveEditableNode(null, settings)
        try {
            activeNode?.refresh()
        } catch (_: Exception) {}
        val fullText = activeNode?.text?.toString() ?: ""
        var start = -1
        var end = -1

        if (targetEnd in 1..fullText.length && targetStart in 0 until targetEnd) {
            val sub = fullText.substring(targetStart, targetEnd)
            if (sub.equals(key, ignoreCase = true)) {
                start = targetStart
                end = targetEnd
            }
        }

        if (start == -1 && fullText.isNotEmpty()) {
            var cursor = if (activeNode != null) getCursorPosition(activeNode, null, fullText) else fullText.length
            if (cursor <= 0 || cursor > fullText.length) cursor = fullText.length
            val checkStart = (cursor - key.length).coerceAtLeast(0)
            if (fullText.substring(checkStart, cursor).equals(key, ignoreCase = true)) {
                start = checkStart
                end = cursor
            } else {
                val idx = fullText.lastIndexOf(key, ignoreCase = true)
                if (idx != -1) {
                    start = idx
                    end = idx + key.length
                }
            }
        }

        val (actualStart, actualEnd) = if (start != -1 && end != -1) {
            val selEnd = activeNode?.textSelectionEnd ?: -1
            val effectiveEnd = if (selEnd > end && activeNode?.textSelectionStart == end) selEnd else end
            Pair(start, effectiveEnd)
        } else {
            val safeLen = fullText.length
            val fallbackStart = (safeLen - key.length).coerceAtLeast(0)
            Pair(fallbackStart, safeLen)
        }

        // First resolve auto variables: {date}, {time}, {day}, {greeting}, {uuid}
        val autoResolved = com.textexpander.app.util.VariableHelper.resolveAutoVariables(expansion)
        val userVars = com.textexpander.app.util.VariableHelper.extractUserInputVariables(autoResolved)

        if (userVars.isNotEmpty()) {
            showVariableDialog(activeNode, fullText, actualStart, actualEnd, autoResolved, userVars, isHumanTyping)
            return
        }

        if (isHumanTyping) {
            performHumanTypingExpansion(activeNode, fullText, actualStart, actualEnd, autoResolved)
        } else {
            performDirectExpansion(activeNode, fullText, actualStart, actualEnd, autoResolved, fromPopup = true)
        }
    }

    private fun performHumanTypingExpansion(
        targetNode: AccessibilityNodeInfo?,
        fullText: String,
        startIndex: Int,
        endIndex: Int,
        expandedText: String
    ) {
        if (!isCurrentlyExpanding.compareAndSet(false, true)) {
            return
        }

        try {
            val settings = SettingsPreferences.get(this)
            val node = targetNode ?: lastActiveEditableNode ?: findActiveEditableNode(null, settings)

            val safeStart = startIndex.coerceIn(0, fullText.length)
            val safeEnd = endIndex.coerceIn(safeStart, fullText.length)

            val cursorMarker = "{{cursor}}"
            val baseExpansion: String
            val cursorOffset: Int
            if (expandedText.contains(cursorMarker)) {
                cursorOffset = expandedText.indexOf(cursorMarker)
                baseExpansion = expandedText.replace(cursorMarker, "")
            } else {
                cursorOffset = expandedText.length
                baseExpansion = expandedText
            }

            val finalExpansion = baseExpansion
            val savedKeystrokes = (finalExpansion.length - (safeEnd - safeStart)).coerceAtLeast(0)

            val prefix = if (fullText.isNotEmpty() && safeStart <= fullText.length) fullText.substring(0, safeStart) else ""
            val suffix = if (fullText.isNotEmpty() && safeEnd <= fullText.length) fullText.substring(safeEnd) else ""

            if (finalExpansion.isEmpty()) {
                isCurrentlyExpanding.set(false)
                return
            }

            if (node == null) {
                isCurrentlyExpanding.set(false)
                return
            }

            triggerHapticFeedback()

            var charIndex = 1
            val typingRunnable = object : Runnable {
                override fun run() {
                    try {
                        node.refresh()
                        val currentTypedText = finalExpansion.substring(0, charIndex)
                        val currentFullText = prefix + currentTypedText + suffix
                        val currentCursorPos = safeStart + charIndex

                        val setBundle = Bundle().apply {
                            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, currentFullText)
                        }
                        val actionSuccess = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, setBundle)

                        if (!actionSuccess && charIndex == 1) {
                            // If ACTION_SET_TEXT is not supported by target field, fallback gracefully
                            isCurrentlyExpanding.set(false)
                            performDirectExpansion(node, fullText, safeStart, safeEnd, expandedText, fromPopup = true)
                            return
                        }

                        val cursorArgs = Bundle().apply {
                            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, currentCursorPos)
                            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, currentCursorPos)
                        }
                        node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, cursorArgs)

                        if (charIndex < finalExpansion.length) {
                            charIndex++
                            mainHandler.postDelayed(this, 100L)
                        } else {
                            // Typing complete: set cursor to {{cursor}} target offset if specified
                            if (cursorOffset != finalExpansion.length) {
                                val finalCursor = safeStart + cursorOffset
                                val finalCursorArgs = Bundle().apply {
                                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, finalCursor)
                                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, finalCursor)
                                }
                                node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, finalCursorArgs)
                            }

                            val now = System.currentTimeMillis()
                            if (now - lastExpandedTimestamp > 1200L || lastExpandedKey != finalExpansion) {
                                settings.recordSavedKeystrokes(savedKeystrokes)
                                lastExpandedTimestamp = now
                                lastExpandedKey = finalExpansion
                            }

                            mainHandler.postDelayed({
                                isCurrentlyExpanding.set(false)
                            }, EXPANSION_COOLDOWN_MS)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error during human typing loop", e)
                        isCurrentlyExpanding.set(false)
                    }
                }
            }

            mainHandler.post(typingRunnable)

        } catch (e: Exception) {
            Log.e(TAG, "Error initiating human typing", e)
            isCurrentlyExpanding.set(false)
        }
    }

    private fun performDirectExpansion(
        targetNode: AccessibilityNodeInfo?,
        fullText: String,
        startIndex: Int,
        endIndex: Int,
        expandedText: String,
        fromPopup: Boolean = false
    ) {
        val autoResolved = com.textexpander.app.util.VariableHelper.resolveAutoVariables(expandedText)
        val userVars = com.textexpander.app.util.VariableHelper.extractUserInputVariables(autoResolved)
        if (userVars.isNotEmpty()) {
            showVariableDialog(targetNode, fullText, startIndex, endIndex, autoResolved, userVars, false)
            return
        }

        if (!isCurrentlyExpanding.compareAndSet(false, true)) {
            return
        }

        try {
            val settings = SettingsPreferences.get(this)
            val node = targetNode ?: lastActiveEditableNode ?: findActiveEditableNode(null, settings)

            val safeStart = startIndex.coerceIn(0, fullText.length)
            val safeEnd = endIndex.coerceIn(safeStart, fullText.length)

            val cursorMarker = "{{cursor}}"
            val baseExpansion: String
            val cursorOffset: Int
            if (autoResolved.contains(cursorMarker)) {
                cursorOffset = autoResolved.indexOf(cursorMarker)
                baseExpansion = autoResolved.replace(cursorMarker, "")
            } else {
                cursorOffset = autoResolved.length
                baseExpansion = autoResolved
            }

            val finalExpansion = baseExpansion
            val savedKeystrokes = (finalExpansion.length - (safeEnd - safeStart)).coerceAtLeast(0)

            val newText = if (fullText.isNotEmpty()) {
                fullText.replaceRange(safeStart, safeEnd, finalExpansion)
            } else {
                finalExpansion
            }

            var success = false

            if (node != null) {
                // Primary Method: ACTION_SET_TEXT (Instant, direct, supported across most apps)
                val setBundle = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newText)
                }
                success = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, setBundle)

                if (success) {
                    val now = System.currentTimeMillis()
                    if (now - lastExpandedTimestamp > 1200L || lastExpandedKey != finalExpansion) {
                        settings.recordSavedKeystrokes(savedKeystrokes)
                        lastExpandedTimestamp = now
                        lastExpandedKey = finalExpansion
                    }

                    // Move cursor to end of inserted expansion or cursor placeholder position
                    val newCursor = safeStart + cursorOffset
                    val cursorArgs = Bundle().apply {
                        putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, newCursor)
                        putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, newCursor)
                    }
                    node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, cursorArgs)
                }
            }

            // Trigger guaranteed single vibration at the exact moment of text input
            triggerHapticFeedback()

            // Secondary Fallback: Clipboard Paste (Vital for Chrome, WebViews, and custom editors)
            if (!success && node != null) {
                clipboardManager.setPrimaryClip(ClipData.newPlainText("TextExpander", finalExpansion))
                val selectionArgs = Bundle().apply {
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, safeStart)
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, safeEnd)
                }
                node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selectionArgs)
                mainHandler.postDelayed({
                    try {
                        val pasted = node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
                        if (pasted) {
                            val now = System.currentTimeMillis()
                            if (now - lastExpandedTimestamp > 1200L || lastExpandedKey != finalExpansion) {
                                settings.recordSavedKeystrokes(savedKeystrokes)
                                lastExpandedTimestamp = now
                                lastExpandedKey = finalExpansion
                            }
                        }
                    } catch (_: Exception) {}
                }, 25L)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error performing expansion", e)
        } finally {
            mainHandler.postDelayed({
                isCurrentlyExpanding.set(false)
            }, EXPANSION_COOLDOWN_MS)
        }
    }

    private fun findActiveEditableNode(
        event: AccessibilityEvent? = null,
        settings: SettingsPreferences? = null
    ): AccessibilityNodeInfo? {
        val eventSource = event?.source
        if (eventSource != null) {
            if (eventSource.isEditable || eventSource.isFocused || eventSource.isFocusable) {
                return eventSource
            }
            val inputChild = eventSource.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (inputChild != null) {
                return inputChild
            }
            return eventSource
        }

        val focused = findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focused != null) {
            return focused
        }

        val rootNode = rootInActiveWindow
        if (rootNode != null) {
            val windowFocused = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (windowFocused != null) {
                return windowFocused
            }
        }

        // Also check windows for focused input (vital for multi-window and Chromium)
        try {
            val windowList = windows
            if (!windowList.isNullOrEmpty()) {
                for (win in windowList) {
                    val root = win.root
                    if (root != null) {
                        val wf = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                        if (wf != null) {
                            return wf
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        return lastActiveEditableNode
    }

    private fun getCursorPosition(
        node: AccessibilityNodeInfo,
        event: AccessibilityEvent?,
        fullText: String
    ): Int {
        val selectionEnd = node.textSelectionEnd
        if (selectionEnd in 1..fullText.length) {
            return selectionEnd
        }

        if (event != null) {
            val eventIndex = event.fromIndex + event.addedCount
            if (eventIndex in 1..fullText.length) {
                return eventIndex
            }
        }

        return fullText.length
    }

    override fun onInterrupt() {
        isCurrentlyExpanding.set(false)
        hideFloatingSuggestion()
    }

    private fun triggerHapticFeedback() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val effect = VibrationEffect.createOneShot(55, VibrationEffect.DEFAULT_AMPLITUDE)
                    vibrator.vibrate(effect)
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(55)
                }
            }
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        activeServiceInstance = null
        isServiceRunning = false
        pendingDetectionRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingDetectionRunnable = null
        try {
            clipboardManager.removePrimaryClipChangedListener(clipChangedListener)
        } catch (_: Exception) {}
        hideFloatingSuggestion()
        try {
            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                lastActiveEditableNode?.recycle()
            }
            lastActiveEditableNode = null
        } catch (_: Exception) {}
        mainHandler.removeCallbacksAndMessages(null)
        serviceScope.cancel()
    }
}
