package com.seniortribe.app.activities;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.FirebaseFirestore;
import com.seniortribe.app.R;
import com.seniortribe.app.data.MembershipRepository;
import com.seniortribe.app.data.NeedActionException;
import com.seniortribe.app.data.NeedErrors;
import com.seniortribe.app.models.Membership;
import com.seniortribe.app.utils.Constants;
import com.seniortribe.app.utils.NetworkUtils;

import java.util.HashMap;
import java.util.Map;

public class CreatePostActivity extends AppCompatActivity {

    private EditText etPostTitle, etPostContent;
    private Button btnSubmitPost;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_post);

        etPostTitle = findViewById(R.id.etPostTitle);
        etPostContent = findViewById(R.id.etPostContent);
        btnSubmitPost = findViewById(R.id.btnSubmitPost);

        btnSubmitPost.setOnClickListener(v -> submit());
    }

    private void submit() {
        String title = etPostTitle.getText().toString().trim();
        String content = etPostContent.getText().toString().trim();

        if (TextUtils.isEmpty(title) || TextUtils.isEmpty(content)) {
            Toast.makeText(this, "Vui lòng nhập tiêu đề và nội dung bài viết", Toast.LENGTH_SHORT).show();
            return;
        }
        // Đăng bài phải được máy chủ xác nhận, không báo thành công khi offline.
        if (!NetworkUtils.isNetworkAvailable(this)) {
            Toast.makeText(this, "Chưa có kết nối mạng. Kết nối rồi thử đăng lại.", Toast.LENGTH_LONG).show();
            return;
        }

        btnSubmitPost.setEnabled(false);
        MembershipRepository.get().loadCurrent(false)
                .continueWithTask(task -> {
                    Membership membership = task.getResult();
                    if (membership == null || !membership.isRepresentative()) {
                        throw new NeedActionException(
                                "Chỉ đại diện tổ chức mới được đăng bài lên Bảng tin.");
                    }

                    Map<String, Object> post = new HashMap<>();
                    post.put("orgId", membership.getOrgId());
                    post.put("authorOrg", membership.getOrgName());
                    post.put("authorUid", membership.getUid());
                    post.put("title", title);
                    post.put("content", content);
                    post.put("createdAt", System.currentTimeMillis());
                    return FirebaseFirestore.getInstance().collection(Constants.COL_POSTS).add(post);
                })
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(this, "Đã đăng bài viết lên Bảng tin cộng đồng!", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }
                    btnSubmitPost.setEnabled(true);
                    Toast.makeText(this, NeedErrors.message(task.getException()), Toast.LENGTH_LONG).show();
                });
    }
}
