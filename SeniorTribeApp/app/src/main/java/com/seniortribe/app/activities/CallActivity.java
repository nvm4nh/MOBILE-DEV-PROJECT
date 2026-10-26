package com.seniortribe.app.activities;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;
import com.seniortribe.app.R;
import com.seniortribe.app.calls.CallSession;
import com.seniortribe.app.data.CallRepository;
import com.seniortribe.app.data.MembershipRepository;
import com.seniortribe.app.models.CallRecord;
import com.seniortribe.app.models.Membership;
import com.seniortribe.app.notifications.AppNotifier;
import com.seniortribe.app.utils.Constants;
import com.seniortribe.app.utils.NetworkUtils;

import org.webrtc.IceCandidate;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoTrack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * E06 · Gọi thoại / video giữa đầu mối ghi nhận và người được giao phụ trách (R08).
 *
 * Hai chế độ:
 *  - Gọi đi: xin quyền micro (+ camera), tạo offer, ghi calls/{id} trạng thái RINGING, chờ bên kia
 *    nghe máy; 45 giây không ai nghe thì ghi MISSED.
 *  - Cuộc gọi đến: đổ chuông, Nghe máy (tạo answer, ACCEPTED) hoặc Từ chối (DECLINED).
 * ICE candidate trao đổi qua calls/{id}/candidates. Âm thanh / hình ảnh đi thẳng giữa hai máy.
 *
 * Màn hình tự xử lý xoay (configChanges trong manifest) vì tạo lại Activity sẽ làm rớt cuộc gọi;
 * bố cục FrameLayout tự co giãn theo chiều ngang / dọc (T04).
 */
public class CallActivity extends AppCompatActivity {

    public static final String EXTRA_INCOMING = "extra_call_incoming";
    public static final String EXTRA_CALL_ID = "extra_call_id";
    public static final String EXTRA_VIDEO = "extra_call_video";
    public static final String EXTRA_PEER_UID = "extra_call_peer_uid";
    public static final String EXTRA_PEER_NAME = "extra_call_peer_name";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final CallRepository repo = new CallRepository();
    private final List<ListenerRegistration> registrations = new ArrayList<>();
    private final List<IceCandidate> unsentCandidates = new ArrayList<>();

    private String assignmentId, needId, callId, peerUid, peerName;
    private boolean incoming, video, speaker, micOn = true, cameraOn = true;
    private boolean callCreated, answered, connectedOnce, finished;
    private long connectedAt;
    @Nullable private Membership me;
    @Nullable private DocumentReference callRef;
    @Nullable private CallSession session;
    @Nullable private Ringtone ringtone;
    private int previousAudioMode = AudioManager.MODE_NORMAL;

    private SurfaceViewRenderer remoteView, localView;
    private View layoutPeer, layoutControls, layoutIncoming;
    private TextView tvAvatar, tvPeerName, tvStatus, tvTimer;
    private ImageButton btnMic, btnCamera, btnSwitch, btnSpeaker;

    private final ActivityResultLauncher<String[]> permissions = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                if (hasPermissions()) startMedia();
                else {
                    Toast.makeText(this, R.string.call_permission_denied, Toast.LENGTH_LONG).show();
                    if (incoming) decline(); else endCall(false);
                }
            });

    private final Runnable ringTimeout = () -> {
        if (!incoming && !answered && !finished) {
            setStatus(R.string.call_status_no_answer);
            endCall(true);
        }
    };

    private final Runnable timerTick = new Runnable() {
        @Override public void run() {
            if (connectedAt > 0 && !finished) {
                long s = (SystemClock.elapsedRealtime() - connectedAt) / 1000;
                tvTimer.setText(String.format(Locale.getDefault(), "%02d:%02d", s / 60, s % 60));
                handler.postDelayed(this, 1000);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showOverLockScreen();
        setContentView(R.layout.activity_call);
        bindViews();

        assignmentId = getIntent().getStringExtra(Constants.EXTRA_ASSIGNMENT_ID);
        needId = getIntent().getStringExtra(Constants.EXTRA_NEED_ID);
        callId = getIntent().getStringExtra(EXTRA_CALL_ID);
        incoming = getIntent().getBooleanExtra(EXTRA_INCOMING, false) || callId != null;
        video = getIntent().getBooleanExtra(EXTRA_VIDEO, false);
        peerUid = getIntent().getStringExtra(EXTRA_PEER_UID);
        peerName = getIntent().getStringExtra(EXTRA_PEER_NAME);
        if (assignmentId == null || (incoming && callId == null) || (!incoming && peerUid == null)) {
            finish();
            return;
        }
        if (callId != null) new AppNotifier(this).cancel("call_" + callId);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                // Không thoát nhầm khi đang gọi: phải bấm nút kết thúc
                Toast.makeText(CallActivity.this, R.string.call_use_hangup, Toast.LENGTH_SHORT).show();
            }
        });

        btnMic.setOnClickListener(v -> toggleMic());
        btnCamera.setOnClickListener(v -> toggleCamera());
        btnSwitch.setOnClickListener(v -> { if (session != null) session.switchCamera(); });
        btnSpeaker.setOnClickListener(v -> setSpeaker(!speaker));
        findViewById(R.id.btnHangup).setOnClickListener(v -> endCall(false));
        findViewById(R.id.btnDecline).setOnClickListener(v -> decline());
        findViewById(R.id.btnAccept).setOnClickListener(v -> accept());

        renderPeer();
        MembershipRepository.get().loadCurrent(false).addOnCompleteListener(t -> {
            me = t.isSuccessful() ? t.getResult() : null;
            if (me == null) {
                Toast.makeText(this, R.string.call_no_access, Toast.LENGTH_LONG).show();
                finishCall();
                return;
            }
            if (incoming) prepareIncoming();
            else beginOutgoing();
        });
    }

    private void bindViews() {
        remoteView = findViewById(R.id.remoteView);
        localView = findViewById(R.id.localView);
        layoutPeer = findViewById(R.id.layoutPeer);
        layoutControls = findViewById(R.id.layoutControls);
        layoutIncoming = findViewById(R.id.layoutIncoming);
        tvAvatar = findViewById(R.id.tvAvatar);
        tvPeerName = findViewById(R.id.tvPeerName);
        tvStatus = findViewById(R.id.tvCallStatus);
        tvTimer = findViewById(R.id.tvTimer);
        btnMic = findViewById(R.id.btnMic);
        btnCamera = findViewById(R.id.btnCamera);
        btnSwitch = findViewById(R.id.btnSwitchCamera);
        btnSpeaker = findViewById(R.id.btnSpeaker);
    }

    private void showOverLockScreen() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
    }

    private void renderPeer() {
        String name = !TextUtils.isEmpty(peerName) ? peerName : getString(R.string.chat_sender_unknown);
        tvPeerName.setText(name);
        tvAvatar.setText(name.substring(0, 1).toUpperCase(Locale.getDefault()));
        boolean v = video;
        btnCamera.setVisibility(v ? View.VISIBLE : View.GONE);
        btnSwitch.setVisibility(v ? View.VISIBLE : View.GONE);
        layoutIncoming.setVisibility(incoming ? View.VISIBLE : View.GONE);
        layoutControls.setVisibility(incoming ? View.GONE : View.VISIBLE);
        setStatus(incoming ? (v ? R.string.call_status_incoming_video : R.string.call_status_incoming_voice)
                : (v ? R.string.call_status_calling_video : R.string.call_status_calling_voice));
    }

    // =====================================================================
    // Gọi đi
    // =====================================================================

    private void beginOutgoing() {
        if (!NetworkUtils.isNetworkAvailable(this)) {
            Toast.makeText(this, R.string.action_needs_network, Toast.LENGTH_LONG).show();
            finishCall();
            return;
        }
        callRef = repo.newCallRef(assignmentId);
        callId = callRef.getId();
        if (hasPermissions()) startMedia();
        else permissions.launch(requiredPermissions());
    }

    /** Có quyền rồi: bật micro / camera, rồi gửi offer (gọi đi) hoặc answer (nghe máy). */
    private void startMedia() {
        if (finished || me == null) return;
        session = new CallSession(this, video, sessionListener);
        session.start(video ? localView : null, video ? remoteView : null);
        if (video) {
            localView.setVisibility(View.VISIBLE);
            remoteView.setVisibility(View.VISIBLE);
            setSpeaker(true);
        } else {
            setSpeaker(false);
        }
        enterCallAudio();
        if (incoming) answerOffer();
        else sendOffer();
    }

    private void sendOffer() {
        session.createOffer(new CallSession.SdpCallback() {
            @Override public void onSdp(String sdp) {
                repo.createCall(callRef, me, peerUid, video, sdp).addOnCompleteListener(t -> {
                    if (!t.isSuccessful()) {
                        Toast.makeText(CallActivity.this, R.string.call_failed_start, Toast.LENGTH_LONG).show();
                        finishCall();
                        return;
                    }
                    callCreated = true;
                    flushCandidates();
                    listenCall();
                    listenCandidates();
                    handler.postDelayed(ringTimeout, CallRecord.RING_TIMEOUT_MS);
                });
            }

            @Override public void onError(String message) {
                Toast.makeText(CallActivity.this, R.string.call_failed_start, Toast.LENGTH_LONG).show();
                finishCall();
            }
        });
    }

    // =====================================================================
    // Cuộc gọi đến
    // =====================================================================

    private void prepareIncoming() {
        callRef = repo.callRef(assignmentId, callId);
        callRef.get().addOnCompleteListener(t -> {
            DocumentSnapshot d = t.isSuccessful() ? t.getResult() : null;
            CallRecord c = d != null ? d.toObject(CallRecord.class) : null;
            if (c == null || !c.isFreshRinging(System.currentTimeMillis()) || !me.getUid().equals(c.getCalleeUid())) {
                Toast.makeText(this, R.string.call_already_ended, Toast.LENGTH_LONG).show();
                finishCall();
                return;
            }
            video = c.isVideo();
            if (TextUtils.isEmpty(peerName)) peerName = c.getCallerName();
            peerUid = c.getCallerUid();
            renderPeer();
            startRinging();
            listenCall();
        });
    }

    private void accept() {
        if (answered || finished) return;
        stopRinging();
        layoutIncoming.setVisibility(View.GONE);
        layoutControls.setVisibility(View.VISIBLE);
        setStatus(R.string.call_status_connecting);
        if (!NetworkUtils.isNetworkAvailable(this)) {
            Toast.makeText(this, R.string.action_needs_network, Toast.LENGTH_LONG).show();
            decline();
            return;
        }
        if (hasPermissions()) startMedia();
        else permissions.launch(requiredPermissions());
    }

    private void answerOffer() {
        callRef.get().addOnCompleteListener(t -> {
            DocumentSnapshot d = t.isSuccessful() ? t.getResult() : null;
            Object offer = d != null ? d.get("offer") : null;
            String sdp = offer instanceof Map ? (String) ((Map<?, ?>) offer).get("sdp") : null;
            if (sdp == null || !CallRecord.RINGING.equals(d.getString("status"))) {
                Toast.makeText(this, R.string.call_already_ended, Toast.LENGTH_LONG).show();
                finishCall();
                return;
            }
            session.acceptOffer(sdp, new CallSession.SdpCallback() {
                @Override public void onSdp(String answer) {
                    repo.answer(callRef, answer).addOnCompleteListener(a -> {
                        if (!a.isSuccessful()) {
                            Toast.makeText(CallActivity.this, R.string.call_already_ended, Toast.LENGTH_LONG).show();
                            finishCall();
                            return;
                        }
                        answered = true;
                        callCreated = true;
                        flushCandidates();
                        listenCandidates();
                    });
                }

                @Override public void onError(String message) {
                    Toast.makeText(CallActivity.this, R.string.call_failed_start, Toast.LENGTH_LONG).show();
                    decline();
                }
            });
        });
    }

    private void decline() {
        stopRinging();
        if (me != null && callRef != null && !answered && !finished) {
            repo.end(callRef, me, CallRecord.DECLINED, null);
        }
        finishCall();
    }

    // =====================================================================
    // Tín hiệu qua Firestore
    // =====================================================================

    private void listenCall() {
        registrations.add(callRef.addSnapshotListener((snap, error) -> {
            if (snap == null || !snap.exists() || finished) return;
            String status = snap.getString("status");
            if (CallRecord.ACCEPTED.equals(status) && !incoming && !answered) {
                answered = true;
                handler.removeCallbacks(ringTimeout);
                setStatus(R.string.call_status_connecting);
                Object answer = snap.get("answer");
                String sdp = answer instanceof Map ? (String) ((Map<?, ?>) answer).get("sdp") : null;
                if (sdp != null && session != null) session.setRemoteAnswer(sdp);
            } else if (CallRecord.DECLINED.equals(status)) {
                toastAndFinish(R.string.call_status_declined);
            } else if (CallRecord.CANCELLED.equals(status) || CallRecord.MISSED.equals(status)) {
                toastAndFinish(incoming ? R.string.call_status_missed : R.string.call_status_no_answer);
            } else if (CallRecord.ENDED.equals(status)) {
                toastAndFinish(R.string.call_status_ended);
            }
        }));
    }

    private void listenCandidates() {
        registrations.add(repo.candidates(callRef).addSnapshotListener((snap, error) -> {
            if (snap == null || session == null || me == null) return;
            for (DocumentChange ch : snap.getDocumentChanges()) {
                if (ch.getType() != DocumentChange.Type.ADDED) continue;
                DocumentSnapshot d = ch.getDocument();
                if (me.getUid().equals(d.getString("fromUid"))) continue;
                String cand = d.getString("candidate");
                Long index = d.getLong("sdpMLineIndex");
                if (cand == null || index == null) continue;
                session.addRemoteCandidate(d.getString("sdpMid"), index.intValue(), cand);
            }
        }));
    }

    private void flushCandidates() {
        if (me == null || callRef == null) return;
        for (IceCandidate c : unsentCandidates) repo.addCandidate(callRef, me, c.sdp, c.sdpMid, c.sdpMLineIndex);
        unsentCandidates.clear();
    }

    private final CallSession.Listener sessionListener = new CallSession.Listener() {
        @Override public void onLocalCandidate(IceCandidate candidate) {
            if (callCreated && me != null && callRef != null) {
                repo.addCandidate(callRef, me, candidate.sdp, candidate.sdpMid, candidate.sdpMLineIndex);
            } else {
                unsentCandidates.add(candidate);
            }
        }

        @Override public void onState(CallSession.State state) {
            if (finished) return;
            switch (state) {
                case CONNECTED:
                    if (!connectedOnce) {
                        connectedOnce = true;
                        connectedAt = SystemClock.elapsedRealtime();
                        tvTimer.setVisibility(View.VISIBLE);
                        handler.post(timerTick);
                    }
                    setStatus(R.string.call_status_connected);
                    if (video) layoutPeer.setVisibility(View.GONE);
                    break;
                case DISCONNECTED:
                    setStatus(R.string.call_status_reconnecting);
                    layoutPeer.setVisibility(View.VISIBLE);
                    break;
                case FAILED:
                    Toast.makeText(CallActivity.this, R.string.call_failed_network, Toast.LENGTH_LONG).show();
                    endCall(false);
                    break;
                default:
                    break;
            }
        }

        @Override public void onRemoteVideo(VideoTrack track) {
            remoteView.setVisibility(View.VISIBLE);
        }
    };

    // =====================================================================
    // Điều khiển
    // =====================================================================

    private void toggleMic() {
        micOn = !micOn;
        if (session != null) session.setMicEnabled(micOn);
        btnMic.setImageResource(micOn ? R.drawable.ic_mic : R.drawable.ic_mic_off);
        btnMic.setActivated(!micOn);
        btnMic.setContentDescription(getString(micOn ? R.string.call_mute : R.string.call_unmute));
    }

    private void toggleCamera() {
        cameraOn = !cameraOn;
        if (session != null) session.setCameraEnabled(cameraOn);
        btnCamera.setImageResource(cameraOn ? R.drawable.ic_videocam : R.drawable.ic_videocam_off);
        btnCamera.setActivated(!cameraOn);
        localView.setVisibility(cameraOn ? View.VISIBLE : View.INVISIBLE);
        btnCamera.setContentDescription(getString(cameraOn ? R.string.call_camera_off : R.string.call_camera_on));
    }

    private void setSpeaker(boolean on) {
        speaker = on;
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (am != null) am.setSpeakerphoneOn(on);
        btnSpeaker.setActivated(on);
    }

    private void enterCallAudio() {
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return;
        previousAudioMode = am.getMode();
        am.setMode(AudioManager.MODE_IN_COMMUNICATION);
        am.setSpeakerphoneOn(speaker);
    }

    private void leaveCallAudio() {
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return;
        am.setSpeakerphoneOn(false);
        am.setMode(previousAudioMode);
    }

    private void startRinging() {
        try {
            ringtone = RingtoneManager.getRingtone(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE));
            if (ringtone != null) ringtone.play();
        } catch (RuntimeException ignored) {
            // Không có nhạc chuông: vẫn hiện màn hình nghe máy
        }
        handler.postDelayed(() -> {
            if (!answered && !finished) toastAndFinish(R.string.call_status_missed);
        }, CallRecord.RING_TIMEOUT_MS);
    }

    private void stopRinging() {
        if (ringtone != null) {
            ringtone.stop();
            ringtone = null;
        }
    }

    /**
     * Kết thúc cuộc gọi về phía mình.
     *
     * @param noAnswer người gọi chờ quá lâu mà không ai nghe
     */
    private void endCall(boolean noAnswer) {
        if (finished) return;
        if (me != null && callRef != null && callCreated) {
            String status;
            Long duration = null;
            if (answered || connectedOnce) {
                status = CallRecord.ENDED;
                duration = connectedAt > 0 ? (SystemClock.elapsedRealtime() - connectedAt) / 1000 : 0L;
            } else if (incoming) {
                status = CallRecord.DECLINED;
            } else {
                status = noAnswer ? CallRecord.MISSED : CallRecord.CANCELLED;
            }
            repo.end(callRef, me, status, duration);
        }
        finishCall();
    }

    private void toastAndFinish(int message) {
        if (finished) return;
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        finishCall();
    }

    private void finishCall() {
        if (finished) return;
        finished = true;
        handler.removeCallbacksAndMessages(null);
        stopRinging();
        for (ListenerRegistration r : registrations) r.remove();
        registrations.clear();
        if (session != null) {
            session.dispose();
            session = null;
            leaveCallAudio();
        }
        finish();
    }

    private void setStatus(int text) {
        tvStatus.setText(text);
    }

    private boolean hasPermissions() {
        for (String p : requiredPermissions()) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) return false;
        }
        return true;
    }

    private String[] requiredPermissions() {
        return video ? new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA}
                : new String[]{Manifest.permission.RECORD_AUDIO};
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Ẩn màn hình gọi thì tạm tắt camera (không quay khi người dùng không thấy)
        if (session != null && video && cameraOn && !isFinishing()) session.pauseCamera(true);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (session != null && video && cameraOn) session.pauseCamera(false);
    }

    @Override
    protected void onDestroy() {
        // Thoát bất thường (ví dụ hệ thống đóng Activity): vẫn báo kết thúc cho bên kia
        if (!finished && !isChangingConfigurations()) endCall(false);
        super.onDestroy();
    }
}
