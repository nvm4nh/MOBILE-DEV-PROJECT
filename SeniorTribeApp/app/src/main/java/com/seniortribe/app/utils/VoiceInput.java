package com.seniortribe.app.utils;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.seniortribe.app.R;
import com.seniortribe.app.dialogs.VoiceInputSheet;

import java.util.ArrayList;

/**
 * E04 · Mở nhập bằng giọng nói cho một màn hình: xin quyền micro (giải thích trước nếu cần),
 * mở bảng "Đang nghe"; máy không có dịch vụ nhận giọng nói ngầm thì dùng hộp thoại nhận giọng nói
 * của hệ thống; không có cả hai thì báo để người dùng gõ.
 *
 * Phải tạo trong onCreate của Activity (đăng ký ActivityResultLauncher trước khi màn hình bắt đầu).
 */
public final class VoiceInput {

    public interface Callback {
        void onText(String text);
    }

    private final AppCompatActivity activity;
    private final String requestKey;
    private final int maxLength;
    private final View snackbarAnchor;
    private final Callback callback;
    private final ActivityResultLauncher<String> permissionLauncher;
    private final ActivityResultLauncher<Intent> systemLauncher;

    public VoiceInput(AppCompatActivity activity, String requestKey, int maxLength, View snackbarAnchor, Callback callback) {
        this.activity = activity;
        this.requestKey = requestKey;
        this.maxLength = maxLength;
        this.snackbarAnchor = snackbarAnchor;
        this.callback = callback;

        permissionLauncher = activity.registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            if (granted) open();
            else showDenied();
        });
        systemLauncher = activity.registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            Intent data = result.getData();
            if (result.getResultCode() != AppCompatActivity.RESULT_OK || data == null) return;
            ArrayList<String> list = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (list != null && !list.isEmpty() && list.get(0) != null && !list.get(0).trim().isEmpty()) {
                callback.onText(list.get(0).trim());
            }
        });
        // Nhận chữ từ bảng "Đang nghe" (vẫn nhận được sau khi xoay màn hình)
        activity.getSupportFragmentManager().setFragmentResultListener(requestKey, activity, (key, bundle) -> {
            String text = bundle.getString(VoiceInputSheet.RESULT_TEXT);
            if (text != null && !text.trim().isEmpty()) callback.onText(text.trim());
        });
    }

    /** Bấm "Nói để nhập nội dung". */
    public void start() {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            open();
        } else if (activity.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
            new MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.voice_permission_title)
                    .setMessage(R.string.voice_permission_message)
                    .setNegativeButton(R.string.action_cancel, null)
                    .setPositiveButton(R.string.voice_permission_allow,
                            (d, w) -> permissionLauncher.launch(Manifest.permission.RECORD_AUDIO))
                    .show();
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
        }
    }

    private void open() {
        if (SpeechRecognizer.isRecognitionAvailable(activity)) {
            if (activity.getSupportFragmentManager().findFragmentByTag(requestKey) == null) {
                VoiceInputSheet.newInstance(requestKey, maxLength).show(activity.getSupportFragmentManager(), requestKey);
            }
            return;
        }
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN");
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, activity.getString(R.string.voice_prompt));
        try {
            systemLauncher.launch(intent);
        } catch (ActivityNotFoundException e) {
            Snackbar.make(snackbarAnchor, R.string.voice_not_available, Snackbar.LENGTH_LONG).show();
        }
    }

    private void showDenied() {
        Snackbar.make(snackbarAnchor, R.string.voice_permission_denied, Snackbar.LENGTH_LONG)
                .setAction(R.string.voice_open_settings, v -> {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", activity.getPackageName(), null));
                    activity.startActivity(intent);
                })
                .show();
    }
}
