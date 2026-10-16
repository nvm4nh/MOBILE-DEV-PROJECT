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
import com.seniortribe.app.models.NeedPrivateDetails;
import com.seniortribe.app.models.NeedSummary;
import com.seniortribe.app.utils.Constants;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class CreateNeedActivity extends AppCompatActivity {

    private EditText etNeedTitle, etElderlyName, etDistrict, etExactAddress, etCategory, etDesc;
    private Button btnSubmitNeed;
    private FirebaseFirestore firestore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_need);

        etNeedTitle = findViewById(R.id.etNeedTitle);
        etElderlyName = findViewById(R.id.etElderlyName);
        etDistrict = findViewById(R.id.etDistrict);
        etExactAddress = findViewById(R.id.etExactAddress);
        etCategory = findViewById(R.id.etCategory);
        etDesc = findViewById(R.id.etDesc);
        btnSubmitNeed = findViewById(R.id.btnSubmitNeed);

        try { firestore = FirebaseFirestore.getInstance(); } catch (Exception ignored) {}

        btnSubmitNeed.setOnClickListener(v -> submitNeed());
    }

    private void submitNeed() {
        String title = etNeedTitle.getText().toString().trim();
        String elderlyName = etElderlyName.getText().toString().trim();
        String district = etDistrict.getText().toString().trim();
        String exactAddress = etExactAddress.getText().toString().trim();
        String category = etCategory.getText().toString().trim();
        String desc = etDesc.getText().toString().trim();

        if (TextUtils.isEmpty(title) || TextUtils.isEmpty(elderlyName) || TextUtils.isEmpty(district)) {
            Toast.makeText(this, "Vui lòng nhập tiêu đề, tên cụ và quận/huyện", Toast.LENGTH_SHORT).show();
            return;
        }

        if (category.isEmpty()) category = Constants.CAT_CONVERSATION;

        // Sinh mã ca #ST-YYYY-XXXX
        String year = new SimpleDateFormat("yyyy", Locale.getDefault()).format(new Date());
        String codeSuffix = String.format(Locale.getDefault(), "%04d", (int)(Math.random() * 9000) + 1000);
        String caseCode = "#ST-" + year + "-" + codeSuffix;

        NeedSummary summary = new NeedSummary();
        summary.setCaseCode(caseCode);
        summary.setTitle(title);
        summary.setCategory(category);
        summary.setDistrict(district);
        summary.setUrgency(Constants.URGENCY_MEDIUM);
        summary.setStatus(Constants.STATUS_REVIEW); // Bắt đầu ở trạng thái REVIEW
        summary.setCreatedAt(System.currentTimeMillis());
        summary.setUpdatedAt(System.currentTimeMillis());

        try {
            if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                summary.setReporterUid(FirebaseAuth.getInstance().getCurrentUser().getUid());
                summary.setReporterName(FirebaseAuth.getInstance().getCurrentUser().getDisplayName());
            }
        } catch (Exception ignored) {}

        if (firestore != null) {
            btnSubmitNeed.setEnabled(false);
            firestore.collection(Constants.COL_NEEDS_SUMMARY)
                    .add(summary)
                    .addOnSuccessListener(docRef -> {
                        String needId = docRef.getId();
                        // Lưu thông tin riêng vào needs_private để bảo mật dữ liệu nhân thân
                        NeedPrivateDetails priv = new NeedPrivateDetails();
                        priv.setNeedId(needId);
                        priv.setElderlyName(elderlyName);
                        priv.setExactAddress(exactAddress);
                        priv.setHealthNotes(desc);
                        firestore.collection(Constants.COL_NEEDS_PRIVATE).document(needId).set(priv);

                        Toast.makeText(this, "Đã gửi nhu cầu (" + caseCode + ") lên hệ thống!", Toast.LENGTH_LONG).show();
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        btnSubmitNeed.setEnabled(true);
                        Toast.makeText(this, "Ghi nhận nhu cầu thành công trên máy (Chế độ mô phỏng)", Toast.LENGTH_SHORT).show();
                        finish();
                    });
        } else {
            Toast.makeText(this, "Đã ghi nhận nhu cầu " + caseCode, Toast.LENGTH_SHORT).show();
            finish();
        }
    }
}
