package vn.edu.ueh.quyenong.f04;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

public class CreatePostActivity extends Activity {

    private EditText etTitle;
    private EditText etContent;
    private TextView btnPublish;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_post);

        etTitle = findViewById(R.id.etTitle);
        etContent = findViewById(R.id.etContent);
        btnPublish = findViewById(R.id.btnPublish);

        ImageButton btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());
        btnPublish.setOnClickListener(v -> publishPost());
    }

    private void publishPost() {
        String title = etTitle.getText().toString().trim();
        String content = etContent.getText().toString().trim();

        if (title.isEmpty()) {
            etTitle.setError("Vui lòng nhập tiêu đề bài đăng");
            etTitle.requestFocus();
            showKeyboard(etTitle);
            return;
        }

        if (content.isEmpty()) {
            etContent.setError("Vui lòng nhập nội dung bài đăng");
            etContent.requestFocus();
            showKeyboard(etContent);
            return;
        }

        hideKeyboard();
        Toast.makeText(this,
                "Thông tin hợp lệ. Có thể đăng bài.",
                Toast.LENGTH_SHORT).show();
    }

    private void showKeyboard(View view) {
        view.post(() -> {
            InputMethodManager imm = (InputMethodManager)
                    getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
            }
        });
    }

    private void hideKeyboard() {
        View view = getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager)
                    getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
            }
        }
    }
}
