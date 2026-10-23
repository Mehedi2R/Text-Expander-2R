package com.textexpander.app.data

import android.content.Context
import android.content.SharedPreferences

class SettingsPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Detection
    var enableDetection: Boolean
        get() = prefs.getBoolean(KEY_ENABLE_DETECTION, true)
        set(value) = prefs.edit().putBoolean(KEY_ENABLE_DETECTION, value).apply()

    var useOptimizedDetection: Boolean
        get() = prefs.getBoolean(KEY_OPTIMIZED_DETECTION, true)
        set(value) = prefs.edit().putBoolean(KEY_OPTIMIZED_DETECTION, value).apply()

    var detectInAllApps: Boolean
        get() = prefs.getBoolean(KEY_DETECT_IN_ALL_APPS, true)
        set(value) = prefs.edit().putBoolean(KEY_DETECT_IN_ALL_APPS, value).apply()

    var detectInAllLanguages: Boolean
        get() = prefs.getBoolean(KEY_DETECT_ALL_LANGUAGES, true)
        set(value) = prefs.edit().putBoolean(KEY_DETECT_ALL_LANGUAGES, value).apply()

    var matchFromBeginning: Boolean
        get() = prefs.getBoolean(KEY_MATCH_FROM_BEGINNING, true)
        set(value) = prefs.edit().putBoolean(KEY_MATCH_FROM_BEGINNING, value).apply()

    var enableForegroundService: Boolean
        get() = prefs.getBoolean(KEY_ENABLE_FOREGROUND_SERVICE, true)
        set(value) = prefs.edit().putBoolean(KEY_ENABLE_FOREGROUND_SERVICE, value).apply()

    // Suggestion
    var enableSuggestionOverlay: Boolean
        get() = prefs.getBoolean(KEY_ENABLE_SUGGESTION_OVERLAY, true)
        set(value) = prefs.edit().putBoolean(KEY_ENABLE_SUGGESTION_OVERLAY, value).apply()

    // Clipboard
    var enableClipboard: Boolean
        get() = prefs.getBoolean(KEY_ENABLE_CLIPBOARD, true)
        set(value) = prefs.edit().putBoolean(KEY_ENABLE_CLIPBOARD, value).apply()

    // Statistics
    var enableStatistics: Boolean
        get() = prefs.getBoolean(KEY_ENABLE_STATISTICS, true)
        set(value) = prefs.edit().putBoolean(KEY_ENABLE_STATISTICS, value).apply()

    var shareStatistics: Boolean
        get() = prefs.getBoolean(KEY_SHARE_STATISTICS, true)
        set(value) = prefs.edit().putBoolean(KEY_SHARE_STATISTICS, value).apply()

    var savedKeystrokes: Long
        get() = prefs.getLong(KEY_SAVED_KEYSTROKES, 0L)
        set(value) = prefs.edit().putLong(KEY_SAVED_KEYSTROKES, value).apply()

    var hardwareKeystrokes: Long
        get() = prefs.getLong(KEY_HARDWARE_KEYSTROKES, 0L)
        set(value) = prefs.edit().putLong(KEY_HARDWARE_KEYSTROKES, value).apply()

    // Interaction
    var showClipboardAccess: Boolean
        get() = prefs.getBoolean(KEY_SHOW_CLIPBOARD_ACCESS, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_CLIPBOARD_ACCESS, value).apply()

    var enableHapticFeedback: Boolean
        get() = prefs.getBoolean(KEY_ENABLE_HAPTIC_FEEDBACK, true)
        set(value) = prefs.edit().putBoolean(KEY_ENABLE_HAPTIC_FEEDBACK, value).apply()

    data class CustomFolderItem(
        val name: String,
        val path: String,
        val uriString: String = "",
        val createdAt: Long = System.currentTimeMillis()
    )

    fun getCustomFolders(): List<CustomFolderItem> {
        val json = prefs.getString(KEY_CUSTOM_FOLDERS, "[]") ?: "[]"
        val list = mutableListOf<CustomFolderItem>()
        try {
            val array = org.json.JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    CustomFolderItem(
                        name = obj.getString("name"),
                        path = obj.getString("path"),
                        uriString = obj.optString("uriString", ""),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    fun saveCustomFolder(folder: CustomFolderItem) {
        val current = getCustomFolders().toMutableList()
        current.removeAll { it.name.equals(folder.name, ignoreCase = true) }
        current.add(folder)
        saveCustomFoldersList(current)
    }

    fun deleteCustomFolder(name: String) {
        val current = getCustomFolders().toMutableList()
        current.removeAll { it.name.equals(name, ignoreCase = true) }
        saveCustomFoldersList(current)
    }

    private fun saveCustomFoldersList(list: List<CustomFolderItem>) {
        val array = org.json.JSONArray()
        for (item in list) {
            val obj = org.json.JSONObject().apply {
                put("name", item.name)
                put("path", item.path)
                put("uriString", item.uriString)
                put("createdAt", item.createdAt)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_CUSTOM_FOLDERS, array.toString()).apply()
    }

    var backupFolderUri: String
        get() = prefs.getString(KEY_BACKUP_FOLDER_URI, "") ?: ""
        set(value) = prefs.edit().putString(KEY_BACKUP_FOLDER_URI, value).apply()

    var backupFolderPath: String
        get() {
            val p = prefs.getString(KEY_BACKUP_FOLDER_PATH, "")
            return if (!p.isNullOrBlank()) p else "/Documents/TextExpander"
        }
        set(value) = prefs.edit().putString(KEY_BACKUP_FOLDER_PATH, value).apply()

    var lastBackupTimestamp: Long
        get() = prefs.getLong(KEY_LAST_BACKUP_TIMESTAMP, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_BACKUP_TIMESTAMP, value).apply()

    fun recordSavedKeystrokes(keystrokes: Int) {
        if (!enableStatistics) return
        val current = savedKeystrokes
        val updated = current + keystrokes.coerceAtLeast(0)
        prefs.edit().putLong(KEY_SAVED_KEYSTROKES, updated).apply()
    }

    fun recordHardwareKeystrokes(count: Int = 1) {
        if (!enableStatistics) return
        val current = hardwareKeystrokes
        val updated = current + count.coerceAtLeast(0)
        prefs.edit().putLong(KEY_HARDWARE_KEYSTROKES, updated).apply()
    }

    fun resetSavedKeystrokes() {
        prefs.edit().putLong(KEY_SAVED_KEYSTROKES, 0L).apply()
        prefs.edit().putLong(KEY_HARDWARE_KEYSTROKES, 0L).apply()
    }

    fun resetHardwareKeystrokes() {
        prefs.edit().putLong(KEY_HARDWARE_KEYSTROKES, 0L).apply()
    }

    companion object {
        private const val PREFS_NAME = "text_expander_settings_prefs"

        private const val KEY_ENABLE_DETECTION = "key_enable_detection"
        private const val KEY_OPTIMIZED_DETECTION = "key_optimized_detection"
        private const val KEY_DETECT_IN_ALL_APPS = "key_detect_in_all_apps"
        private const val KEY_DETECT_ALL_LANGUAGES = "key_detect_all_languages"
        private const val KEY_MATCH_FROM_BEGINNING = "key_match_from_beginning"
        private const val KEY_ENABLE_FOREGROUND_SERVICE = "key_enable_foreground_service"

        private const val KEY_ENABLE_SUGGESTION_OVERLAY = "key_enable_suggestion_overlay"
        private const val KEY_ENABLE_CLIPBOARD = "key_enable_clipboard"

        private const val KEY_ENABLE_STATISTICS = "key_enable_statistics"
        private const val KEY_SHARE_STATISTICS = "key_share_statistics"
        private const val KEY_SAVED_KEYSTROKES = "key_saved_keystrokes"
        private const val KEY_HARDWARE_KEYSTROKES = "key_hardware_keystrokes"

        private const val KEY_SHOW_CLIPBOARD_ACCESS = "key_show_clipboard_access"
        private const val KEY_ENABLE_HAPTIC_FEEDBACK = "key_enable_haptic_feedback"

        private const val KEY_CUSTOM_FOLDERS = "key_custom_folders"

        private const val KEY_BACKUP_FOLDER_URI = "key_backup_folder_uri"
        private const val KEY_BACKUP_FOLDER_PATH = "key_backup_folder_path"
        private const val KEY_LAST_BACKUP_TIMESTAMP = "key_last_backup_timestamp"

        @Volatile
        private var instance: SettingsPreferences? = null

        fun get(context: Context): SettingsPreferences {
            return instance ?: synchronized(this) {
                instance ?: SettingsPreferences(context).also { instance = it }
            }
        }
    }
}
