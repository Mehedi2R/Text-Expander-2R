package com.textexpander.app.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

@Dao
public interface ClipboardDao {

    @Query("SELECT * FROM clipboard_history ORDER BY copied_at DESC")
    LiveData<List<ClipboardEntity>> getAllClipboardLiveData();

    @Query("SELECT * FROM clipboard_history WHERE content = :content LIMIT 1")
    ClipboardEntity getByContent(String content);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(ClipboardEntity item);

    @Update
    void update(ClipboardEntity item);

    @Query("DELETE FROM clipboard_history WHERE id = :id")
    void deleteById(long id);

    @Query("DELETE FROM clipboard_history")
    void clearAll();

    @Query("UPDATE clipboard_history SET copy_count = 1 WHERE copy_count != 1")
    void resetAllCountsToOne();

    @Query("SELECT * FROM clipboard_history ORDER BY copied_at DESC LIMIT :limit")
    List<ClipboardEntity> getRecentClips(int limit);

    @Query("SELECT * FROM clipboard_history ORDER BY copied_at DESC")
    List<ClipboardEntity> getAllSync();

    @Query("DELETE FROM clipboard_history WHERE id NOT IN (SELECT MAX(id) FROM clipboard_history GROUP BY content)")
    void deleteExactDuplicates();
}
