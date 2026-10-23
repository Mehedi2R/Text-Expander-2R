package com.textexpander.app.data;

import android.database.Cursor;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class ShortcutDao_Impl implements ShortcutDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<ShortcutEntity> __insertionAdapterOfShortcutEntity;

  private final EntityDeletionOrUpdateAdapter<ShortcutEntity> __deletionAdapterOfShortcutEntity;

  private final EntityDeletionOrUpdateAdapter<ShortcutEntity> __updateAdapterOfShortcutEntity;

  private final SharedSQLiteStatement __preparedStmtOfDeleteById;

  public ShortcutDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfShortcutEntity = new EntityInsertionAdapter<ShortcutEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `shortcuts` (`id`,`shortcut_key`,`expanded_text`,`text_byte_size`,`updated_at`) VALUES (nullif(?, 0),?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          final ShortcutEntity entity) {
        statement.bindLong(1, entity.id);
        if (entity.shortcutKey == null) {
          statement.bindNull(2);
        } else {
          statement.bindString(2, entity.shortcutKey);
        }
        if (entity.expandedText == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.expandedText);
        }
        statement.bindLong(4, entity.textByteSize);
        statement.bindLong(5, entity.updatedAt);
      }
    };
    this.__deletionAdapterOfShortcutEntity = new EntityDeletionOrUpdateAdapter<ShortcutEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `shortcuts` WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          final ShortcutEntity entity) {
        statement.bindLong(1, entity.id);
      }
    };
    this.__updateAdapterOfShortcutEntity = new EntityDeletionOrUpdateAdapter<ShortcutEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `shortcuts` SET `id` = ?,`shortcut_key` = ?,`expanded_text` = ?,`text_byte_size` = ?,`updated_at` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          final ShortcutEntity entity) {
        statement.bindLong(1, entity.id);
        if (entity.shortcutKey == null) {
          statement.bindNull(2);
        } else {
          statement.bindString(2, entity.shortcutKey);
        }
        if (entity.expandedText == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.expandedText);
        }
        statement.bindLong(4, entity.textByteSize);
        statement.bindLong(5, entity.updatedAt);
        statement.bindLong(6, entity.id);
      }
    };
    this.__preparedStmtOfDeleteById = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM shortcuts WHERE id = ?";
        return _query;
      }
    };
  }

  @Override
  public long insertShortcut(final ShortcutEntity shortcut) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      final long _result = __insertionAdapterOfShortcutEntity.insertAndReturnId(shortcut);
      __db.setTransactionSuccessful();
      return _result;
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public void deleteShortcut(final ShortcutEntity shortcut) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      __deletionAdapterOfShortcutEntity.handle(shortcut);
      __db.setTransactionSuccessful();
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public void updateShortcut(final ShortcutEntity shortcut) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      __updateAdapterOfShortcutEntity.handle(shortcut);
      __db.setTransactionSuccessful();
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public void deleteById(final long id) {
    __db.assertNotSuspendingTransaction();
    final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteById.acquire();
    int _argIndex = 1;
    _stmt.bindLong(_argIndex, id);
    try {
      __db.beginTransaction();
      try {
        _stmt.executeUpdateDelete();
        __db.setTransactionSuccessful();
      } finally {
        __db.endTransaction();
      }
    } finally {
      __preparedStmtOfDeleteById.release(_stmt);
    }
  }

  @Override
  public LiveData<List<ShortcutSummary>> getAllSummaries() {
    final String _sql = "SELECT id, shortcut_key, SUBSTR(expanded_text, 1, 200) AS snippet, LENGTH(expanded_text) AS char_count, text_byte_size, updated_at FROM shortcuts ORDER BY updated_at DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return __db.getInvalidationTracker().createLiveData(new String[] {"shortcuts"}, false, new Callable<List<ShortcutSummary>>() {
      @Override
      @Nullable
      public List<ShortcutSummary> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = 0;
          final int _cursorIndexOfShortcutKey = 1;
          final int _cursorIndexOfSnippet = 2;
          final int _cursorIndexOfCharCount = 3;
          final int _cursorIndexOfTextByteSize = 4;
          final int _cursorIndexOfUpdatedAt = 5;
          final List<ShortcutSummary> _result = new ArrayList<ShortcutSummary>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ShortcutSummary _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpShortcutKey;
            if (_cursor.isNull(_cursorIndexOfShortcutKey)) {
              _tmpShortcutKey = null;
            } else {
              _tmpShortcutKey = _cursor.getString(_cursorIndexOfShortcutKey);
            }
            final String _tmpSnippet;
            if (_cursor.isNull(_cursorIndexOfSnippet)) {
              _tmpSnippet = null;
            } else {
              _tmpSnippet = _cursor.getString(_cursorIndexOfSnippet);
            }
            final int _tmpCharCount;
            _tmpCharCount = _cursor.getInt(_cursorIndexOfCharCount);
            final long _tmpTextByteSize;
            _tmpTextByteSize = _cursor.getLong(_cursorIndexOfTextByteSize);
            final long _tmpUpdatedAt;
            _tmpUpdatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt);
            _item = new ShortcutSummary(_tmpId,_tmpShortcutKey,_tmpSnippet,_tmpCharCount,_tmpTextByteSize,_tmpUpdatedAt);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public LiveData<List<ShortcutSummary>> searchSummaries(final String query) {
    final String _sql = "SELECT id, shortcut_key, SUBSTR(expanded_text, 1, 200) AS snippet, LENGTH(expanded_text) AS char_count, text_byte_size, updated_at FROM shortcuts WHERE shortcut_key LIKE '%' || ? || '%' OR expanded_text LIKE '%' || ? || '%' ORDER BY updated_at DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    if (query == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, query);
    }
    _argIndex = 2;
    if (query == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, query);
    }
    return __db.getInvalidationTracker().createLiveData(new String[] {"shortcuts"}, false, new Callable<List<ShortcutSummary>>() {
      @Override
      @Nullable
      public List<ShortcutSummary> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = 0;
          final int _cursorIndexOfShortcutKey = 1;
          final int _cursorIndexOfSnippet = 2;
          final int _cursorIndexOfCharCount = 3;
          final int _cursorIndexOfTextByteSize = 4;
          final int _cursorIndexOfUpdatedAt = 5;
          final List<ShortcutSummary> _result = new ArrayList<ShortcutSummary>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ShortcutSummary _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpShortcutKey;
            if (_cursor.isNull(_cursorIndexOfShortcutKey)) {
              _tmpShortcutKey = null;
            } else {
              _tmpShortcutKey = _cursor.getString(_cursorIndexOfShortcutKey);
            }
            final String _tmpSnippet;
            if (_cursor.isNull(_cursorIndexOfSnippet)) {
              _tmpSnippet = null;
            } else {
              _tmpSnippet = _cursor.getString(_cursorIndexOfSnippet);
            }
            final int _tmpCharCount;
            _tmpCharCount = _cursor.getInt(_cursorIndexOfCharCount);
            final long _tmpTextByteSize;
            _tmpTextByteSize = _cursor.getLong(_cursorIndexOfTextByteSize);
            final long _tmpUpdatedAt;
            _tmpUpdatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt);
            _item = new ShortcutSummary(_tmpId,_tmpShortcutKey,_tmpSnippet,_tmpCharCount,_tmpTextByteSize,_tmpUpdatedAt);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public ShortcutEntity getShortcutByKey(final String key) {
    final String _sql = "SELECT * FROM shortcuts WHERE shortcut_key = ? COLLATE NOCASE LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    if (key == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, key);
    }
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
      final int _cursorIndexOfShortcutKey = CursorUtil.getColumnIndexOrThrow(_cursor, "shortcut_key");
      final int _cursorIndexOfExpandedText = CursorUtil.getColumnIndexOrThrow(_cursor, "expanded_text");
      final int _cursorIndexOfTextByteSize = CursorUtil.getColumnIndexOrThrow(_cursor, "text_byte_size");
      final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updated_at");
      final ShortcutEntity _result;
      if (_cursor.moveToFirst()) {
        final long _tmpId;
        _tmpId = _cursor.getLong(_cursorIndexOfId);
        final String _tmpShortcutKey;
        if (_cursor.isNull(_cursorIndexOfShortcutKey)) {
          _tmpShortcutKey = null;
        } else {
          _tmpShortcutKey = _cursor.getString(_cursorIndexOfShortcutKey);
        }
        final String _tmpExpandedText;
        if (_cursor.isNull(_cursorIndexOfExpandedText)) {
          _tmpExpandedText = null;
        } else {
          _tmpExpandedText = _cursor.getString(_cursorIndexOfExpandedText);
        }
        final long _tmpTextByteSize;
        _tmpTextByteSize = _cursor.getLong(_cursorIndexOfTextByteSize);
        final long _tmpUpdatedAt;
        _tmpUpdatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt);
        _result = new ShortcutEntity(_tmpId,_tmpShortcutKey,_tmpExpandedText,_tmpTextByteSize,_tmpUpdatedAt);
      } else {
        _result = null;
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public ShortcutEntity getShortcutById(final long id) {
    final String _sql = "SELECT * FROM shortcuts WHERE id = ? LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, id);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
      final int _cursorIndexOfShortcutKey = CursorUtil.getColumnIndexOrThrow(_cursor, "shortcut_key");
      final int _cursorIndexOfExpandedText = CursorUtil.getColumnIndexOrThrow(_cursor, "expanded_text");
      final int _cursorIndexOfTextByteSize = CursorUtil.getColumnIndexOrThrow(_cursor, "text_byte_size");
      final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updated_at");
      final ShortcutEntity _result;
      if (_cursor.moveToFirst()) {
        final long _tmpId;
        _tmpId = _cursor.getLong(_cursorIndexOfId);
        final String _tmpShortcutKey;
        if (_cursor.isNull(_cursorIndexOfShortcutKey)) {
          _tmpShortcutKey = null;
        } else {
          _tmpShortcutKey = _cursor.getString(_cursorIndexOfShortcutKey);
        }
        final String _tmpExpandedText;
        if (_cursor.isNull(_cursorIndexOfExpandedText)) {
          _tmpExpandedText = null;
        } else {
          _tmpExpandedText = _cursor.getString(_cursorIndexOfExpandedText);
        }
        final long _tmpTextByteSize;
        _tmpTextByteSize = _cursor.getLong(_cursorIndexOfTextByteSize);
        final long _tmpUpdatedAt;
        _tmpUpdatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt);
        _result = new ShortcutEntity(_tmpId,_tmpShortcutKey,_tmpExpandedText,_tmpTextByteSize,_tmpUpdatedAt);
      } else {
        _result = null;
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public LiveData<List<ShortcutEntity>> getAllShortcutsLiveData() {
    final String _sql = "SELECT * FROM shortcuts";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return __db.getInvalidationTracker().createLiveData(new String[] {"shortcuts"}, false, new Callable<List<ShortcutEntity>>() {
      @Override
      @Nullable
      public List<ShortcutEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfShortcutKey = CursorUtil.getColumnIndexOrThrow(_cursor, "shortcut_key");
          final int _cursorIndexOfExpandedText = CursorUtil.getColumnIndexOrThrow(_cursor, "expanded_text");
          final int _cursorIndexOfTextByteSize = CursorUtil.getColumnIndexOrThrow(_cursor, "text_byte_size");
          final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updated_at");
          final List<ShortcutEntity> _result = new ArrayList<ShortcutEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ShortcutEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpShortcutKey;
            if (_cursor.isNull(_cursorIndexOfShortcutKey)) {
              _tmpShortcutKey = null;
            } else {
              _tmpShortcutKey = _cursor.getString(_cursorIndexOfShortcutKey);
            }
            final String _tmpExpandedText;
            if (_cursor.isNull(_cursorIndexOfExpandedText)) {
              _tmpExpandedText = null;
            } else {
              _tmpExpandedText = _cursor.getString(_cursorIndexOfExpandedText);
            }
            final long _tmpTextByteSize;
            _tmpTextByteSize = _cursor.getLong(_cursorIndexOfTextByteSize);
            final long _tmpUpdatedAt;
            _tmpUpdatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt);
            _item = new ShortcutEntity(_tmpId,_tmpShortcutKey,_tmpExpandedText,_tmpTextByteSize,_tmpUpdatedAt);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public List<ShortcutEntity> getAllShortcutsSync() {
    final String _sql = "SELECT * FROM shortcuts";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
      final int _cursorIndexOfShortcutKey = CursorUtil.getColumnIndexOrThrow(_cursor, "shortcut_key");
      final int _cursorIndexOfExpandedText = CursorUtil.getColumnIndexOrThrow(_cursor, "expanded_text");
      final int _cursorIndexOfTextByteSize = CursorUtil.getColumnIndexOrThrow(_cursor, "text_byte_size");
      final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updated_at");
      final List<ShortcutEntity> _result = new ArrayList<ShortcutEntity>(_cursor.getCount());
      while (_cursor.moveToNext()) {
        final ShortcutEntity _item;
        final long _tmpId;
        _tmpId = _cursor.getLong(_cursorIndexOfId);
        final String _tmpShortcutKey;
        if (_cursor.isNull(_cursorIndexOfShortcutKey)) {
          _tmpShortcutKey = null;
        } else {
          _tmpShortcutKey = _cursor.getString(_cursorIndexOfShortcutKey);
        }
        final String _tmpExpandedText;
        if (_cursor.isNull(_cursorIndexOfExpandedText)) {
          _tmpExpandedText = null;
        } else {
          _tmpExpandedText = _cursor.getString(_cursorIndexOfExpandedText);
        }
        final long _tmpTextByteSize;
        _tmpTextByteSize = _cursor.getLong(_cursorIndexOfTextByteSize);
        final long _tmpUpdatedAt;
        _tmpUpdatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt);
        _item = new ShortcutEntity(_tmpId,_tmpShortcutKey,_tmpExpandedText,_tmpTextByteSize,_tmpUpdatedAt);
        _result.add(_item);
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
