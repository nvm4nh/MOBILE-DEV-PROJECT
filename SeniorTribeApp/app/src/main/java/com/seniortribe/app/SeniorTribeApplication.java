package com.seniortribe.app;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.seniortribe.app.auth.AuthRepository;
import com.seniortribe.app.auth.FirestoreMembershipWatch;
import com.seniortribe.app.auth.GoogleCredentialSession;
import com.seniortribe.app.auth.LiveAccessMonitor;
import com.seniortribe.app.search.SearchHistory;
import com.seniortribe.app.ui.DisplaySettings;
import com.seniortribe.app.ui.TextScaleApplier;
import com.seniortribe.app.data.MembershipRepository;
import com.seniortribe.app.notifications.AppNotifier;
import com.seniortribe.app.notifications.NotificationCenter;

public class SeniorTribeApplication extends Application {
    private AuthRepository authRepository;
    private GoogleCredentialSession googleCredentialSession;
    private LiveAccessMonitor accessMonitor;

    private final MutableLiveData<LiveAccessMonitor.State> liveAccess =
            new MutableLiveData<>();

    private String authorizedUid;
    private String previousSessionUid;
    private NotificationCenter notificationCenter;

    @Override
    public void onCreate() {
        super.onCreate();

        // E08 · Giao diện sáng / tối và cỡ chữ người dùng đã chọn, áp dụng cho mọi màn hình
        new DisplaySettings(this).applyStoredTheme();
        TextScaleApplier.install(this);

        if (FirebaseApp.initializeApp(this) == null) {
            throw new IllegalStateException("Firebase configuration is missing");
        }
        // E08 · Lịch sử tìm kiếm chỉ thuộc tài khoản đang dùng: đăng xuất thì xóa
        SearchHistory.clearOnSignOut(this);

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.setFirestoreSettings(
                new FirebaseFirestoreSettings.Builder(db.getFirestoreSettings())
                        .setPersistenceEnabled(false)
                        .build()
        );

        FirebaseAuth auth = FirebaseAuth.getInstance();
        Handler handler = new Handler(Looper.getMainLooper());

        // E01 · Kênh thông báo và listener realtime (chạy khi tài khoản đã được xác nhận quyền)
        AppNotifier.createChannels(this);
        // E06 · Xóa tệp tạm của chat (ảnh chụp, tệp đã mở) còn sót nếu lần trước app bị tắt đột ngột
        com.seniortribe.app.activities.ChatActivity.clearSharedCache(this);
        notificationCenter = new NotificationCenter(this);

        googleCredentialSession = new GoogleCredentialSession(this);
        authRepository = new AuthRepository(auth, db, googleCredentialSession);

        accessMonitor = new LiveAccessMonitor(
                new FirestoreMembershipWatch(db, auth, action -> handler.post(action)),
                (delay, action) -> {
                    handler.postDelayed(action, delay);
                    return () -> handler.removeCallbacks(action);
                },
                state -> {
                    if (state.stage != LiveAccessMonitor.Stage.CHECKING
                            && state.stage != LiveAccessMonitor.Stage.GRANTED) {
                        dropAccess();
                    }
                    liveAccess.setValue(state);
                }
        );

        liveAccess.setValue(accessMonitor.state());

        FirebaseUser initial = auth.getCurrentUser();
        previousSessionUid = initial == null ? null : initial.getUid();

        auth.addAuthStateListener(changed -> {
            FirebaseUser user = changed.getCurrentUser();

            if (user == null || !user.getUid().equals(authorizedUid)) {
                clearActiveAccess();
            }

            if (user == null && previousSessionUid != null) {
                googleCredentialSession.clearState();
                // E01 · Đăng xuất: gỡ thông báo, hủy nhắc lịch và xóa mốc đã xem trên máy
                notificationCenter.signOut();
            }

            previousSessionUid = user == null ? null : user.getUid();
        });
    }

    public AuthRepository getAuthRepository() {
        return authRepository;
    }

    /** E01 · Thông báo realtime của tài khoản đang đăng nhập. */
    public NotificationCenter getNotificationCenter() {
        return notificationCenter;
    }

    public GoogleCredentialSession getGoogleCredentialSession() {
        return googleCredentialSession;
    }

    public LiveData<LiveAccessMonitor.State> liveAccess() {
        return liveAccess;
    }

    public void markActiveAccess(String uid) {
        if (!authRepository.isCurrentUser(uid)) {
            throw new IllegalStateException("SESSION");
        }

        authorizedUid = uid;
        accessMonitor.start(uid);
        notificationCenter.start(uid);
    }

    public void recheckActiveAccess(String uid) {
        if (hasActiveAccess() && authorizedUid.equals(uid)) {
            accessMonitor.start(uid);
        } else {
            clearActiveAccess();
        }
    }

    public void clearActiveAccess() {
        dropAccess();
        if (accessMonitor != null) accessMonitor.stop();
    }

    private void dropAccess() {
        authorizedUid = null;
        MembershipRepository.get().clear();
        if (notificationCenter != null) notificationCenter.stop();
    }

    public boolean hasActiveAccess() {
        return authorizedUid != null && authRepository.isCurrentUser(authorizedUid);
    }
}