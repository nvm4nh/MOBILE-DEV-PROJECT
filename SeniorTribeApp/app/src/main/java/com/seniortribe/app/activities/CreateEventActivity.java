package com.seniortribe.app.activities;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.seniortribe.app.R;
import com.seniortribe.app.data.EventRepository;
import com.seniortribe.app.models.EventForm;
import com.seniortribe.app.models.Membership;
import com.seniortribe.app.utils.DateTimeUtils;
import com.seniortribe.app.utils.OrgAvatar;
import com.seniortribe.app.utils.PrivacyGuard;
import com.seniortribe.app.utils.StateView;
import com.seniortribe.app.viewmodels.CreateEventViewModel;

import java.util.Calendar;

/**
 * E07 · Tạo sự kiện. Chỉ đại diện tổ chức; sự kiện đăng dưới tên tổ chức.
 * Ngày và khung giờ chọn bằng hộp thoại (giờ bắt đầu rồi giờ kết thúc), số chỗ bằng bộ tăng giảm.
 */
public class CreateEventActivity extends AppCompatActivity {

    private CreateEventViewModel viewModel;
    private View root, scroll;
    private TextView tvAsAvatar, tvAsOrg, tvDate, tvTime, tvTimeError, tvCapacity;
    private TextInputLayout tilName, tilPlace, tilArea, tilContent;
    private TextInputEditText etName, etPlace, etArea, etContent;
    private MaterialButton btnMinus, btnPlus, btnSubmit;
    private MaterialSwitch swAllowCancel, swOpenOthers;
    private StateView stateView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_event);
        bindViews();

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        tvDate.setOnClickListener(v -> pickDate());
        tvTime.setOnClickListener(v -> pickStartTime());
        btnMinus.setOnClickListener(v -> changeCapacity(-1));
        btnPlus.setOnClickListener(v -> changeCapacity(1));
        btnMinus.setOnLongClickListener(v -> { changeCapacity(-10); return true; });
        btnPlus.setOnLongClickListener(v -> { changeCapacity(10); return true; });
        swAllowCancel.setOnCheckedChangeListener((b, c) -> viewModel.getDraft().allowCancel = c);
        swOpenOthers.setOnCheckedChangeListener((b, c) -> viewModel.getDraft().openToOtherOrgs = c);
        btnSubmit.setOnClickListener(v -> submit());

        viewModel = new ViewModelProvider(this).get(CreateEventViewModel.class);
        CreateEventViewModel.Draft d = viewModel.getDraft();
        swAllowCancel.setChecked(d.allowCancel);
        swOpenOthers.setChecked(d.openToOtherOrgs);
        renderDraft();
        viewModel.getUi().observe(this, this::render);
        viewModel.getMessages().observe(this, e -> {
            String msg = e.getIfNotHandled();
            if (msg != null) Snackbar.make(root, msg, Snackbar.LENGTH_LONG).show();
        });
        viewModel.getCreated().observe(this, e -> {
            String id = e.getIfNotHandled();
            if (id == null) return;
            startActivity(new Intent(this, EventDetailActivity.class).putExtra(EventDetailActivity.EXTRA_EVENT_ID, id));
            finish();
        });
        viewModel.start();
    }

    private void bindViews() {
        root = findViewById(R.id.rootCreateEvent);
        scroll = findViewById(R.id.scrollCreateEvent);
        tvAsAvatar = findViewById(R.id.tvAsAvatar);
        tvAsOrg = findViewById(R.id.tvAsOrg);
        tilName = findViewById(R.id.tilEventName);
        etName = findViewById(R.id.etEventName);
        tvDate = findViewById(R.id.tvEventDate);
        tvTime = findViewById(R.id.tvEventTime);
        tvTimeError = findViewById(R.id.tvTimeError);
        tilPlace = findViewById(R.id.tilEventPlace);
        etPlace = findViewById(R.id.etEventPlace);
        tilArea = findViewById(R.id.tilEventArea);
        etArea = findViewById(R.id.etEventArea);
        btnMinus = findViewById(R.id.btnCapacityMinus);
        tvCapacity = findViewById(R.id.tvCapacity);
        btnPlus = findViewById(R.id.btnCapacityPlus);
        swAllowCancel = findViewById(R.id.swAllowCancel);
        swOpenOthers = findViewById(R.id.swOpenOthers);
        tilContent = findViewById(R.id.tilEventContent);
        etContent = findViewById(R.id.etEventContent);
        btnSubmit = findViewById(R.id.btnSubmitEvent);
        stateView = new StateView(findViewById(R.id.frameCreateEvent));
    }

    private void render(CreateEventViewModel.Ui s) {
        if (s.loading) {
            scroll.setVisibility(View.INVISIBLE);
            stateView.showLoading();
            return;
        }
        Membership me = s.membership;
        if (me == null || !me.isRepresentative()) {
            scroll.setVisibility(View.INVISIBLE);
            if (s.error != null) {
                stateView.showError(getString(R.string.state_error_title), s.error, null, null);
            } else {
                stateView.showMessage(R.drawable.ic_lock, getString(R.string.create_event_title),
                        getString(R.string.create_event_not_rep), null, null);
            }
            return;
        }
        stateView.hide();
        scroll.setVisibility(View.VISIBLE);
        tvAsAvatar.setText(OrgAvatar.initials(me.getOrgName()));
        tvAsOrg.setText(me.getOrgName());
        btnSubmit.setEnabled(!s.busy);
    }

    private void renderDraft() {
        CreateEventViewModel.Draft d = viewModel.getDraft();
        tvDate.setText(d.dateSet ? DateTimeUtils.formatWeekdayFullDate(d.startAt) : "");
        tvTime.setText(d.endAt > 0 ? DateTimeUtils.formatTimeRange(d.startAt, d.endAt) : "");
        tvCapacity.setText(String.valueOf(d.capacity));
        btnMinus.setEnabled(d.capacity > 1);
        btnPlus.setEnabled(d.capacity < EventRepository.MAX_CAPACITY);
    }

    private void changeCapacity(int delta) {
        viewModel.changeCapacity(delta);
        renderDraft();
    }

    // =====================================================================
    // Chọn ngày giờ
    // =====================================================================

    private void pickDate() {
        CreateEventViewModel.Draft d = viewModel.getDraft();
        Calendar c = Calendar.getInstance();
        if (d.dateSet) c.setTimeInMillis(d.startAt);
        else c.add(Calendar.DAY_OF_MONTH, 7);
        DatePickerDialog dialog = new DatePickerDialog(this, (dp, y, m, day) -> {
            Calendar start = Calendar.getInstance();
            if (d.endAt > 0) start.setTimeInMillis(d.startAt);
            else {
                start.set(Calendar.HOUR_OF_DAY, 0);
                start.set(Calendar.MINUTE, 0);
            }
            long length = d.endAt > 0 ? d.endAt - d.startAt : 0;
            start.set(y, m, day);
            start.set(Calendar.SECOND, 0);
            start.set(Calendar.MILLISECOND, 0);
            d.startAt = start.getTimeInMillis();
            d.endAt = length > 0 ? d.startAt + length : 0;
            d.dateSet = true;
            tvTimeError.setVisibility(View.GONE);
            renderDraft();
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
        dialog.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
        dialog.show();
    }

    private void pickStartTime() {
        CreateEventViewModel.Draft d = viewModel.getDraft();
        if (!d.dateSet) {
            showTimeError(getString(R.string.create_event_error_date));
            return;
        }
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(d.startAt);
        int hour = d.endAt > 0 ? c.get(Calendar.HOUR_OF_DAY) : 8;
        int minute = d.endAt > 0 ? c.get(Calendar.MINUTE) : 0;
        TimePickerDialog dialog = new TimePickerDialog(this, (tp, h, min) -> {
            Calendar start = Calendar.getInstance();
            start.setTimeInMillis(d.startAt);
            start.set(Calendar.HOUR_OF_DAY, h);
            start.set(Calendar.MINUTE, min);
            pickEndTime(start.getTimeInMillis());
        }, hour, minute, true);
        dialog.setTitle(R.string.create_event_pick_start);
        dialog.show();
    }

    private void pickEndTime(long startAt) {
        CreateEventViewModel.Draft d = viewModel.getDraft();
        Calendar end = Calendar.getInstance();
        end.setTimeInMillis(d.endAt > startAt ? d.endAt : startAt + 3L * 60 * 60 * 1000);
        TimePickerDialog dialog = new TimePickerDialog(this, (tp, h, min) -> {
            Calendar e = Calendar.getInstance();
            e.setTimeInMillis(startAt);
            e.set(Calendar.HOUR_OF_DAY, h);
            e.set(Calendar.MINUTE, min);
            if (e.getTimeInMillis() <= startAt) {
                showTimeError(getString(R.string.create_event_error_end));
                return;
            }
            d.startAt = startAt;
            d.endAt = e.getTimeInMillis();
            tvTimeError.setVisibility(View.GONE);
            renderDraft();
        }, end.get(Calendar.HOUR_OF_DAY), end.get(Calendar.MINUTE), true);
        dialog.setTitle(R.string.create_event_pick_end);
        dialog.show();
    }

    private void showTimeError(String message) {
        tvTimeError.setText(message);
        tvTimeError.setVisibility(View.VISIBLE);
    }

    // =====================================================================
    // Đăng
    // =====================================================================

    private void submit() {
        CreateEventViewModel.Draft d = viewModel.getDraft();
        EventForm form = new EventForm();
        form.title = text(etName);
        form.place = text(etPlace);
        form.area = text(etArea);
        form.content = text(etContent);
        form.startAt = d.startAt;
        form.endAt = d.endAt;
        form.capacity = d.capacity;
        form.allowCancel = d.allowCancel;
        form.openToOtherOrgs = d.openToOtherOrgs;

        boolean ok = true;
        tilName.setError(form.title.length() < 5 ? getString(R.string.create_event_error_name) : null);
        if (form.title.length() < 5) ok = false;
        if (!d.dateSet || d.endAt <= 0) {
            showTimeError(getString(R.string.create_event_error_date));
            ok = false;
        }
        tilPlace.setError(form.place.length() < 2 ? getString(R.string.create_event_error_place) : null);
        if (form.place.length() < 2) ok = false;
        if (form.content.length() < 10) {
            tilContent.setError(getString(R.string.create_event_error_content));
            ok = false;
        } else if (PrivacyGuard.containsPhoneNumber(form.content)) {
            tilContent.setError(getString(R.string.create_event_phone_warning));
            ok = false;
        } else {
            tilContent.setError(null);
        }
        if (!ok) return;

        String error = EventRepository.validate(form, System.currentTimeMillis());
        if (error != null) {
            Snackbar.make(root, error, Snackbar.LENGTH_LONG).show();
            return;
        }
        viewModel.submit(form);
    }

    private static String text(TextInputEditText et) {
        return et.getText() != null ? et.getText().toString().trim() : "";
    }
}
