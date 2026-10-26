package com.seniortribe.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.seniortribe.app.R;
import com.seniortribe.app.data.FeedbackRepository;
import com.seniortribe.app.models.NeedSummary;
import com.seniortribe.app.models.OrgRating;
import com.seniortribe.app.utils.Constants;
import com.seniortribe.app.utils.DateTimeUtils;
import com.seniortribe.app.utils.NeedStatus;
import com.seniortribe.app.utils.OrgAvatar;
import com.seniortribe.app.utils.PrivacyGuard;
import com.seniortribe.app.utils.StateView;
import com.seniortribe.app.viewmodels.RateOrgViewModel;

import java.util.ArrayList;
import java.util.List;

/**
 * E03 · Đánh giá tổ chức sau khi nhu cầu hoàn thành.
 *  - Chỉ đầu mối của đơn vị ghi nhận; mỗi lần tiếp nhận chỉ đánh giá một lần.
 *  - Chọn số sao theo ý kiến của cụ, chọn điều cụ thấy tốt, nhận xét không bắt buộc.
 *  - Phải xác nhận "Đã hỏi ý kiến cụ" trước khi gửi.
 * Đánh giá không hiện trên bảng tin; chỉ hai bên và điều phối viên xem được.
 */
public class RateOrgActivity extends AppCompatActivity {

    private static final int[] LEVELS = {
            R.string.rate_level_0, R.string.rate_level_1, R.string.rate_level_2,
            R.string.rate_level_3, R.string.rate_level_4, R.string.rate_level_5
    };

    private RateOrgViewModel viewModel;
    private String needId;

    private MaterialToolbar toolbar;
    private View root, scroll;
    private TextView tvStatus, tvCompletedOn, tvAvatar, tvOrgName, tvAssignee, tvExisting;
    private TextView tvStarLevel, tvStarHint, tvElderError;
    private final ImageButton[] stars = new ImageButton[5];
    private ChipGroup chipGroupTags;
    private TextInputLayout tilComment;
    private TextInputEditText etComment;
    private CheckBox cbElderAsked;
    private MaterialButton btnSubmit, btnReport;
    private StateView stateView;
    private boolean tagsBuilt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rate_org);
        needId = getIntent().getStringExtra(Constants.EXTRA_NEED_ID);
        if (needId == null) {
            finish();
            return;
        }
        bindViews();
        buildTags();

        toolbar.setNavigationOnClickListener(v -> finish());
        for (int i = 0; i < stars.length; i++) {
            final int value = i + 1;
            stars[i].setContentDescription(getString(R.string.rate_star_desc, value));
            stars[i].setOnClickListener(v -> {
                viewModel.setStars(value);
                renderStars(value);
            });
        }
        cbElderAsked.setOnCheckedChangeListener((b, checked) -> {
            if (checked) tvElderError.setVisibility(View.GONE);
        });
        btnSubmit.setOnClickListener(v -> submit());
        btnReport.setOnClickListener(v -> startActivity(new Intent(this, ReportFormActivity.class)
                .putExtra(Constants.EXTRA_NEED_ID, needId)));

        viewModel = new ViewModelProvider(this).get(RateOrgViewModel.class);
        renderStars(viewModel.getStars());
        viewModel.getUi().observe(this, this::render);
        viewModel.getMessages().observe(this, e -> {
            String msg = e.getIfNotHandled();
            if (msg != null) Snackbar.make(root, msg, Snackbar.LENGTH_LONG).show();
        });
        viewModel.getSubmitted().observe(this, e -> {
            if (e.getIfNotHandled() == null) return;
            Snackbar.make(root, R.string.rate_done, Snackbar.LENGTH_LONG).show();
            root.postDelayed(this::finish, 900);
        });
        viewModel.start(needId);
    }

    private void bindViews() {
        root = findViewById(R.id.rootRateOrg);
        toolbar = findViewById(R.id.toolbar);
        scroll = findViewById(R.id.scrollRate);
        tvStatus = findViewById(R.id.tvRateStatus);
        tvCompletedOn = findViewById(R.id.tvCompletedOn);
        tvAvatar = findViewById(R.id.tvOrgAvatar);
        tvOrgName = findViewById(R.id.tvOrgName);
        tvAssignee = findViewById(R.id.tvAssignee);
        tvExisting = findViewById(R.id.tvExisting);
        stars[0] = findViewById(R.id.btnStar1);
        stars[1] = findViewById(R.id.btnStar2);
        stars[2] = findViewById(R.id.btnStar3);
        stars[3] = findViewById(R.id.btnStar4);
        stars[4] = findViewById(R.id.btnStar5);
        tvStarLevel = findViewById(R.id.tvStarLevel);
        tvStarHint = findViewById(R.id.tvStarHint);
        chipGroupTags = findViewById(R.id.chipGroupTags);
        tilComment = findViewById(R.id.tilComment);
        etComment = findViewById(R.id.etComment);
        cbElderAsked = findViewById(R.id.cbElderAsked);
        tvElderError = findViewById(R.id.tvElderError);
        btnSubmit = findViewById(R.id.btnSubmitRating);
        btnReport = findViewById(R.id.btnReport);
        stateView = new StateView(findViewById(R.id.frameRate));
    }

    /** Chip "Điều cụ thấy tốt" tạo từ danh sách trong strings; id cố định để giữ lựa chọn khi xoay. */
    private void buildTags() {
        if (tagsBuilt) return;
        tagsBuilt = true;
        String[] tags = getResources().getStringArray(R.array.rate_tags);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < tags.length; i++) {
            Chip chip = (Chip) inflater.inflate(R.layout.item_choice_chip, chipGroupTags, false);
            chip.setId(View.generateViewId());
            chip.setText(tags[i]);
            chip.setTag(tags[i]);
            chipGroupTags.addView(chip);
        }
    }

    private List<String> selectedTags() {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < chipGroupTags.getChildCount(); i++) {
            View child = chipGroupTags.getChildAt(i);
            if (child instanceof Chip && ((Chip) child).isChecked()) out.add(String.valueOf(child.getTag()));
        }
        return out;
    }

    // =====================================================================
    // Hiển thị
    // =====================================================================

    private void render(RateOrgViewModel.Ui s) {
        if (s.loading) {
            scroll.setVisibility(View.INVISIBLE);
            stateView.showLoading();
            return;
        }
        if (s.error != null && s.need == null) {
            scroll.setVisibility(View.INVISIBLE);
            stateView.showError(getString(R.string.state_error_title), s.error,
                    getString(R.string.action_retry), v -> viewModel.retry());
            return;
        }
        NeedSummary n = s.need;
        if (n == null) {
            finish();
            return;
        }
        if (s.existing == null && !FeedbackRepository.canRate(s.membership, n)) {
            scroll.setVisibility(View.INVISIBLE);
            stateView.showMessage(R.drawable.ic_lock, getString(R.string.rate_title_plain),
                    getString(R.string.rate_not_allowed), null, null);
            return;
        }
        stateView.hide();
        scroll.setVisibility(View.VISIBLE);

        if (!TextUtils.isEmpty(n.getCaseCode())) toolbar.setTitle(getString(R.string.rate_title, n.getCaseCode()));
        NeedStatus.bindChip(tvStatus, n.getStatus());
        tvCompletedOn.setText(n.getCompletedAt() != null
                ? getString(R.string.rate_completed_on, DateTimeUtils.formatDate(n.getCompletedAt().getTime())) : "");
        tvAvatar.setText(OrgAvatar.initials(n.getAssignedOrgName()));
        tvOrgName.setText(n.getAssignedOrgName());
        boolean hasAssignee = !TextUtils.isEmpty(n.getAssigneeName());
        tvAssignee.setVisibility(hasAssignee ? View.VISIBLE : View.GONE);
        if (hasAssignee) tvAssignee.setText(getString(R.string.rate_assignee, n.getAssigneeName()));

        OrgRating existing = s.existing;
        if (existing != null) {
            showExisting(existing);
        } else {
            setFormEnabled(!s.busy);
            btnSubmit.setText(R.string.rate_submit);
        }
    }

    /** Đã đánh giá: hiện lại kết quả, khóa form. */
    private void showExisting(OrgRating r) {
        tvExisting.setVisibility(View.VISIBLE);
        String when = r.getCreatedAt() != null ? DateTimeUtils.formatDate(r.getCreatedAt().getTime()) : "";
        tvExisting.setText(getString(R.string.rate_already, when, r.getByName() != null ? r.getByName() : ""));
        renderStars(r.getStarCount());
        List<String> tags = r.getTagList();
        for (int i = 0; i < chipGroupTags.getChildCount(); i++) {
            View child = chipGroupTags.getChildAt(i);
            if (child instanceof Chip) ((Chip) child).setChecked(tags.contains(String.valueOf(child.getTag())));
        }
        if (etComment.getText() == null || etComment.getText().length() == 0) {
            etComment.setText(r.getComment() != null ? r.getComment() : "");
        }
        cbElderAsked.setChecked(r.isElderAsked());
        tvStarHint.setVisibility(View.GONE);
        setFormEnabled(false);
        btnSubmit.setVisibility(View.GONE);
    }

    private void setFormEnabled(boolean enabled) {
        for (ImageButton b : stars) b.setEnabled(enabled);
        for (int i = 0; i < chipGroupTags.getChildCount(); i++) chipGroupTags.getChildAt(i).setEnabled(enabled);
        etComment.setEnabled(enabled);
        cbElderAsked.setEnabled(enabled);
        btnSubmit.setEnabled(enabled);
    }

    private void renderStars(int value) {
        for (int i = 0; i < stars.length; i++) {
            stars[i].setImageResource(i < value ? R.drawable.ic_star : R.drawable.ic_star_outline);
            stars[i].setSelected(i < value);
        }
        tvStarLevel.setText(LEVELS[Math.max(0, Math.min(5, value))]);
        tvStarLevel.setTextColor(getColor(value == 0 ? R.color.text_secondary : R.color.text_primary));
    }

    // =====================================================================
    // Gửi
    // =====================================================================

    private void submit() {
        if (viewModel.getStars() < 1) {
            tvStarLevel.setText(R.string.rate_error_stars);
            tvStarLevel.setTextColor(getColor(R.color.error));
            return;
        }
        if (!cbElderAsked.isChecked()) {
            tvElderError.setVisibility(View.VISIBLE);
            cbElderAsked.requestFocus();
            return;
        }
        String comment = etComment.getText() != null ? etComment.getText().toString().trim() : "";
        if (PrivacyGuard.containsPhoneNumber(comment)) {
            tilComment.setError(getString(R.string.feedback_phone_warning));
            return;
        }
        tilComment.setError(null);
        viewModel.submit(selectedTags(), comment);
    }
}
