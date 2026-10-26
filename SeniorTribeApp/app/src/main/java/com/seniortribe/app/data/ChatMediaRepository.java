package com.seniortribe.app.data;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.Blob;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;
import com.seniortribe.app.models.ChatMessage;
import com.seniortribe.app.models.Membership;
import com.seniortribe.app.utils.Constants;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * E06 · Gửi và tải ảnh / tệp trong chat phối hợp.
 *
 * Nhóm dùng gói Firebase miễn phí nên không có Cloud Storage; tệp được chia thành các mảnh tối
 * đa 700 000 byte lưu trong needs_assignments/{id}/attachments/{attId}/chunks/{i} (mỗi document
 * Firestore tối đa 1 MiB). Thứ tự ghi: metadata + mảnh đầu → từng mảnh sau → tin nhắn, mỗi bước
 * một request (không vượt giới hạn kích thước request); Security Rules chỉ nhận tin nhắn khi mảnh
 * cuối đã có, nên bên kia không bao giờ thấy tệp dở dang.
 *
 * Riêng tư (R03, F16):
 *  - Ảnh được nén lại thành JPEG mới nên mất hết EXIF (vị trí GPS, máy chụp, giờ chụp).
 *  - Tệp chỉ giữ trong bộ nhớ khi xem; mở bằng ứng dụng khác thì ghi tạm vào cache và xóa khi
 *    đóng chat. Firestore đã tắt cache bền vững nên các mảnh không nằm lại trên máy.
 */
public class ChatMediaRepository {

    public static final int CHUNK_SIZE = 700_000;
    public static final long MAX_SIZE = 5L * 1024 * 1024;
    private static final int MAX_IMAGE_EDGE = 1600;
    private static final int THUMB_EDGE = 320;
    private static final int MAX_THUMB_BYTES = 60_000;
    private static final String SUB_ATTACHMENTS = "attachments";
    private static final String SUB_CHUNKS = "chunks";

    /** Kiểu tệp được gửi: PDF, Word, Excel, PowerPoint, văn bản, ảnh. */
    public static final String[] ALLOWED_FILE_TYPES = {
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "text/plain",
            "image/jpeg",
            "image/png",
            "image/webp"
    };
    private static final Set<String> ALLOWED = new HashSet<>(Arrays.asList(ALLOWED_FILE_TYPES));

    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    /** Tiến độ gửi: số byte đã lên máy chủ / tổng số byte. */
    public interface Progress {
        void onProgress(long sent, long total);
    }

    /** Tệp đã đọc và (với ảnh) đã nén, sẵn sàng gửi. */
    public static final class Prepared {
        public final String kind;       // IMAGE / FILE
        public final String fileName;
        public final String mimeType;
        public final byte[] data;
        @Nullable public final byte[] thumb;

        Prepared(String kind, String fileName, String mimeType, byte[] data, @Nullable byte[] thumb) {
            this.kind = kind;
            this.fileName = fileName;
            this.mimeType = mimeType;
            this.data = data;
            this.thumb = thumb;
        }
    }

    private final FirebaseFirestore db;

    public ChatMediaRepository() {
        this(FirebaseFirestore.getInstance());
    }

    public ChatMediaRepository(FirebaseFirestore db) {
        this.db = db;
    }

    // =====================================================================
    // Chuẩn bị tệp (chạy nền)
    // =====================================================================

    /** Đọc ảnh, xoay đúng chiều, thu nhỏ cạnh dài tối đa 1600 px, nén JPEG (bỏ EXIF), tạo ảnh nhỏ. */
    public Task<Prepared> prepareImage(Context context, Uri uri) {
        final ContentResolver resolver = context.getApplicationContext().getContentResolver();
        return Tasks.call(IO, () -> {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = resolver.openInputStream(uri)) {
                if (in == null) throw new NeedActionException("Không mở được ảnh.");
                BitmapFactory.decodeStream(in, null, bounds);
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                throw new NeedActionException("Tệp này không phải ảnh hợp lệ.");
            }
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sampleSize(Math.max(bounds.outWidth, bounds.outHeight), MAX_IMAGE_EDGE);
            Bitmap decoded;
            try (InputStream in = resolver.openInputStream(uri)) {
                decoded = BitmapFactory.decodeStream(in, null, opts);
            }
            if (decoded == null) throw new NeedActionException("Không đọc được ảnh.");
            int rotation = 0;
            try (InputStream in = resolver.openInputStream(uri)) {
                if (in != null) rotation = rotationOf(new ExifInterface(in));
            } catch (IOException ignored) {
                // Không có EXIF: giữ nguyên chiều
            }
            Bitmap image = scaleAndRotate(decoded, MAX_IMAGE_EDGE, rotation);
            byte[] data = jpeg(image, 82, MAX_SIZE);
            Bitmap small = scaleAndRotate(image, THUMB_EDGE, 0);
            byte[] thumb = jpeg(small, 60, MAX_THUMB_BYTES);
            if (small != image) small.recycle();
            image.recycle();
            if (data.length > MAX_SIZE) throw new NeedActionException("Ảnh quá lớn, kể cả sau khi nén.");
            String name = "anh_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".jpg";
            return new Prepared(ChatMessage.TYPE_IMAGE, name, "image/jpeg", data, thumb.length <= MAX_THUMB_BYTES ? thumb : null);
        });
    }

    /** Đọc tệp đính kèm (PDF, Word, Excel…), kiểm tra loại và dung lượng tối đa 5 MB. */
    public Task<Prepared> prepareFile(Context context, Uri uri) {
        final ContentResolver resolver = context.getApplicationContext().getContentResolver();
        return Tasks.call(IO, () -> {
            String mime = resolver.getType(uri);
            if (mime == null || !ALLOWED.contains(mime)) {
                throw new NeedActionException("Chỉ gửi được PDF, Word, Excel, PowerPoint, văn bản hoặc ảnh.");
            }
            String name = null;
            long declared = -1;
            try (Cursor c = resolver.query(uri, new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE},
                    null, null, null)) {
                if (c != null && c.moveToFirst()) {
                    int n = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    int s = c.getColumnIndex(OpenableColumns.SIZE);
                    if (n >= 0) name = c.getString(n);
                    if (s >= 0 && !c.isNull(s)) declared = c.getLong(s);
                }
            }
            if (declared > MAX_SIZE) throw new NeedActionException("Tệp lớn hơn 5 MB nên không gửi được.");
            byte[] data;
            try (InputStream in = resolver.openInputStream(uri)) {
                if (in == null) throw new NeedActionException("Không mở được tệp.");
                data = readAll(in, MAX_SIZE);
            }
            if (data.length == 0) throw new NeedActionException("Tệp đang trống.");
            return new Prepared(ChatMessage.TYPE_FILE, safeName(name, mime), mime, data, null);
        });
    }

    // =====================================================================
    // Gửi
    // =====================================================================

    /**
     * Gửi tệp đã chuẩn bị. Tệp một mảnh: một batch duy nhất. Tệp nhiều mảnh: metadata + mảnh đầu,
     * từng mảnh tiếp theo, rồi tin nhắn. Lỗi giữa chừng thì tin nhắn không được gửi, bên kia không
     * thấy gì; phần đã tải lên không hiện ở đâu và không đọc được ngoài hai người trong chat.
     */
    public Task<Void> send(String assignmentId, Membership me, Prepared p, @Nullable String caption,
                           @Nullable Progress progress) {
        String text = caption != null ? caption.trim() : "";
        if (text.length() > 1000) {
            return Tasks.forException(new NeedActionException("Chú thích dài quá 1000 ký tự."));
        }
        if (p.data.length == 0 || p.data.length > MAX_SIZE) {
            return Tasks.forException(new NeedActionException("Tệp phải nhỏ hơn 5 MB."));
        }
        DocumentReference assignment = db.collection(Constants.COL_ASSIGNMENTS).document(assignmentId);
        DocumentReference attRef = assignment.collection(SUB_ATTACHMENTS).document();
        DocumentReference msgRef = assignment.collection(Constants.COL_MESSAGES).document();
        final int count = (int) ((p.data.length + CHUNK_SIZE - 1) / CHUNK_SIZE);
        final long total = p.data.length;

        Map<String, Object> meta = new HashMap<>();
        meta.put("ownerUid", me.getUid());
        meta.put("fileName", p.fileName);
        meta.put("mimeType", p.mimeType);
        meta.put("size", (long) p.data.length);
        meta.put("chunkCount", (long) count);
        meta.put("kind", p.kind);
        meta.put("createdAt", FieldValue.serverTimestamp());

        Map<String, Object> message = new HashMap<>();
        message.put("senderUid", me.getUid());
        message.put("senderName", me.getDisplayName());
        message.put("text", text);
        message.put("at", FieldValue.serverTimestamp());
        message.put("type", p.kind);
        message.put("attachmentId", attRef.getId());
        message.put("fileName", p.fileName);
        message.put("mimeType", p.mimeType);
        message.put("size", (long) p.data.length);
        if (p.thumb != null) message.put("thumb", Blob.fromBytes(p.thumb));

        WriteBatch first = db.batch();
        first.set(attRef, meta);
        first.set(chunkRef(attRef, 0), chunk(p.data, 0));
        if (count == 1) first.set(msgRef, message);
        Task<Void> chain = first.commit().addOnSuccessListener(v -> report(progress, Math.min(CHUNK_SIZE, total), total));
        for (int i = 1; i < count; i++) {
            final int index = i;
            chain = chain.continueWithTask(t -> {
                if (!t.isSuccessful()) return t;
                return chunkRef(attRef, index).set(chunk(p.data, index))
                        .addOnSuccessListener(v -> report(progress, Math.min((long) (index + 1) * CHUNK_SIZE, total), total));
            });
        }
        if (count > 1) {
            chain = chain.continueWithTask(t -> t.isSuccessful() ? msgRef.set(message) : t);
        }
        return chain;
    }

    // =====================================================================
    // Tải về (chỉ giữ trong bộ nhớ)
    // =====================================================================

    /** Tải đủ các mảnh của một tệp và ghép lại; kiểm tra số mảnh và dung lượng khớp metadata. */
    public Task<byte[]> download(String assignmentId, String attachmentId) {
        DocumentReference attRef = db.collection(Constants.COL_ASSIGNMENTS).document(assignmentId)
                .collection(SUB_ATTACHMENTS).document(attachmentId);
        return attRef.get().continueWithTask(metaTask -> {
            DocumentSnapshot meta = metaTask.getResult();
            if (meta == null || !meta.exists()) throw new NeedActionException("Không tìm thấy tệp.");
            final long size = meta.getLong("size") != null ? meta.getLong("size") : -1;
            final long count = meta.getLong("chunkCount") != null ? meta.getLong("chunkCount") : -1;
            return attRef.collection(SUB_CHUNKS).orderBy("index").get().continueWith(IO, chunksTask -> {
                List<DocumentSnapshot> docs = chunksTask.getResult().getDocuments();
                if (docs.size() != count || size <= 0) throw new NeedActionException("Tệp chưa tải lên đủ.");
                ByteArrayOutputStream out = new ByteArrayOutputStream((int) size);
                for (int i = 0; i < docs.size(); i++) {
                    DocumentSnapshot d = docs.get(i);
                    Long index = d.getLong("index");
                    Blob data = d.getBlob("data");
                    if (index == null || index != i || data == null) throw new NeedActionException("Tệp bị lỗi.");
                    byte[] bytes = data.toBytes();
                    out.write(bytes, 0, bytes.length);
                }
                byte[] all = out.toByteArray();
                if (all.length != size) throw new NeedActionException("Tệp bị lỗi (sai dung lượng).");
                return all;
            });
        });
    }

    // =====================================================================

    private static DocumentReference chunkRef(DocumentReference att, int index) {
        return att.collection(SUB_CHUNKS).document(String.valueOf(index));
    }

    private static Map<String, Object> chunk(byte[] all, int index) {
        int from = index * CHUNK_SIZE;
        int to = Math.min(all.length, from + CHUNK_SIZE);
        Map<String, Object> m = new HashMap<>();
        m.put("index", (long) index);
        m.put("data", Blob.fromBytes(Arrays.copyOfRange(all, from, to)));
        return m;
    }

    private static void report(@Nullable Progress p, long sent, long total) {
        if (p != null) MAIN.post(() -> p.onProgress(sent, total));
    }

    private static int sampleSize(int longEdge, int target) {
        int s = 1;
        while (longEdge / (s * 2) >= target) s *= 2;
        return s;
    }

    private static int rotationOf(ExifInterface exif) {
        switch (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            case ExifInterface.ORIENTATION_ROTATE_90: return 90;
            case ExifInterface.ORIENTATION_ROTATE_180: return 180;
            case ExifInterface.ORIENTATION_ROTATE_270: return 270;
            default: return 0;
        }
    }

    private static Bitmap scaleAndRotate(Bitmap src, int maxEdge, int rotation) {
        int longEdge = Math.max(src.getWidth(), src.getHeight());
        float scale = longEdge > maxEdge ? (float) maxEdge / longEdge : 1f;
        if (scale == 1f && rotation == 0) return src;
        Matrix m = new Matrix();
        m.postScale(scale, scale);
        if (rotation != 0) m.postRotate(rotation);
        Bitmap out = Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), m, true);
        if (out != src && rotation != 0) src.recycle();
        return out;
    }

    /** Nén JPEG, giảm chất lượng dần nếu vượt giới hạn. */
    private static byte[] jpeg(Bitmap bmp, int quality, long maxBytes) {
        int q = quality;
        byte[] out;
        do {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.JPEG, q, bos);
            out = bos.toByteArray();
            q -= 10;
        } while (out.length > maxBytes && q >= 30);
        return out;
    }

    private static byte[] readAll(InputStream in, long max) throws IOException, NeedActionException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[16 * 1024];
        long total = 0;
        int n;
        while ((n = in.read(buf)) > 0) {
            total += n;
            if (total > max) throw new NeedActionException("Tệp lớn hơn 5 MB nên không gửi được.");
            bos.write(buf, 0, n);
        }
        return bos.toByteArray();
    }

    /** Tên tệp an toàn: bỏ ký tự đường dẫn, tối đa 120 ký tự, có đuôi theo kiểu tệp. */
    static String safeName(@Nullable String name, String mime) {
        String n = name != null ? name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim() : "";
        if (n.isEmpty()) n = "tep" + extensionFor(mime);
        if (n.length() > 120) {
            int dot = n.lastIndexOf('.');
            String ext = dot > 0 && n.length() - dot <= 8 ? n.substring(dot) : "";
            n = n.substring(0, 120 - ext.length()) + ext;
        }
        return n;
    }

    private static String extensionFor(String mime) {
        if (TextUtils.isEmpty(mime)) return "";
        switch (mime) {
            case "application/pdf": return ".pdf";
            case "text/plain": return ".txt";
            case "image/jpeg": return ".jpg";
            case "image/png": return ".png";
            default: return "";
        }
    }

    /** "1,2 MB", "350 KB". */
    public static String formatSize(long bytes) {
        if (bytes >= 1024 * 1024) return String.format(Locale.getDefault(), "%.1f MB", bytes / (1024f * 1024f));
        if (bytes >= 1024) return String.format(Locale.getDefault(), "%d KB", bytes / 1024);
        return bytes + " B";
    }
}
