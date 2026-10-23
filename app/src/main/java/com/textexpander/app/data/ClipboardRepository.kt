package com.textexpander.app.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * Repository responsible for clipboard history operations and intelligent deduplication.
 * Prevents duplicate cards, overlapping forwarded message fragments, and partial text selections.
 */
object ClipboardRepository {

    private const val TAG = "ClipboardRepo"
    private const val RECENT_TIME_WINDOW_MS = 180000L // 3 minutes

    /**
     * Determines whether `sub` is a partial fragment/duplicate of `parent`.
     */
    private fun isSubDuplicate(sub: String, parent: String): Boolean {
        if (sub.isEmpty() || parent.isEmpty()) return false
        if (sub == parent) return true
        if (!parent.contains(sub)) return false

        // Long text or multi-line messages (like forwarded telegram/whatsapp chats)
        if (sub.length >= 15 || sub.contains("\n") || parent.contains("\n")) {
            return true
        }

        // Multi-word phrases
        if (sub.trim().contains(" ") || parent.trim().contains(" ")) {
            return true
        }

        // Prefix or suffix match with high similarity (>= 60% overlap)
        if ((parent.startsWith(sub) || parent.endsWith(sub)) && sub.length >= 4 &&
            (sub.length.toDouble() / parent.length.toDouble()) >= 0.6
        ) {
            return true
        }

        return false
    }

    /**
     * Records copied text with strict deduplication:
     * - Merges exact duplicates
     * - Upgrades recent partial selections to full text
     * - Ignores fragmented copies when full text was already copied
     */
    suspend fun recordCopiedText(
        context: Context,
        rawText: String,
        expansionToIgnore: String = ""
    ) = withContext(Dispatchers.IO) {
        val clean = rawText.trim()
        if (clean.isBlank()) return@withContext
        if (expansionToIgnore.isNotBlank() && clean == expansionToIgnore.trim()) return@withContext

        try {
            val db = AppDatabase.getInstance(context)
            val dao = db.clipboardDao()
            val now = System.currentTimeMillis()

            // 1. Exact match check
            val existingExact = dao.getByContent(clean)
            if (existingExact != null) {
                existingExact.copiedAt = now
                existingExact.copyCount = 1
                dao.update(existingExact)
                dao.deleteExactDuplicates()
                return@withContext
            }

            // 2. Overlap & substring check among recent items
            val recentClips = dao.getRecentClips(20)
            var handled = false
            for (recent in recentClips) {
                val timeDiff = abs(now - recent.copiedAt)
                if (timeDiff <= RECENT_TIME_WINDOW_MS) {
                    val recentContent = recent.content.trim()

                    if (clean == recentContent) {
                        recent.copiedAt = now
                        recent.copyCount = 1
                        dao.update(recent)
                        handled = true
                        break
                    } else if (clean.contains(recentContent) && isSubDuplicate(recentContent, clean)) {
                        // Incoming text is the fuller/complete version (e.g. forwarded message with header)
                        recent.content = clean
                        recent.copiedAt = now
                        recent.copyCount = 1
                        dao.update(recent)
                        handled = true
                        Log.d(TAG, "Upgraded recent fragment to fuller text")
                        break
                    } else if (recentContent.contains(clean) && isSubDuplicate(clean, recentContent)) {
                        // Fuller version is already in clipboard history! Refresh its timestamp without duplicating.
                        recent.copiedAt = now
                        recent.copyCount = 1
                        dao.update(recent)
                        handled = true
                        Log.d(TAG, "Ignored fragment; fuller text already present")
                        break
                    }
                }
            }

            if (!handled) {
                val entity = ClipboardEntity(0, clean, 1, now)
                dao.insert(entity)
            }

            dao.deleteExactDuplicates()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to record clipboard: ${e.message}", e)
        }
    }

    /**
     * Cleans up any existing duplicates, overlapping fragments, and obsolete entries.
     */
    suspend fun cleanExistingDuplicates(context: Context) = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getInstance(context)
            val dao = db.clipboardDao()

            dao.resetAllCountsToOne()
            dao.deleteExactDuplicates()

            // Clean historical overlapping fragments within 3 minutes of each other among recent entries
            val recentItems = dao.getRecentClips(100)
            val toDelete = mutableSetOf<Long>()

            for (i in recentItems.indices) {
                val itemA = recentItems[i]
                if (toDelete.contains(itemA.id)) continue

                for (j in i + 1 until recentItems.size) {
                    val itemB = recentItems[j]
                    if (toDelete.contains(itemB.id)) continue

                    val timeDiff = abs(itemA.copiedAt - itemB.copiedAt)
                    if (timeDiff <= RECENT_TIME_WINDOW_MS) {
                        val contentA = itemA.content.trim()
                        val contentB = itemB.content.trim()

                        if (contentA == contentB) {
                            toDelete.add(itemB.id)
                        } else if (contentA.contains(contentB) && isSubDuplicate(contentB, contentA)) {
                            toDelete.add(itemB.id)
                        } else if (contentB.contains(contentA) && isSubDuplicate(contentA, contentB)) {
                            toDelete.add(itemA.id)
                            break
                        }
                    }
                }
            }

            for (delId in toDelete) {
                dao.deleteById(delId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed cleaning duplicates: ${e.message}", e)
        }
    }
}
