package com.seniortribe.app.notifications;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Source;
import com.seniortribe.app.data.MembershipRepository;
import com.seniortribe.app.models.Membership;
import com.seniortribe.app.models.NeedSummary;
import com.seniortribe.app.utils.Constants;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * E01 · Kiểm tra định kỳ khi ứng dụng không chạy (WorkManager, khoảng 15 phút một lần, chỉ khi
 * có mạng). Đọc các nhu cầu của đơn vị, sự kiện và tin nhắn mới sau mốc đã xử lý, rồi hiện
 * thông báo như {@link NotificationCenter}.
 *
 * Vì nhóm dùng gói Firebase miễn phí (không có Cloud Functions để gửi FCM), đây là cách nhận
 * thông báo khi app đã tắt; độ trễ tối đa khoảng 15 phút và phụ thuộc chế độ tiết kiệm pin.
 * Khi ứng dụng đang mở, {@link NotificationCenter} báo ngay theo thời gian thực.
 */
public class NotificationSyncWorker extends Worker {

    private static final String WORK_NAME = "notification_sync";
    private static final long TIMEOUT_S = 20;

    public NotificationSyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    public static void schedule(Context context) {
        PeriodicWorkRequest req = new PeriodicWorkRequest.Builder(NotificationSyncWorker.class, 15, TimeUnit.MINUTES)
                .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, req);
    }

    public static void cancel(Context context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME);
    }

    @NonNull
    @Override
    public Result doWork() {
        // Ứng dụng đang chạy và đã có listener realtime thì không cần làm lại
        if (NotificationCenter.isRunning()) return Result.success();
        try {
            Membership me = Tasks.await(MembershipRepository.get().loadCurrent(true), TIMEOUT_S, TimeUnit.SECONDS);
            if (me == null) return Result.success(); // chưa / không còn quyền tổ chức
            AppNotifier.createChannels(getApplicationContext());
            NotificationEngine engine = new NotificationEngine(getApplicationContext(), me);
            FirebaseFirestore db = FirebaseFirestore.getInstance();

            Map<String, NeedSummary> needs = new LinkedHashMap<>();
            addAll(needs, db.collection(Constants.COL_NEEDS_SUMMARY).whereEqualTo("recorderOrgId", me.getOrgId()));
            addAll(needs, db.collection(Constants.COL_NEEDS_SUMMARY).whereEqualTo("assignedOrgId", me.getOrgId()));

            for (NeedSummary n : needs.values()) {
                if (engine.watchEvents(n)) {
                    QuerySnapshot events = await(db.collection(Constants.COL_NEEDS_SUMMARY).document(n.getNeedId())
                            .collection(Constants.SUB_EVENTS)
                            .whereGreaterThan("at", new Timestamp(new Date(engine.eventsSince(n.getNeedId()))))
                            .orderBy("at"));
                    engine.onEvents(n, events.getDocuments());
                }
                if (engine.watchMessages(n)) {
                    String a = n.getCurrentAssignmentId();
                    QuerySnapshot messages = await(db.collection(Constants.COL_ASSIGNMENTS).document(a)
                            .collection(Constants.COL_MESSAGES)
                            .whereGreaterThan("at", new Timestamp(new Date(engine.messagesSince(a))))
                            .orderBy("at"));
                    engine.onMessages(n, a, messages.getDocuments(), null);
                }
            }
            engine.syncReminders(new ArrayList<>(needs.values()));
            return Result.success();
        } catch (Exception e) {
            // Mất mạng / hết phiên: thử lại ở lần định kỳ sau, không báo lỗi cho người dùng
            return Result.success();
        }
    }

    private static void addAll(Map<String, NeedSummary> out, Query q) throws Exception {
        for (DocumentSnapshot d : await(q).getDocuments()) {
            NeedSummary n = d.toObject(NeedSummary.class);
            if (n != null) out.put(n.getNeedId(), n);
        }
    }

    private static QuerySnapshot await(Query q) throws Exception {
        return Tasks.await(q.get(Source.SERVER), TIMEOUT_S, TimeUnit.SECONDS);
    }
}
