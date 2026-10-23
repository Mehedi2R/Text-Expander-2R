package com.textexpander.app.data;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "clipboard_history")
public class ClipboardEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "content")
    public String content;

    @ColumnInfo(name = "copy_count")
    public int copyCount;

    @ColumnInfo(name = "copied_at")
    public long copiedAt;

    public ClipboardEntity(long id, String content, int copyCount, long copiedAt) {
        this.id = id;
        this.content = content;
        this.copyCount = copyCount;
        this.copiedAt = copiedAt;
    }
}
