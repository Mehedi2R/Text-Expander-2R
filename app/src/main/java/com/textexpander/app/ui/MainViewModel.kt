package com.textexpander.app.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import com.textexpander.app.TextExpanderApp
import com.textexpander.app.data.ClipboardEntity
import com.textexpander.app.data.ClipboardRepository
import com.textexpander.app.data.ShortcutEntity
import com.textexpander.app.data.ShortcutSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = (application as TextExpanderApp).database
    private val shortcutDao = db.shortcutDao()
    private val clipboardDao = db.clipboardDao()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val shortcuts: StateFlow<List<ShortcutSummary>> = _searchQuery
        .flatMapLatest { query ->
            if (query.isBlank()) {
                shortcutDao.allSummaries.asFlow()
            } else {
                shortcutDao.searchSummaries(query.trim()).asFlow()
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val clipboardHistory: StateFlow<List<ClipboardEntity>> = clipboardDao.allClipboardLiveData.asFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    suspend fun getFullShortcut(id: Long): ShortcutEntity? {
        return withContext(Dispatchers.IO) {
            shortcutDao.getShortcutById(id)
        }
    }

    fun saveShortcut(
        id: Long = 0,
        key: String,
        expandedText: String,
        folderName: String = "",
        onComplete: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val cleanKey = key.trim()
                if (cleanKey.isEmpty()) {
                    onComplete(false, "Shortcut key cannot be empty.")
                    return@launch
                }
                if (cleanKey.contains(" ") || cleanKey.contains("\n") || cleanKey.contains("\t")) {
                    onComplete(false, "Shortcut key cannot contain spaces or whitespace.")
                    return@launch
                }
                if (expandedText.isEmpty()) {
                    onComplete(false, "Expanded text cannot be empty.")
                    return@launch
                }

                val byteSize = expandedText.toByteArray(Charsets.UTF_8).size.toLong()

                withContext(Dispatchers.IO) {
                    val existing = shortcutDao.getShortcutByKey(cleanKey)
                    if (existing != null && (id == 0L || existing.id != id)) {
                        withContext(Dispatchers.Main) {
                            onComplete(false, "A shortcut with key \"$cleanKey\" already exists.")
                        }
                        return@withContext
                    }

                    val entity = ShortcutEntity(
                        if (id > 0) id else 0,
                        cleanKey,
                        expandedText,
                        byteSize,
                        System.currentTimeMillis(),
                        folderName.trim()
                    )
                    shortcutDao.insertShortcut(entity)

                    withContext(Dispatchers.Main) {
                        onComplete(true, null)
                    }
                }
            } catch (e: Exception) {
                onComplete(false, e.localizedMessage ?: "Failed to save shortcut.")
            }
        }
    }

    fun restoreShortcutsBatch(shortcuts: List<ShortcutEntity>, onComplete: (Int) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val deduplicatedMap = LinkedHashMap<String, ShortcutEntity>()
            for (s in shortcuts) {
                val cleanKey = s.shortcutKey.trim()
                if (cleanKey.isNotEmpty()) {
                    s.shortcutKey = cleanKey
                    deduplicatedMap[cleanKey.lowercase(java.util.Locale.ROOT)] = s
                }
            }
            var count = 0
            for (shortcut in deduplicatedMap.values) {
                try {
                    val cleanKey = shortcut.shortcutKey.trim()
                    val existing = shortcutDao.getShortcutByKey(cleanKey)
                    val textBytes = shortcut.expandedText.toByteArray(Charsets.UTF_8).size.toLong()
                    if (existing != null) {
                        existing.expandedText = shortcut.expandedText
                        existing.textByteSize = textBytes
                        existing.updatedAt = maxOf(shortcut.updatedAt, System.currentTimeMillis())
                        if (shortcut.folderName.isNotBlank()) {
                            existing.folderName = shortcut.folderName
                        }
                        shortcutDao.updateShortcut(existing)
                    } else {
                        shortcut.id = 0
                        shortcut.textByteSize = textBytes
                        shortcutDao.insertShortcut(shortcut)
                    }
                    count++
                } catch (e: Exception) {
                    Log.e("MainViewModel", "Error restoring shortcut: ${shortcut.shortcutKey}", e)
                }
            }
            withContext(Dispatchers.Main) {
                onComplete(count)
            }
        }
    }

    fun deleteShortcut(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            shortcutDao.deleteById(id)
        }
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            ClipboardRepository.cleanExistingDuplicates(getApplication())
        }
    }

    fun recordCopiedText(text: String) {
        val clean = text.trim()
        if (clean.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            ClipboardRepository.recordCopiedText(getApplication(), clean)
        }
    }

    fun deleteClipboardItem(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            clipboardDao.deleteById(id)
        }
    }

    fun clearClipboardHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            clipboardDao.clearAll()
        }
    }
}
