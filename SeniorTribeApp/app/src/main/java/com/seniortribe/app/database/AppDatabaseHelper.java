package com.seniortribe.app.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.seniortribe.app.models.Post;

import java.util.ArrayList;
import java.util.List;

/**
 * Quản lý SQLite Offline Caching theo tiêu chí T05 & Chương 5.2/5.3 của Đồ án SeniorTribe.
 * Lưu trữ bộ nhớ đệm bài viết cộng đồng để người dùng đọc được khi mất kết nối mạng.
 */
public class AppDatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "seniortribe_offline.db";
    private static final int DATABASE_VERSION = 1;

    // Table: cached_posts
    public static final String TABLE_POSTS = "cached_posts";
    public static final String COL_ID = "id";
    public static final String COL_SERVER_ID = "server_id";
    public static final String COL_TITLE = "title";
    public static final String COL_CONTENT = "content";
    public static final String COL_AUTHOR_ORG = "author_org";
    public static final String COL_IMAGE_URL = "image_url";
    public static final String COL_LIKES = "likes_count";
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
        String createTable = "CREATE TABLE " + TABLE_POSTS + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_SERVER_ID + " TEXT UNIQUE, "
                + COL_TITLE + " TEXT, "
                + COL_CONTENT + " TEXT, "
                + COL_AUTHOR_ORG + " TEXT, "
                + COL_IMAGE_URL + " TEXT, "
                + COL_LIKES + " INTEGER DEFAULT 0, "
                + COL_CREATED_AT + " INTEGER, "
                + COL_CACHED_AT + " INTEGER)";
        db.execSQL(createTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_POSTS);
        onCreate(db);
    }

    /**
     * Lưu danh sách bài viết từ Firestore vào SQLite cache
     */
    public void savePosts(List<Post> posts) {
        if (posts == null || posts.isEmpty()) return;
        SQLiteDatabase db = this.getWritableDatabase();
        db.beginTransaction();
        try {
            for (Post p : posts) {
                ContentValues values = new ContentValues();
                values.put(COL_SERVER_ID, p.getId());
                values.put(COL_TITLE, p.getTitle());
                values.put(COL_CONTENT, p.getContent());
                values.put(COL_AUTHOR_ORG, p.getAuthorOrg());
                values.put(COL_IMAGE_URL, p.getImageUrl());
                values.put(COL_LIKES, p.getLikesCount());
                values.put(COL_CREATED_AT, p.getCreatedAt());
                values.put(COL_CACHED_AT, System.currentTimeMillis());

                db.insertWithOnConflict(TABLE_POSTS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Lấy danh sách bài viết đã lưu đệm khi offline
     */
    public List<Post> getAllCachedPosts() {
        List<Post> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_POSTS, null, null, null, null, null, COL_CREATED_AT + " DESC");

        if (cursor != null && cursor.moveToFirst()) {
            do {
                Post post = new Post();
                post.setId(cursor.getString(cursor.getColumnIndexOrThrow(COL_SERVER_ID)));
                post.setTitle(cursor.getString(cursor.getColumnIndexOrThrow(COL_TITLE)));
                post.setContent(cursor.getString(cursor.getColumnIndexOrThrow(COL_CONTENT)));
                post.setAuthorOrg(cursor.getString(cursor.getColumnIndexOrThrow(COL_AUTHOR_ORG)));
                post.setImageUrl(cursor.getString(cursor.getColumnIndexOrThrow(COL_IMAGE_URL)));
                post.setLikesCount(cursor.getInt(cursor.getColumnIndexOrThrow(COL_LIKES)));
                post.setCreatedAt(cursor.getLong(cursor.getColumnIndexOrThrow(COL_CREATED_AT)));
                post.setCached(true);
                list.add(post);
            } while (cursor.moveToNext());
            cursor.close();
        }
        return list;
    }

    public void clearCache() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_POSTS, null, null);
    }
}
