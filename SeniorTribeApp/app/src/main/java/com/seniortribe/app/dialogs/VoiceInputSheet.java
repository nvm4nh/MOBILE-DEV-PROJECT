package com.seniortribe.app.dialogs;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.seniortribe.app.R;
import com.seniortribe.app.views.VoiceWaveView;

import java.util.ArrayList;
import java.util.Locale;

/**
 * E04 · Bảng "Đang nghe": chuyển lời nói tiếng Việt thành chữ bằng SpeechRecognizer của máy.
 *
 *  - Hiện chữ đang nhận ngay khi nói (kết quả tạm), sóng âm theo độ lớn giọng và thời gian đã nghe.
 *  - Nói liền nhiều câu: hết một câu máy tự nghe tiếp cho tới khi bấm "Dừng".
 *  - "Dùng nội dung" trả chữ về màn hình gọi (Fragment Result API) để người nhập đọc lại và sửa.
 * Âm thanh không được lưu: chỉ chữ được trả về.
 */
public class VoiceInputSheet extends BottomSheetDialogFragment {

    public static final String RESULT_TEXT = "voice_text";

    private static final String ARG_REQUEST_KEY = "request_key";
    private static final String ARG_MAX_LENGTH = "max_length";
    private static final String STATE_TEXT = "committed_text";
    private static final String STATE_ELAPSED = "elapsed";
    private static final long MAX_LISTEN_MS = 3 * 60 * 1000L;

    private static final int MODE_PREPARING = 0;
    private static final int MODE_LISTENING = 1;
    private static final int MODE_PROCESSING = 2;
    private static final int MODE_STOPPED = 3;
    private static final int MODE_ERROR = 4;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private SpeechRecognizer recognizer;
    private final StringBuilder committed = new StringBuilder();
    private String partial = "";
    private int mode = MODE_PREPARING;
    private boolean wantListening;
    private long elapsedBefore;
    private long startedAt;

    private TextView tvState, tvMeta, tvTranscript, tvError;
    private ImageView ivMic;
    private View btnMic;
    private VoiceWaveView wave;
    private MaterialButton btnStop, btnUse;

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            renderMeta();
            if (elapsedMillis() >= MAX_LISTEN_MS && mode == MODE_LISTENING) stopListening();
            if (wantListening) handler.postDelayed(this, 1000);
        }
    };

    public static VoiceInputSheet newInstance(String requestKey, int maxLength) {
        Bundle args = new Bundle();
        args.putString(ARG_REQUEST_KEY, requestKey);
        args.putInt(ARG_MAX_LENGTH, maxLength);
        VoiceInputSheet sheet = new VoiceInputSheet();
        sheet.setArguments(args);
        return sheet;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_voice_input, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        tvState = view.findViewById(R.id.tvVoiceState);
        tvMeta = view.findViewById(R.id.tvVoiceMeta);
        tvTranscript = view.findViewById(R.id.tvVoiceTranscript);
        tvError = view.findViewById(R.id.tvVoiceError);
        ivMic = view.findViewById(R.id.ivVoiceMic);
        btnMic = view.findViewById(R.id.btnVoiceMic);
        wave = view.findViewById(R.id.voiceWave);
        btnStop = view.findViewById(R.id.btnVoiceStop);
        btnUse = view.findViewById(R.id.btnVoiceUse);

        btnMic.setOnClickListener(v -> toggle());
        btnStop.setOnClickListener(v -> toggle());
        btnUse.setOnClickListener(v -> deliver());

        recognizer = SpeechRecognizer.createSpeechRecognizer(requireContext());
        recognizer.setRecognitionListener(listener);

        if (savedInstanceState != null) {
            // Xoay màn hình: giữ chữ đã nhận, chờ người dùng bấm "Nói tiếp"
            committed.append(savedInstanceState.getString(STATE_TEXT, ""));
            elapsedBefore = savedInstanceState.getLong(STATE_ELAPSED);
            mode = MODE_STOPPED;
            render();
        } else {
            startListening();
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_TEXT, currentText());
        outState.putLong(STATE_ELAPSED, elapsedMillis());
    }

    @Override
    public void onDestroyView() {
        wantListening = false;
        handler.removeCallbacksAndMessages(null);
        if (recognizer != null) {
            recognizer.cancel();
            recognizer.destroy();
            recognizer = null;
        }
        super.onDestroyView();
    }

    // =====================================================================
    // Nghe
    // =====================================================================

    private void toggle() {
        if (wantListening) stopListening();
        else startListening();
    }

    private void startListening() {
        if (recognizer == null) return;
        if (!wantListening) {
            wantListening = true;
            startedAt = System.currentTimeMillis();
            handler.removeCallbacks(ticker);
            handler.post(ticker);
        }
        mode = MODE_PREPARING;
        render();
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN");
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "vi-VN");
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L);
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, requireContext().getPackageName());
        recognizer.startListening(intent);
    }

    private void stopListening() {
        boolean wasListening = wantListening;
        wantListening = false;
        if (wasListening) elapsedBefore += System.currentTimeMillis() - startedAt;
        handler.removeCallbacks(ticker);
        if (recognizer != null) recognizer.stopListening();
        mode = partial.isEmpty() ? MODE_STOPPED : MODE_PROCESSING;
        render();
    }

    private final RecognitionListener listener = new RecognitionListener() {
        @Override public void onReadyForSpeech(Bundle params) {
            mode = MODE_LISTENING;
            tvError.setVisibility(View.GONE);
            render();
        }

        @Override public void onBeginningOfSpeech() {}

        @Override public void onRmsChanged(float rmsdB) {
            if (wave != null && mode == MODE_LISTENING) wave.push(rmsdB);
        }

        @Override public void onBufferReceived(byte[] buffer) {}

        @Override public void onEndOfSpeech() {
            if (mode == MODE_LISTENING) {
                mode = MODE_PROCESSING;
                render();
            }
        }

        @Override public void onError(int error) {
            boolean silence = error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT;
            if (silence && wantListening && elapsedMillis() < MAX_LISTEN_MS && currentText().length() > 0) {
                // Ngừng nói một lúc giữa hai câu: nghe tiếp
                commitPartial();
                handler.postDelayed(() -> { if (wantListening) startListening(); }, 250);
                return;
            }
            commitPartial();
            if (wantListening) elapsedBefore += System.currentTimeMillis() - startedAt;
            wantListening = false;
            handler.removeCallbacks(ticker);
            if (silence && currentText().length() > 0) {
                mode = MODE_STOPPED;
            } else {
                mode = MODE_ERROR;
                tvError.setText(errorMessage(error));
                tvError.setVisibility(View.VISIBLE);
            }
            render();
        }

        @Override public void onResults(Bundle results) {
            String best = first(results);
            partial = best != null ? best : partial;
            commitPartial();
            if (wantListening && elapsedMillis() < MAX_LISTEN_MS && currentText().length() < maxLength()) {
                handler.postDelayed(() -> { if (wantListening) startListening(); }, 250);
                render();
            } else {
                if (wantListening) elapsedBefore += System.currentTimeMillis() - startedAt;
                wantListening = false;
                handler.removeCallbacks(ticker);
                mode = MODE_STOPPED;
                render();
            }
        }

        @Override public void onPartialResults(Bundle partialResults) {
            String best = first(partialResults);
            if (best != null) {
                partial = best;
                renderTranscript();
            }
        }

        @Override public void onEvent(int eventType, Bundle params) {}
    };

    private void commitPartial() {
        String p = partial.trim();
        partial = "";
        if (p.isEmpty()) return;
        if (committed.length() > 0) committed.append(endsSentence(committed) ? " " : ", ");
        committed.append(p);
    }

    private static boolean endsSentence(CharSequence s) {
        char c = s.charAt(s.length() - 1);
        return c == '.' || c == '!' || c == '?';
    }

    @Nullable
    private static String first(Bundle results) {
        if (results == null) return null;
        ArrayList<String> list = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        return list != null && !list.isEmpty() ? list.get(0) : null;
    }

    private String currentText() {
        String p = partial.trim();
        if (p.isEmpty()) return committed.toString();
        if (committed.length() == 0) return p;
        return committed + (endsSentence(committed) ? " " : ", ") + p;
    }

    private int maxLength() {
        return requireArguments().getInt(ARG_MAX_LENGTH, 1000);
    }

    private long elapsedMillis() {
        return elapsedBefore + (wantListening ? System.currentTimeMillis() - startedAt : 0);
    }

    private int errorMessage(int error) {
        switch (error) {
            case SpeechRecognizer.ERROR_NO_MATCH:
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                return R.string.voice_error_no_match;
            case SpeechRecognizer.ERROR_NETWORK:
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
            case SpeechRecognizer.ERROR_SERVER:
                return R.string.voice_error_network;
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                return R.string.voice_error_busy;
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                return R.string.voice_error_permission;
            default:
                return R.string.voice_error_generic;
        }
    }

    // =====================================================================
    // Hiển thị
    // =====================================================================

    private void render() {
        if (tvState == null) return;
        switch (mode) {
            case MODE_PREPARING: tvState.setText(R.string.voice_preparing); break;
            case MODE_LISTENING: tvState.setText(R.string.voice_listening); break;
            case MODE_PROCESSING: tvState.setText(R.string.voice_processing); break;
            case MODE_ERROR: tvState.setText(R.string.voice_error_title); break;
            default: tvState.setText(R.string.voice_stopped); break;
        }
        boolean active = wantListening;
        ivMic.setImageResource(active ? R.drawable.ic_stop : R.drawable.ic_mic);
        btnMic.setAlpha(mode == MODE_PROCESSING ? 0.6f : 1f);
        wave.setListening(mode == MODE_LISTENING);
        btnStop.setText(active ? R.string.voice_stop : R.string.voice_continue);
        if (mode != MODE_ERROR) tvError.setVisibility(View.GONE);
        renderMeta();
        renderTranscript();
    }

    private void renderMeta() {
        if (tvMeta == null) return;
        long s = elapsedMillis() / 1000;
        tvMeta.setText(getString(R.string.voice_meta, String.format(Locale.ROOT, "%02d:%02d", s / 60, s % 60)));
    }

    private void renderTranscript() {
        if (tvTranscript == null) return;
        String text = currentText();
        if (text.isEmpty()) {
            tvTranscript.setText(R.string.voice_transcript_empty);
            tvTranscript.setTextColor(requireContext().getColor(R.color.text_hint));
        } else {
            tvTranscript.setText(text);
            tvTranscript.setTextColor(requireContext().getColor(R.color.text_primary));
        }
        btnUse.setEnabled(!text.trim().isEmpty());
    }

    private void deliver() {
        commitPartial();
        String text = committed.toString().trim();
        if (text.isEmpty()) return;
        wantListening = false;
        if (recognizer != null) recognizer.cancel();
        Bundle result = new Bundle();
        result.putString(RESULT_TEXT, text);
        getParentFragmentManager().setFragmentResult(requireArguments().getString(ARG_REQUEST_KEY), result);
        dismiss();
    }
}
