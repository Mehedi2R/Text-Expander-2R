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
public final class ClipboardDao_Impl implements ClipboardDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<ClipboardEntity> __insertionAdapterOfClipboardEntity;

  private final EntityDeletionOrUpdateAdapter<ClipboardEntity> __updateAdapterOfClipboardEntity;

  private final SharedSQLiteStatement __preparedStmtOfDeleteById;

  private final SharedSQLiteStatement __preparedStmtOfClearAll;

  private final SharedSQLiteStatement __preparedStmtOfResetAllCountsToOne;

  private final SharedSQLiteStatement __preparedStmtOfDeleteExactDuplicates;

  public ClipboardDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfClipboardEntity = new EntityInsertionAdapter<ClipboardEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `clipboard_history` (`id`,`content`,`copy_count`,`copied_at`) VALUES (nullif(?, 0),?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          final ClipboardEntity entity) {
        statement.bindLong(1, entity.id);
        if (entity.content == null) {
          statement.bindNull(2);
        } else {
          statement.bindString(2, entity.content);
        }
        statement.bindLong(3, entity.copyCount);
        statement.bindLong(4, entity.copiedAt);
      }
    };
    this.__updateAdapterOfClipboardEntity = new EntityDeletionOrUpdateAdapter<ClipboardEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `clipboard_history` SET `id` = ?,`content` = ?,`copy_count` = ?,`copied_at` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          final ClipboardEntity entity) {
        statement.bindLong(1, entity.id);
        if (entity.content == null) {
          statement.bindNull(2);
        } else {
          statement.bindString(2, entity.content);
        }
        statement.bindLong(3, entity.copyCount);
        statement.bindLong(4, entity.copiedAt);
        statement.bindLong(5, entity.id);
      }
    };
    this.__preparedStmtOfDeleteById = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM clipboard_history WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfClearAll = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM clipboard_history";
        return _query;
      }
    };
    this.__preparedStmtOfResetAllCountsToOne = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE clipboard_history SET copy_count = 1 WHERE copy_count != 1";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteExactDuplicates = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM clipboard_history WHERE id NOT IN (SELECT MAX(id) FROM clipboard_history GROUP BY content)";
        return _query;
      }
    };
  }

  @Override
  public long insert(final ClipboardEntity item) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      final long _result = __insertionAdapterOfClipboardEntity.insertAndReturnId(item);
      __db.setTransactionSuccessful();
      return _result;
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public void update(final ClipboardEntity item) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      __updateAdapterOfClipboardEntity.handle(item);
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
  public void clearAll() {
    __db.assertNotSuspendingTransaction();
    final SupportSQLiteStatement _stmt = __preparedStmtOfClearAll.acquire();
    try {
      __db.beginTransaction();
      try {
        _stmt.executeUpdateDelete();
        __db.setTransactionSuccessful();
      } finally {
        __db.endTransaction();
      }
    } finally {
      __preparedStmtOfClearAll.release(_stmt);
    }
  }

  @Override
  public void resetAllCountsToOne() {
    __db.assertNotSuspendingTransaction();
    final SupportSQLiteStatement _stmt = __preparedStmtOfResetAllCountsToOne.acquire();
    try {
      __db.beginTransaction();
      try {
        _stmt.executeUpdateDelete();
        __db.setTransactionSuccessful();
      } finally {
        __db.endTransaction();
      }
    } finally {
      __preparedStmtOfResetAllCountsToOne.release(_stmt);
    }
  }

  @Override
  public void deleteExactDuplicates() {
    __db.assertNotSuspendingTransaction();
    final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteExactDuplicates.acquire();
    try {
      __db.beginTransaction();
      try {
        _stmt.executeUpdateDelete();
        __db.setTransactionSuccessful();
      } finally {
        __db.endTransaction();
      }
    } finally {
      __preparedStmtOfDeleteExactDuplicates.release(_stmt);
    }
  }

  @Override
  public LiveData<List<ClipboardEntity>> getAllClipboardLiveData() {
    final String _sql = "SELECT * FROM clipboard_history ORDER BY copied_at DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return __db.getInvalidationTracker().createLiveData(new String[] {"clipboard_history"}, false, new Callable<List<ClipboardEntity>>() {
      @Override
      @Nullable
      public List<ClipboardEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfContent = CursorUtil.getColumnIndexOrThrow(_cursor, "content");
          final int _cursorIndexOfCopyCount = CursorUtil.getColumnIndexOrThrow(_cursor, "copy_count");
          final int _cursorIndexOfCopiedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "copied_at");
          final List<ClipboardEntity> _result = new ArrayList<ClipboardEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ClipboardEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpContent;
            if (_cursor.isNull(_cursorIndexOfContent)) {
              _tmpContent = null;
            } else {
              _tmpContent = _cursor.getString(_cursorIndexOfContent);
            }
            final int _tmpCopyCount;
            _tmpCopyCount = _cursor.getInt(_cursorIndexOfCopyCount);
            final long _tmpCopiedAt;
            _tmpCopiedAt = _cursor.getLong(_cursorIndexOfCopiedAt);
            _item = new ClipboardEntity(_tmpId,_tmpContent,_tmpCopyCount,_tmpCopiedAt);
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
  public ClipboardEntity getByContent(final String content) {
    final String _sql = "SELECT * FROM clipboard_history WHERE content = ? LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    if (content == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, content);
    }
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
      final int _cursorIndexOfContent = CursorUtil.getColumnIndexOrThrow(_cursor, "content");
      final int _cursorIndexOfCopyCount = CursorUtil.getColumnIndexOrThrow(_cursor, "copy_count");
      final int _cursorIndexOfCopiedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "copied_at");
      final ClipboardEntity _result;
      if (_cursor.moveToFirst()) {
        final long _tmpId;
        _tmpId = _cursor.getLong(_cursorIndexOfId);
        final String _tmpContent;
        if (_cursor.isNull(_cursorIndexOfContent)) {
          _tmpContent = null;
        } else {
          _tmpContent = _cursor.getString(_cursorIndexOfContent);
        }
        final int _tmpCopyCount;
        _tmpCopyCount = _cursor.getInt(_cursorIndexOfCopyCount);
        final long _tmpCopiedAt;
        _tmpCopiedAt = _cursor.getLong(_cursorIndexOfCopiedAt);
        _result = new ClipboardEntity(_tmpId,_tmpContent,_tmpCopyCount,_tmpCopiedAt);
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
  public List<ClipboardEntity> getRecentClips(final int limit) {
    final String _sql = "SELECT * FROM clipboard_history ORDER BY copied_at DESC LIMIT ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, limit);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
      final int _cursorIndexOfContent = CursorUtil.getColumnIndexOrThrow(_cursor, "content");
      final int _cursorIndexOfCopyCount = CursorUtil.getColumnIndexOrThrow(_cursor, "copy_count");
      final int _cursorIndexOfCopiedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "copied_at");
      final List<ClipboardEntity> _result = new ArrayList<ClipboardEntity>(_cursor.getCount());
      while (_cursor.moveToNext()) {
        final ClipboardEntity _item;
        final long _tmpId;
        _tmpId = _cursor.getLong(_cursorIndexOfId);
        final String _tmpContent;
        if (_cursor.isNull(_cursorIndexOfContent)) {
          _tmpContent = null;
        } else {
          _tmpContent = _cursor.getString(_cursorIndexOfContent);
        }
        final int _tmpCopyCount;
        _tmpCopyCount = _cursor.getInt(_cursorIndexOfCopyCount);
        final long _tmpCopiedAt;
        _tmpCopiedAt = _cursor.getLong(_cursorIndexOfCopiedAt);
        _item = new ClipboardEntity(_tmpId,_tmpContent,_tmpCopyCount,_tmpCopiedAt);
        _result.add(_item);
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public List<ClipboardEntity> getAllSync() {
    final String _sql = "SELECT * FROM clipboard_history ORDER BY copied_at DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
      final int _cursorIndexOfContent = CursorUtil.getColumnIndexOrThrow(_cursor, "content");
      final int _cursorIndexOfCopyCount = CursorUtil.getColumnIndexOrThrow(_cursor, "copy_count");
      final int _cursorIndexOfCopiedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "copied_at");
      final List<ClipboardEntity> _result = new ArrayList<ClipboardEntity>(_cursor.getCount());
      while (_cursor.moveToNext()) {
        final ClipboardEntity _item;
        final long _tmpId;
        _tmpId = _cursor.getLong(_cursorIndexOfId);
        final String _tmpContent;
        if (_cursor.isNull(_cursorIndexOfContent)) {
          _tmpContent = null;
        } else {
          _tmpContent = _cursor.getString(_cursorIndexOfContent);
        }
        final int _tmpCopyCount;
        _tmpCopyCount = _cursor.getInt(_cursorIndexOfCopyCount);
        final long _tmpCopiedAt;
        _tmpCopiedAt = _cursor.getLong(_cursorIndexOfCopiedAt);
        _item = new ClipboardEntity(_tmpId,_tmpContent,_tmpCopyCount,_tmpCopiedAt);
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
