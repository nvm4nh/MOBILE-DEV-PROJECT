package com.seniortribe.app.activities;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.seniortribe.app.R;
import com.seniortribe.app.utils.Constants;
import com.seniortribe.app.utils.ZoomImageView;
import com.seniortribe.app.viewmodels.AttachmentViewModel;

import java.io.OutputStream;

/**
 * E06 · Xem ảnh gửi trong chat. Ảnh chỉ nằm trong bộ nhớ khi xem (không lưu cache xuống máy,
 * F16); "Lưu ảnh về máy" là thao tác chủ động của người dùng, có nhắc trước.
 * Không cho chụp màn hình / hiện trong danh sách ứng dụng gần đây (FLAG_SECURE) vì ảnh có thể
 * chứa thông tin của người cao tuổi.
 */
public class ImageViewerActivity extends AppCompatActivity {

    public static final String EXTRA_ATTACHMENT_ID = "extra_attachment_id";
    public static final String EXTRA_FILE_NAME = "extra_file_name";

    private byte[] data;
    private String fileName;

    private final ActivityResultLauncher<String> saveLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("image/jpeg"), this::writeTo);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_image_viewer);

        String assignmentId = getIntent().getStringExtra(Constants.EXTRA_ASSIGNMENT_ID);
        String attachmentId = getIntent().getStringExtra(EXTRA_ATTACHMENT_ID);
        fileName = getIntent().getStringExtra(EXTRA_FILE_NAME);
        if (assignmentId == null || attachmentId == null) {
            finish();
            return;
        }

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        MenuItem save = toolbar.getMenu().add(R.string.viewer_save);
        save.setIcon(R.drawable.ic_download);
        save.getIcon().setTint(0xFFFFFFFF);
        save.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        save.setEnabled(false);
        save.setOnMenuItemClickListener(i -> {
            askSave();
            return true;
        });

        ZoomImageView image = findViewById(R.id.image);
        View loading = findViewById(R.id.layoutLoading);
        View progress = findViewById(R.id.progress);
        TextView message = findViewById(R.id.tvViewerMessage);

        AttachmentViewModel vm = new ViewModelProvider(this).get(AttachmentViewModel.class);
        vm.getState().observe(this, s -> {
            if (s == null) return;
            if (s.loading) {
                loading.setVisibility(View.VISIBLE);
                progress.setVisibility(View.VISIBLE);
                message.setText(R.string.viewer_loading);
                return;
            }
            if (s.data == null) {
                loading.setVisibility(View.VISIBLE);
                progress.setVisibility(View.GONE);
                message.setText(s.error != null ? s.error : getString(R.string.viewer_error));
                return;
            }
            Bitmap bmp = BitmapFactory.decodeByteArray(s.data, 0, s.data.length);
            if (bmp == null) {
                progress.setVisibility(View.GONE);
                message.setText(R.string.viewer_error);
                return;
            }
            data = s.data;
            loading.setVisibility(View.GONE);
            image.setImageBitmap(bmp);
            save.setEnabled(true);
        });
        vm.load(assignmentId, attachmentId);
    }

    private void askSave() {
        if (data == null) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.viewer_save)
                .setMessage(R.string.chat_file_save_warning)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.chat_file_save, (d, w) ->
                        saveLauncher.launch(fileName != null ? fileName : "anh.jpg"))
                .show();
    }

    private void writeTo(Uri uri) {
        if (uri == null || data == null) return;
        try (OutputStream out = getContentResolver().openOutputStream(uri)) {
            if (out == null) throw new java.io.IOException("no stream");
            out.write(data);
            Toast.makeText(this, getString(R.string.chat_file_saved, fileName != null ? fileName : ""), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, R.string.viewer_error, Toast.LENGTH_LONG).show();
        }
    }
}
