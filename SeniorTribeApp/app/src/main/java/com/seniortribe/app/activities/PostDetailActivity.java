package com.seniortribe.app.activities;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.seniortribe.app.R;
import com.seniortribe.app.models.Post;
import com.seniortribe.app.utils.Constants;
import com.seniortribe.app.utils.DateTimeUtils;

public class PostDetailActivity extends AppCompatActivity {

    private TextView tvAuthorOrg, tvPostDate, tvPostTitle, tvPostContent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_post_detail);

        tvAuthorOrg = findViewById(R.id.tvAuthorOrg);
        tvPostDate = findViewById(R.id.tvPostDate);
        tvPostTitle = findViewById(R.id.tvPostTitle);
        tvPostContent = findViewById(R.id.tvPostContent);

        Post post = (Post) getIntent().getSerializableExtra(Constants.EXTRA_POST);
        if (post != null) {
            tvAuthorOrg.setText(post.getAuthorOrg() != null ? post.getAuthorOrg() : "Tổ chức thiện nguyện");
            tvPostDate.setText(DateTimeUtils.formatDate(post.getCreatedAt()));
            tvPostTitle.setText(post.getTitle());
            tvPostContent.setText(post.getContent());
        }
    }
}
