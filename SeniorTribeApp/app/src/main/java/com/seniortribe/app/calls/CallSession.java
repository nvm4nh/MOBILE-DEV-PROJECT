package com.seniortribe.app.calls;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.seniortribe.app.R;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.DataChannel;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.MediaStreamTrack;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.RtpTransceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;
import org.webrtc.audio.JavaAudioDeviceModule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * E06 · Một phiên gọi WebRTC (thoại hoặc video) giữa hai máy.
 *
 * Lớp này chỉ lo phần âm thanh / hình ảnh và kết nối ngang hàng; việc trao đổi offer, answer,
 * ICE candidate qua Firestore nằm ở CallActivity + CallRepository. Mọi callback trả về luồng
 * chính. Gọi {@link #dispose()} đúng một lần khi kết thúc để trả camera, micro.
 */
public class CallSession {

    public enum State { CONNECTING, CONNECTED, DISCONNECTED, FAILED }

    public interface Listener {
        void onLocalCandidate(IceCandidate candidate);
        void onState(State state);
        void onRemoteVideo(VideoTrack track);
    }

    public interface SdpCallback {
        void onSdp(String sdp);
        void onError(String message);
    }

    private static boolean initialized;

    private final Context context;
    private final boolean video;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());

    private EglBase egl;
    private PeerConnectionFactory factory;
    private PeerConnection pc;
    private AudioSource audioSource;
    private AudioTrack audioTrack;
    private VideoCapturer capturer;
    private VideoSource videoSource;
    private VideoTrack videoTrack;
    private SurfaceTextureHelper textureHelper;
    @Nullable private SurfaceViewRenderer localView, remoteView;
    private boolean remoteSet, disposed;
    private final List<IceCandidate> pendingRemote = new ArrayList<>();

    public CallSession(Context context, boolean video, Listener listener) {
        this.context = context.getApplicationContext();
        this.video = video;
        this.listener = listener;
    }

    public EglBase.Context eglContext() {
        return egl.getEglBaseContext();
    }

    /** Khởi tạo micro (và camera nếu gọi video), gắn vào hai khung hình. */
    public void start(@Nullable SurfaceViewRenderer local, @Nullable SurfaceViewRenderer remote) {
        if (!initialized) {
            PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(context)
                    .createInitializationOptions());
            initialized = true;
        }
        egl = EglBase.create();
        factory = PeerConnectionFactory.builder()
                .setVideoEncoderFactory(new DefaultVideoEncoderFactory(egl.getEglBaseContext(), true, true))
                .setVideoDecoderFactory(new DefaultVideoDecoderFactory(egl.getEglBaseContext()))
                .setAudioDeviceModule(JavaAudioDeviceModule.builder(context)
                        .setUseHardwareAcousticEchoCanceler(true)
                        .setUseHardwareNoiseSuppressor(true)
                        .createAudioDeviceModule())
                .createPeerConnectionFactory();

        localView = local;
        remoteView = remote;
        if (remote != null) {
            remote.init(egl.getEglBaseContext(), null);
            remote.setEnableHardwareScaler(true);
        }
        if (local != null) {
            local.init(egl.getEglBaseContext(), null);
            local.setMirror(true);
            local.setZOrderMediaOverlay(true);
            local.setEnableHardwareScaler(true);
        }

        audioSource = factory.createAudioSource(new MediaConstraints());
        audioTrack = factory.createAudioTrack("st_audio", audioSource);
        if (video) startCamera();

        PeerConnection.RTCConfiguration config = new PeerConnection.RTCConfiguration(iceServers());
        config.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
        config.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;
        pc = factory.createPeerConnection(config, observer);
        if (pc == null) {
            post(() -> listener.onState(State.FAILED));
            return;
        }
        List<String> stream = Collections.singletonList("st_stream");
        pc.addTrack(audioTrack, stream);
        if (videoTrack != null) pc.addTrack(videoTrack, stream);
    }

    private void startCamera() {
        CameraEnumerator enumerator = new Camera2Enumerator(context);
        String chosen = null;
        for (String name : enumerator.getDeviceNames()) {
            if (enumerator.isFrontFacing(name)) { chosen = name; break; }
        }
        if (chosen == null && enumerator.getDeviceNames().length > 0) chosen = enumerator.getDeviceNames()[0];
        if (chosen == null) return; // máy không có camera: vẫn gọi được, chỉ không có hình mình
        capturer = enumerator.createCapturer(chosen, null);
        textureHelper = SurfaceTextureHelper.create("st_capture", egl.getEglBaseContext());
        videoSource = factory.createVideoSource(capturer.isScreencast());
        capturer.initialize(textureHelper, context, videoSource.getCapturerObserver());
        capturer.startCapture(640, 480, 24);
        videoTrack = factory.createVideoTrack("st_video", videoSource);
        if (localView != null) videoTrack.addSink(localView);
    }

    /** STUN công khai; TURN chỉ dùng khi nhóm điền cấu hình trong res/values/webrtc.xml. */
    private List<PeerConnection.IceServer> iceServers() {
        List<PeerConnection.IceServer> list = new ArrayList<>();
        list.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer());
        list.add(PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer());
        String turn = context.getString(R.string.webrtc_turn_url);
        if (!TextUtils.isEmpty(turn)) {
            list.add(PeerConnection.IceServer.builder(turn)
                    .setUsername(context.getString(R.string.webrtc_turn_username))
                    .setPassword(context.getString(R.string.webrtc_turn_password))
                    .createIceServer());
        }
        return list;
    }

    private MediaConstraints offerConstraints() {
        MediaConstraints c = new MediaConstraints();
        c.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        c.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", video ? "true" : "false"));
        return c;
    }

    // =====================================================================
    // Trao đổi SDP
    // =====================================================================

    /** Người gọi: tạo offer và đặt làm local description. */
    public void createOffer(SdpCallback cb) {
        if (pc == null) { cb.onError("Không khởi tạo được cuộc gọi."); return; }
        pc.createOffer(new SimpleSdp() {
            @Override public void onCreateSuccess(SessionDescription sdp) {
                pc.setLocalDescription(new SimpleSdp() {
                    @Override public void onSetSuccess() { post(() -> cb.onSdp(sdp.description)); }
                    @Override public void onSetFailure(String e) { post(() -> cb.onError(e)); }
                }, sdp);
            }
            @Override public void onCreateFailure(String e) { post(() -> cb.onError(e)); }
        }, offerConstraints());
    }

    /** Người nghe: nhận offer, tạo answer. */
    public void acceptOffer(String offer, SdpCallback cb) {
        if (pc == null) { cb.onError("Không khởi tạo được cuộc gọi."); return; }
        pc.setRemoteDescription(new SimpleSdp() {
            @Override public void onSetSuccess() {
                post(() -> {
                    remoteSet = true;
                    drainCandidates();
                });
                pc.createAnswer(new SimpleSdp() {
                    @Override public void onCreateSuccess(SessionDescription sdp) {
                        pc.setLocalDescription(new SimpleSdp() {
                            @Override public void onSetSuccess() { post(() -> cb.onSdp(sdp.description)); }
                            @Override public void onSetFailure(String e) { post(() -> cb.onError(e)); }
                        }, sdp);
                    }
                    @Override public void onCreateFailure(String e) { post(() -> cb.onError(e)); }
                }, offerConstraints());
            }
            @Override public void onSetFailure(String e) { post(() -> cb.onError(e)); }
        }, new SessionDescription(SessionDescription.Type.OFFER, offer));
    }

    /** Người gọi: nhận answer của bên kia. */
    public void setRemoteAnswer(String answer) {
        if (pc == null || remoteSet) return;
        pc.setRemoteDescription(new SimpleSdp() {
            @Override public void onSetSuccess() {
                post(() -> {
                    remoteSet = true;
                    drainCandidates();
                });
            }
        }, new SessionDescription(SessionDescription.Type.ANSWER, answer));
    }

    /** ICE candidate của bên kia; chờ tới khi đã có remote description. */
    public void addRemoteCandidate(String sdpMid, int index, String candidate) {
        IceCandidate c = new IceCandidate(sdpMid, index, candidate);
        if (pc == null) return;
        if (!remoteSet) pendingRemote.add(c);
        else pc.addIceCandidate(c);
    }

    private void drainCandidates() {
        if (pc == null) return;
        for (IceCandidate c : pendingRemote) pc.addIceCandidate(c);
        pendingRemote.clear();
    }

    // =====================================================================
    // Điều khiển
    // =====================================================================

    public void setMicEnabled(boolean on) {
        if (audioTrack != null) audioTrack.setEnabled(on);
    }

    public void setCameraEnabled(boolean on) {
        if (videoTrack != null) videoTrack.setEnabled(on);
    }

    public boolean hasCamera() {
        return videoTrack != null;
    }

    public void switchCamera() {
        if (capturer instanceof CameraVideoCapturer) {
            ((CameraVideoCapturer) capturer).switchCamera(null);
        }
    }

    /** Tạm dừng camera khi màn hình gọi bị ẩn, bật lại khi quay về. */
    public void pauseCamera(boolean pause) {
        if (capturer == null) return;
        try {
            if (pause) capturer.stopCapture();
            else capturer.startCapture(640, 480, 24);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Giải phóng camera, micro, kết nối. Gọi nhiều lần không sao. */
    public void dispose() {
        if (disposed) return;
        disposed = true;
        try {
            if (capturer != null) {
                try { capturer.stopCapture(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                capturer.dispose();
            }
            if (videoTrack != null) videoTrack.dispose();
            if (videoSource != null) videoSource.dispose();
            if (textureHelper != null) textureHelper.dispose();
            if (pc != null) pc.dispose();
            if (audioSource != null) audioSource.dispose();
            if (localView != null) localView.release();
            if (remoteView != null) remoteView.release();
            if (factory != null) factory.dispose();
            if (egl != null) egl.release();
        } catch (RuntimeException ignored) {
            // Thư viện đã tự giải phóng một phần khi kết nối hỏng
        }
        pc = null;
    }

    private void post(Runnable r) {
        main.post(() -> {
            if (!disposed) r.run();
        });
    }

    // =====================================================================

    private final PeerConnection.Observer observer = new PeerConnection.Observer() {
        @Override public void onSignalingChange(PeerConnection.SignalingState s) {}

        @Override public void onIceConnectionChange(PeerConnection.IceConnectionState s) {
            State st;
            switch (s) {
                case CONNECTED:
                case COMPLETED: st = State.CONNECTED; break;
                case DISCONNECTED: st = State.DISCONNECTED; break;
                case FAILED: st = State.FAILED; break;
                case CHECKING:
                case NEW: st = State.CONNECTING; break;
                default: return;
            }
            post(() -> listener.onState(st));
        }

        @Override public void onIceConnectionReceivingChange(boolean b) {}

        @Override public void onIceGatheringChange(PeerConnection.IceGatheringState s) {}

        @Override public void onIceCandidate(IceCandidate c) {
            post(() -> listener.onLocalCandidate(c));
        }

        @Override public void onIceCandidatesRemoved(IceCandidate[] c) {}

        @Override public void onAddStream(MediaStream s) {}

        @Override public void onRemoveStream(MediaStream s) {}

        @Override public void onDataChannel(DataChannel d) {}

        @Override public void onRenegotiationNeeded() {}

        @Override public void onAddTrack(RtpReceiver receiver, MediaStream[] streams) {}

        @Override public void onTrack(RtpTransceiver transceiver) {
            MediaStreamTrack track = transceiver.getReceiver().track();
            if (track instanceof VideoTrack) {
                VideoTrack vt = (VideoTrack) track;
                post(() -> {
                    if (remoteView != null) vt.addSink(remoteView);
                    listener.onRemoteVideo(vt);
                });
            }
        }
    };

    /** SdpObserver rỗng để chỉ viết phần cần dùng. */
    private abstract static class SimpleSdp implements SdpObserver {
        @Override public void onCreateSuccess(SessionDescription sdp) {}
        @Override public void onSetSuccess() {}
        @Override public void onCreateFailure(String error) {}
        @Override public void onSetFailure(String error) {}
    }
}
