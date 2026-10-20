package vn.edu.ueh.quyenong.f04;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.TextView;

public class DetailActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);

        ImageButton btnBack = findViewById(R.id.btnBack);

        TextView tvOrganization =
                findViewById(R.id.tvDetailOrganization);
        TextView tvTitle =
                findViewById(R.id.tvDetailTitle);
        TextView tvContent =
                findViewById(R.id.tvDetailContent);

        String organization = getIntent().getStringExtra("organization");
        String title = getIntent().getStringExtra("title");
        String content = getIntent().getStringExtra("content");

        if (organization != null) {
            tvOrganization.setText(organization);
        }

        if (title != null) {
            tvTitle.setText(title);
        }

        if (content != null) {
            tvContent.setText(content);
        }

        btnBack.setOnClickListener(v -> finish());
    }
}
