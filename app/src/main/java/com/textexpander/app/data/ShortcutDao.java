package com.textexpander.app.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

@Dao
public interface ShortcutDao {

    @Query("SELECT id, shortcut_key, SUBSTR(expanded_text, 1, 200) AS snippet, LENGTH(expanded_text) AS char_count, text_byte_size, updated_at, folder_name FROM shortcuts ORDER BY updated_at DESC")
    LiveData<List<ShortcutSummary>> getAllSummaries();

    @Query("SELECT id, shortcut_key, SUBSTR(expanded_text, 1, 200) AS snippet, LENGTH(expanded_text) AS char_count, text_byte_size, updated_at, folder_name FROM shortcuts WHERE shortcut_key LIKE '%' || :query || '%' OR expanded_text LIKE '%' || :query || '%' ORDER BY updated_at DESC")
    LiveData<List<ShortcutSummary>> searchSummaries(String query);

    @Query("SELECT * FROM shortcuts WHERE shortcut_key = :key COLLATE NOCASE LIMIT 1")
    ShortcutEntity getShortcutByKey(String key);

    @Query("SELECT * FROM shortcuts WHERE id = :id LIMIT 1")
    ShortcutEntity getShortcutById(long id);

    @Query("SELECT * FROM shortcuts")
    LiveData<List<ShortcutEntity>> getAllShortcutsLiveData();

    @Query("SELECT * FROM shortcuts")
    List<ShortcutEntity> getAllShortcutsSync();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertShortcut(ShortcutEntity shortcut);

    @Update
    void updateShortcut(ShortcutEntity shortcut);

    @Query("DELETE FROM shortcuts WHERE id = :id")
    void deleteById(long id);
}
