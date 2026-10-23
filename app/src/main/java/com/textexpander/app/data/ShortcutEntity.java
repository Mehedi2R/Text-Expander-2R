package com.textexpander.app.data;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "shortcuts",
    indices = {@Index(value = {"shortcut_key"}, unique = true)}
)
public class ShortcutEntity {
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    public long id;

    @ColumnInfo(name = "shortcut_key")
    public String shortcutKey;

    @ColumnInfo(name = "expanded_text")
    public String expandedText;

    @ColumnInfo(name = "text_byte_size")
    public long textByteSize;

    @ColumnInfo(name = "updated_at")
    public long updatedAt;

    @ColumnInfo(name = "folder_name", defaultValue = "")
    public String folderName = "";

    public ShortcutEntity(long id, String shortcutKey, String expandedText, long textByteSize, long updatedAt, String folderName) {
        this.id = id;
        this.shortcutKey = shortcutKey;
        this.expandedText = expandedText;
        this.textByteSize = textByteSize;
        this.updatedAt = updatedAt;
        this.folderName = folderName != null ? folderName : "";
    }
}
