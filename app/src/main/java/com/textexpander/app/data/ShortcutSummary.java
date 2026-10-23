package com.textexpander.app.data;

import androidx.room.ColumnInfo;

public class ShortcutSummary {
    @ColumnInfo(name = "id")
    public long id;

    @ColumnInfo(name = "shortcut_key")
    public String shortcutKey;

    @ColumnInfo(name = "snippet")
    public String snippet;

    @ColumnInfo(name = "char_count")
    public int charCount;

    @ColumnInfo(name = "text_byte_size")
    public long textByteSize;

    @ColumnInfo(name = "updated_at")
    public long updatedAt;

    @ColumnInfo(name = "folder_name")
    public String folderName;

    public ShortcutSummary(long id, String shortcutKey, String snippet, int charCount, long textByteSize, long updatedAt, String folderName) {
        this.id = id;
        this.shortcutKey = shortcutKey;
        this.snippet = snippet;
        this.charCount = charCount;
        this.textByteSize = textByteSize;
        this.updatedAt = updatedAt;
        this.folderName = folderName != null ? folderName : "";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ShortcutSummary that = (ShortcutSummary) o;
        return id == that.id &&
                charCount == that.charCount &&
                textByteSize == that.textByteSize &&
                updatedAt == that.updatedAt &&
                java.util.Objects.equals(shortcutKey, that.shortcutKey) &&
                java.util.Objects.equals(snippet, that.snippet);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(id, shortcutKey, snippet, charCount, textByteSize, updatedAt);
    }
}
