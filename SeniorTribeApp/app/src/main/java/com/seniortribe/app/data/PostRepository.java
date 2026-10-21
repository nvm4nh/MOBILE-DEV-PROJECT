package com.seniortribe.app.data;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Source;
import com.seniortribe.app.database.AppDatabaseHelper;
import com.seniortribe.app.models.Membership;
import com.seniortribe.app.models.Post;
import com.seniortribe.app.models.PostForm;
import com.seniortribe.app.utils.Constants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Dữ liệu Bảng tin (F04, F05): Firestore là nguồn chính, SQLite là bản sao để xem lại khi mất mạng
 * (F16, T05).
 *
 *  - Có mạng: đọc bài từ máy chủ, rồi ghi đè bộ đệm SQLite ở luồng nền.
 *  - Mất mạng / máy chủ lỗi: đọc bộ đệm SQLite, màn hình báo đang xem bài đã tải.
 *  - Đăng, sửa, ẩn bài: chỉ báo thành công khi máy chủ đã xác nhận, không báo thành công giả.
 *  - Bộ đệm gắn với tài khoản đã tải; đổi tài khoản thì bộ đệm cũ bị xóa.
 */
public class PostRepository {

    /** Một luồng nền dùng chung cho SQLite, tránh đọc/ghi file trên luồng giao diện. */
    private static final Executor IO = Executors.newSingleThreadExecutor();
    private static final String PREFS = "feed_cache";
    private static final String KEY_OWNER = "owner_uid";

    private final FirebaseFirestore db;
    private final AppDatabaseHelper cache;
    private final SharedPreferences prefs;

    public PostRepository(Context context) {
        Context app = context.getApplicationContext();
        this.db = FirebaseFirestore.getInstance();
        this.cache = AppDatabaseHelper.getInstance(app);
        this.prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // =====================================================================
    // F04 · Đọc bài
    // =====================================================================

    /**
     * Bài chưa ẩn, mới nhất trước, đọc thẳng từ máy chủ (Source.SERVER) để không lẫn dữ liệu cũ.
     * Sắp xếp ở máy nên không cần tạo composite index cho (hidden, createdAt).
     * Truy vấn phải có điều kiện hidden == false thì Security Rules mới cho đọc danh sách.
     */
    public Task<List<Post>> loadFromServer() {
        return db.collection(Constants.COL_POSTS)
                .whereEqualTo("hidden", false)
                .get(Source.SERVER)
                .<List<Post>>continueWith(task -> {
                    if (!task.isSuccessful()) throw errorOf(task.getException());
                    List<Post> posts = new ArrayList<>();
                    for (DocumentSnapshot doc : task.getResult().getDocuments()) {
                        Post p = doc.toObject(Post.class);
                        if (p != null && !p.isHidden()) posts.add(p);
                    }
                    sortNewestFirst(posts);
                    return posts;
                });
    }

    /** Một bài mới nhất từ máy chủ, dùng sau khi sửa bài. */
    public Task<Post> loadPost(String postId) {
        return db.collection(Constants.COL_POSTS).document(postId).get(Source.SERVER)
                .<Post>continueWith(task -> {
                    if (!task.isSuccessful()) throw errorOf(task.getException());
                    Post p = task.getResult().toObject(Post.class);
                    if (p == null) throw new NeedActionException("Không tìm thấy bài đăng. Có thể bài đã bị xóa.");
                    return p;
                });
    }

    // =====================================================================
    // F16 · Bộ đệm SQLite
    // =====================================================================

    /** Ghi đè bộ đệm SQLite bằng danh sách vừa tải (chạy nền). */
    public Task<Void> saveToCache(List<Post> posts) {
        final List<Post> copy = new ArrayList<>(posts);
        final String owner = currentUid();
        return Tasks.call(IO, () -> {
            cache.replaceAllPosts(copy);
            prefs.edit().putString(KEY_OWNER, owner).apply();
            return null;
        });
    }

    /**
     * Đọc các bài đã lưu trong SQLite (chạy nền, kết quả trả về luồng giao diện).
     * Bộ đệm của tài khoản khác thì xóa đi và trả về rỗng.
     */
    public Task<List<Post>> loadFromCache() {
        final String owner = currentUid();
        return Tasks.call(IO, () -> {
            String cachedOwner = prefs.getString(KEY_OWNER, null);
            if (cachedOwner == null || !cachedOwner.equals(owner)) {
                cache.clearCache();
                return new ArrayList<Post>();
            }
            return cache.getAllCachedPosts();
        });
    }

    // =====================================================================
    // F05 · Đăng, sửa, ẩn bài
    // =====================================================================

    /**
     * Đại diện tổ chức đăng bài. Tổ chức lấy từ quyền của tài khoản, không cho người dùng tự nhập;
     * dấu "đã xác minh" đọc từ organizations/{orgId} và được rules kiểm tra lại.
     */
    public Task<DocumentReference> createPost(Membership me, PostForm form) {
        if (me == null || !me.isRepresentative()) {
            return Tasks.forException(new NeedActionException("Chỉ đại diện tổ chức mới đăng được bài."));
        }
        return db.collection(Constants.COL_ORGANIZATIONS).document(me.getOrgId()).get(Source.SERVER)
                .<DocumentReference>continueWithTask(orgTask -> {
                    if (!orgTask.isSuccessful()) return Tasks.<DocumentReference>forException(errorOf(orgTask.getException()));
                    Boolean verified = orgTask.getResult().getBoolean("verified");

                    Map<String, Object> post = new HashMap<>();
                    post.put("title", form.title);
                    post.put("content", form.content);
                    post.put("eventTime", form.eventTime);
                    post.put("eventPlace", form.eventPlace);
                    post.put("orgId", me.getOrgId());
                    post.put("orgName", me.getOrgName());
                    post.put("orgVerified", Boolean.TRUE.equals(verified));
                    post.put("authorUid", me.getUid());
                    post.put("authorName", me.getDisplayName());
                    post.put("hidden", false);
                    post.put("createdAt", FieldValue.serverTimestamp());
                    post.put("updatedAt", FieldValue.serverTimestamp());
                    return db.collection(Constants.COL_POSTS).add(post);
                });
    }

    /** Đại diện của tổ chức đã đăng sửa nội dung bài (không đổi tổ chức, người đăng). */
    public Task<Void> updatePost(Membership me, Post post, PostForm form) {
        if (!canManage(me, post)) {
            return Tasks.forException(new NeedActionException("Chỉ đại diện của tổ chức đã đăng mới sửa được bài này."));
        }
        Map<String, Object> update = new HashMap<>();
        update.put("title", form.title);
        update.put("content", form.content);
        update.put("eventTime", form.eventTime);
        update.put("eventPlace", form.eventPlace);
        update.put("updatedAt", FieldValue.serverTimestamp());
        return db.collection(Constants.COL_POSTS).document(post.getId()).update(update);
    }

    /** Ẩn bài khỏi bảng tin. Không xóa để vẫn còn dấu vết trên máy chủ. */
    public Task<Void> hidePost(Membership me, Post post) {
        if (!canManage(me, post)) {
            return Tasks.forException(new NeedActionException("Chỉ đại diện của tổ chức đã đăng mới ẩn được bài này."));
        }
        Map<String, Object> update = new HashMap<>();
        update.put("hidden", true);
        update.put("updatedAt", FieldValue.serverTimestamp());
        return db.collection(Constants.COL_POSTS).document(post.getId()).update(update);
    }

    /** Đại diện của đúng tổ chức đã đăng mới được sửa / ẩn (F05: không sửa bài của tổ chức khác). */
    public static boolean canManage(@Nullable Membership me, @Nullable Post post) {
        return me != null && post != null && post.getId() != null
                && me.isRepresentative() && me.getOrgId() != null && me.getOrgId().equals(post.getOrgId());
    }

    static void sortNewestFirst(List<Post> posts) {
        Collections.sort(posts, (a, b) -> Long.compare(b.getCreatedAtMillis(), a.getCreatedAtMillis()));
    }

    @Nullable
    private static String currentUid() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return user != null ? user.getUid() : null;
    }

    private static Exception errorOf(@Nullable Exception e) {
        return e != null ? e : new IllegalStateException("posts");
    }
}
