package com.seniortribe.app.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.seniortribe.app.models.Post;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Bộ nhớ đệm SQLite cho Bảng tin (F16, T05): lưu bản sao các bài đăng đã tải để xem lại khi mất mạng.
 *
 * Chỉ lưu bài đăng công khai trong mạng lưới. Không lưu thông tin riêng của người cao tuổi
 * (needs_private), tin nhắn chat hay hồ sơ tài khoản xuống máy.
 */
public class AppDatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "seniortribe_offline.db";
    // v2: đổi cột theo schema posts mới (tổ chức, người đăng, thông tin hoạt động), bỏ ảnh và lượt quan tâm
    private static final int DATABASE_VERSION = 2;

    /** Giữ tối đa chừng này bài mới nhất để file đệm luôn nhỏ. */
    public static final int MAX_CACHED_POSTS = 100;

    public static final String TABLE_POSTS = "cached_posts";
    public static final String COL_ID = "id";
    public static final String COL_SERVER_ID = "server_id";
    public static final String COL_TITLE = "title";
    public static final String COL_CONTENT = "content";
    public static final String COL_ORG_ID = "org_id";
    public static final String COL_ORG_NAME = "org_name";
    public static final String COL_AUTHOR_NAME = "author_name";
    public static final String COL_ORG_VERIFIED = "org_verified";
    public static final String COL_EVENT_TIME = "event_time";
    public static final String COL_EVENT_PLACE = "event_place";
    public static final String COL_CREATED_AT = "created_at";
    public static final String COL_CACHED_AT = "cached_at";

    private static AppDatabaseHelper instance;

    public static synchronized AppDatabaseHelper getInstance(Context context) {
        if (instance == null) {
            instance = new AppDatabaseHelper(context.getApplicationContext());
        }
        return instance;
    }

    private AppDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_POSTS + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_SERVER_ID + " TEXT UNIQUE NOT NULL, "
                + COL_TITLE + " TEXT, "
                + COL_CONTENT + " TEXT, "
                + COL_ORG_ID + " TEXT, "
                + COL_ORG_NAME + " TEXT, "
                + COL_AUTHOR_NAME + " TEXT, "
                + COL_ORG_VERIFIED + " INTEGER DEFAULT 0, "
                + COL_EVENT_TIME + " TEXT, "
                + COL_EVENT_PLACE + " TEXT, "
                + COL_CREATED_AT + " INTEGER, "
                + COL_CACHED_AT + " INTEGER)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Chỉ là bộ nhớ đệm, tải lại được từ Firestore nên xóa đi tạo lại cho đơn giản
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_POSTS);
        onCreate(db);
    }

    /**
     * Thay toàn bộ bộ đệm bằng danh sách vừa tải từ Firestore (đã lọc bài ẩn).
     * Thay hết thay vì cộng dồn để bài đã bị ẩn hoặc xóa trên máy chủ không còn hiện khi offline.
     */
    public void replaceAllPosts(List<Post> posts) {
        SQLiteDatabase db = getWritableDatabase();
        long now = System.currentTimeMillis();
        db.beginTransaction();
        try {
            db.delete(TABLE_POSTS, null, null);
            int count = 0;
            for (Post p : posts) {
                if (p.getId() == null || p.isHidden()) continue;
                if (count++ >= MAX_CACHED_POSTS) break;
                ContentValues values = new ContentValues();
                values.put(COL_SERVER_ID, p.getId());
                values.put(COL_TITLE, p.getTitle());
                values.put(COL_CONTENT, p.getContent());
                values.put(COL_ORG_ID, p.getOrgId());
                values.put(COL_ORG_NAME, p.getOrgName());
                values.put(COL_AUTHOR_NAME, p.getAuthorName());
                values.put(COL_ORG_VERIFIED, p.isOrgVerified() ? 1 : 0);
                values.put(COL_EVENT_TIME, p.getEventTime());
                values.put(COL_EVENT_PLACE, p.getEventPlace());
                values.put(COL_CREATED_AT, p.getCreatedAtMillis());
                values.put(COL_CACHED_AT, now);
                db.insertWithOnConflict(TABLE_POSTS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /** Các bài đã lưu, mới nhất trước. Mỗi bài được đánh dấu {@link Post#isCached()}. */
    public List<Post> getAllCachedPosts() {
        List<Post> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.query(TABLE_POSTS, null, null, null, null, null,
                COL_CREATED_AT + " DESC", String.valueOf(MAX_CACHED_POSTS))) {
            while (cursor.moveToNext()) {
                Post post = new Post();
                post.setId(cursor.getString(cursor.getColumnIndexOrThrow(COL_SERVER_ID)));
                post.setTitle(cursor.getString(cursor.getColumnIndexOrThrow(COL_TITLE)));
                post.setContent(cursor.getString(cursor.getColumnIndexOrThrow(COL_CONTENT)));
                post.setOrgId(cursor.getString(cursor.getColumnIndexOrThrow(COL_ORG_ID)));
                post.setOrgName(cursor.getString(cursor.getColumnIndexOrThrow(COL_ORG_NAME)));
                post.setAuthorName(cursor.getString(cursor.getColumnIndexOrThrow(COL_AUTHOR_NAME)));
                post.setOrgVerified(cursor.getInt(cursor.getColumnIndexOrThrow(COL_ORG_VERIFIED)) == 1);
                post.setEventTime(cursor.getString(cursor.getColumnIndexOrThrow(COL_EVENT_TIME)));
                post.setEventPlace(cursor.getString(cursor.getColumnIndexOrThrow(COL_EVENT_PLACE)));
                post.setCreatedAt(new Date(cursor.getLong(cursor.getColumnIndexOrThrow(COL_CREATED_AT))));
                post.setCached(true);
                list.add(post);
            }
        }
        return list;
    }

    /** Xóa bộ đệm, ví dụ khi đăng xuất. */
    public void clearCache() {
        getWritableDatabase().delete(TABLE_POSTS, null, null);
    }
}
