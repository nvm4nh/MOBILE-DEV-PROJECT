package com.seniortribe.app.activities;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.seniortribe.app.R;
import com.seniortribe.app.models.Post;
import com.seniortribe.app.utils.Constants;

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

        btnSubmitPost.setOnClickListener(v -> {
            String title = etPostTitle.getText().toString().trim();
            String content = etPostContent.getText().toString().trim();

            if (TextUtils.isEmpty(title) || TextUtils.isEmpty(content)) {
                Toast.makeText(this, "Vui lòng nhập tiêu đề và nội dung bài viết", Toast.LENGTH_SHORT).show();
                return;
            }

            Post post = new Post();
            post.setTitle(title);
            post.setContent(content);
            post.setAuthorOrg("Đại diện Tổ chức Thiện nguyện");
            post.setCreatedAt(System.currentTimeMillis());

            try {
                if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                    post.setAuthorUid(FirebaseAuth.getInstance().getCurrentUser().getUid());
                }
                FirebaseFirestore.getInstance().collection(Constants.COL_POSTS).add(post);
            } catch (Exception ignored) {}

            Toast.makeText(this, "Đã đăng bài viết lên Bảng tin cộng đồng!", Toast.LENGTH_SHORT).show();
            finish();
        });
    }
}
