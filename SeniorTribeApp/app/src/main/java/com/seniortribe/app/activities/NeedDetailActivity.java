package com.seniortribe.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.seniortribe.app.R;
import com.seniortribe.app.models.NeedSummary;
import com.seniortribe.app.utils.Constants;

public class NeedDetailActivity extends AppCompatActivity {

    private TextView tvCaseCode, tvTitle, tvStatusBadge, tvCategory, tvDistrict, tvReporter, tvWorkflowStep;
    private Button btnAcceptTransaction, btnOpenChat, btnCancelNeed;

    private NeedSummary currentNeed;
    private FirebaseFirestore firestore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_need_detail);

        tvCaseCode = findViewById(R.id.tvCaseCode);
        tvTitle = findViewById(R.id.tvTitle);
        tvStatusBadge = findViewById(R.id.tvStatusBadge);
        tvCategory = findViewById(R.id.tvCategory);
        tvDistrict = findViewById(R.id.tvDistrict);
        tvReporter = findViewById(R.id.tvReporter);
        tvWorkflowStep = findViewById(R.id.tvWorkflowStep);
        btnAcceptTransaction = findViewById(R.id.btnAcceptTransaction);
        btnOpenChat = findViewById(R.id.btnOpenChat);
        btnCancelNeed = findViewById(R.id.btnCancelNeed);

        try { firestore = FirebaseFirestore.getInstance(); } catch (Exception ignored) {}

        currentNeed = (NeedSummary) getIntent().getSerializableExtra(Constants.EXTRA_NEED);
        if (currentNeed == null) {
            currentNeed = new NeedSummary();
            currentNeed.setNeedId("sample");
            currentNeed.setCaseCode("#ST-2026-001");
            currentNeed.setTitle("Thăm hỏi và trò chuyện định kỳ cùng cụ bà neo đơn");
            currentNeed.setCategory(Constants.CAT_CONVERSATION);
            currentNeed.setDistrict("Quận 8, TP.HCM");
            currentNeed.setStatus(Constants.STATUS_OPEN);
        }

        bindData();
        setupActions();
    }

    private void bindData() {
        tvCaseCode.setText(currentNeed.getCaseCode() != null ? currentNeed.getCaseCode() : "#ST-NEED");
        tvTitle.setText(currentNeed.getTitle());
        tvCategory.setText("• Loại hình: " + (currentNeed.getCategory() != null ? currentNeed.getCategory() : "Hỗ trợ"));
        tvDistrict.setText("• Địa bàn: " + (currentNeed.getDistrict() != null ? currentNeed.getDistrict() : "TP.HCM"));
        if (tvReporter != null) {
            tvReporter.setText("• Người phụ trách: " + (currentNeed.getReporterName() != null ? currentNeed.getReporterName() : "Đầu mối cơ sở"));
        }

        String st = currentNeed.getStatus() != null ? currentNeed.getStatus() : Constants.STATUS_OPEN;
        tvStatusBadge.setText("Trạng thái: " + st);

        if (tvWorkflowStep != null) {
            switch (st) {
                case Constants.STATUS_REVIEW:
                    tvWorkflowStep.setText("Bước 1: REVIEW ➔ Đang chờ điều phối viên rà soát");
                    break;
                case Constants.STATUS_OPEN:
                    tvWorkflowStep.setText("Bước 2: OPEN ➔ Sẵn sàng cho tổ chức tiếp nhận hỗ trợ");
                    break;
                case Constants.STATUS_ACCEPTED:
                    tvWorkflowStep.setText("Bước 3: ACCEPTED ➔ Hai đầu mối mở chat và lên lịch");
                    break;
                case Constants.STATUS_SCHEDULED:
                    tvWorkflowStep.setText("Bước 4: SCHEDULED ➔ Đã chốt lịch hẹn thăm cụ");
                    break;
                case Constants.STATUS_DELIVERED:
                    tvWorkflowStep.setText("Bước 5: DELIVERED ➔ Đã hỗ trợ, chờ đầu mối đối soát kết quả");
                    break;
                case Constants.STATUS_COMPLETED:
                    tvWorkflowStep.setText("Bước 6: COMPLETED ➔ Hoàn thành ca hỗ trợ");
                    break;
                default:
                    tvWorkflowStep.setText("Trạng thái hiện tại: " + st);
                    break;
            }
        }

        // Ẩn hiện nút phù hợp theo quy trình
        if (Constants.STATUS_OPEN.equals(st)) {
            btnAcceptTransaction.setVisibility(View.VISIBLE);
            btnOpenChat.setVisibility(View.GONE);
        } else if (Constants.STATUS_ACCEPTED.equals(st) || Constants.STATUS_SCHEDULED.equals(st) || Constants.STATUS_DELIVERED.equals(st)) {
            btnAcceptTransaction.setVisibility(View.GONE);
            btnOpenChat.setVisibility(View.VISIBLE);
        } else {
            btnAcceptTransaction.setVisibility(View.GONE);
            btnOpenChat.setVisibility(View.GONE);
        }
    }

    private void setupActions() {
        // Tiếp nhận bằng Firestore Transaction (Chống xung đột nhận trùng - Chuẩn Mục 4.3.b)
        btnAcceptTransaction.setOnClickListener(v -> acceptNeedTransaction());

        btnOpenChat.setOnClickListener(v -> {
            Intent intent = new Intent(this, ChatActivity.class);
            intent.putExtra(Constants.EXTRA_NEED_ID, currentNeed.getNeedId());
            intent.putExtra(Constants.EXTRA_NEED, currentNeed);
            startActivity(intent);
        });

        btnCancelNeed.setOnClickListener(v -> {
            currentNeed.setStatus(Constants.STATUS_CANCELLED);
            bindData();
            Toast.makeText(this, "Đã ghi nhận yêu cầu hủy/trả lại ca", Toast.LENGTH_SHORT).show();
        });
    }

    private void acceptNeedTransaction() {
        btnAcceptTransaction.setEnabled(false);
        if (firestore == null || currentNeed.getNeedId() == null) {
            currentNeed.setStatus(Constants.STATUS_ACCEPTED);
            bindData();
            Toast.makeText(this, "Tiếp nhận thành công ca hỗ trợ!", Toast.LENGTH_SHORT).show();
            return;
        }

        DocumentReference needRef = firestore.collection(Constants.COL_NEEDS_SUMMARY).document(currentNeed.getNeedId());

        firestore.runTransaction(transaction -> {
            DocumentSnapshot snapshot = transaction.get(needRef);
            String status = snapshot.getString("status");
            if (!Constants.STATUS_OPEN.equals(status)) {
                throw new FirebaseFirestoreException("Nhu cầu này đã có đơn vị khác tiếp nhận!",
                        FirebaseFirestoreException.Code.ABORTED);
            }
            String myUid = FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "assignee_uid";
            transaction.update(needRef, "status", Constants.STATUS_ACCEPTED);
            transaction.update(needRef, "assigneeUid", myUid);
            return null;
        }).addOnSuccessListener(aVoid -> {
            currentNeed.setStatus(Constants.STATUS_ACCEPTED);
            bindData();
            Toast.makeText(this, "Tiếp nhận thành công ca hỗ trợ!", Toast.LENGTH_SHORT).show();
        }).addOnFailureListener(e -> {
            btnAcceptTransaction.setEnabled(true);
            Toast.makeText(this, "Không thể tiếp nhận: " + e.getMessage(), Toast.LENGTH_LONG).show();
        });
    }
}
