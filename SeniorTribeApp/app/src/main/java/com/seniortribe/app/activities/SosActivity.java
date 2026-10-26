package com.seniortribe.app.activities;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.seniortribe.app.R;
import com.seniortribe.app.models.Membership;
import com.seniortribe.app.models.NeedSummary;
import com.seniortribe.app.models.SosAlert;
import com.seniortribe.app.sos.LocationHelper;
import com.seniortribe.app.utils.Constants;
import com.seniortribe.app.utils.DateTimeUtils;
import com.seniortribe.app.utils.NetworkUtils;
import com.seniortribe.app.viewmodels.SosViewModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * E09 · Khẩn cấp (SOS).
 *
 * Theo phạm vi chốt của nhóm: ứng dụng KHÔNG cam kết cứu hộ và chưa có đơn vị phản ứng riêng, nên
 * màn hình này chỉ (1) đưa người dùng tới đúng số khẩn cấp quốc gia 115 / 113 / 114 bằng trình
 * quay số của máy, và (2) báo ngay cho người còn lại trong lần phối hợp (đầu mối ghi nhận hoặc
 * người được giao) kèm loại sự việc, ghi chú, vị trí nếu muốn, để họ liên hệ người nhà của cụ.
 * Báo khẩn chỉ hai người đó thấy (R08) và không xóa được, phục vụ đối soát sau này.
 */
public class SosActivity extends AppCompatActivity {

    private SosViewModel viewModel;
    private SosViewModel.Ui current;

    private View progress, layoutReport, tvNoNeeds, tvNoAlerts;
    private AutoCompleteTextView actNeed;
    private ChipGroup chipsKind;
    private TextInputEditText etNote;
    private MaterialCheckBox cbLocation;
    private TextView tvCalled;
    private View btnSend;
    private ViewGroup layoutAlerts;
    private List<NeedSummary> shownNeeds = new ArrayList<>();

    private final ActivityResultLauncher<String[]> locationPermission = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), r -> {
                if (!LocationHelper.hasPermission(this)) {
                    cbLocation.setChecked(false);
                    Snackbar.make(layoutReport, R.string.sos_location_denied, Snackbar.LENGTH_LONG).show();
                }
            });

    private final ActivityResultLauncher<String> notificationPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> { });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sos);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        progress = findViewById(R.id.progressSos);
        layoutReport = findViewById(R.id.layoutReport);
        tvNoNeeds = findViewById(R.id.tvNoNeeds);
        tvNoAlerts = findViewById(R.id.tvNoAlerts);
        actNeed = findViewById(R.id.actNeed);
        chipsKind = findViewById(R.id.chipsKind);
        etNote = findViewById(R.id.etSosNote);
        cbLocation = findViewById(R.id.cbLocation);
        tvCalled = findViewById(R.id.tvCalled);
        btnSend = findViewById(R.id.btnSendSos);
        layoutAlerts = findViewById(R.id.layoutAlerts);

        findViewById(R.id.btnCall115).setOnClickListener(v -> dial("115"));
        findViewById(R.id.btnCall113).setOnClickListener(v -> dial("113"));
        findViewById(R.id.btnCall114).setOnClickListener(v -> dial("114"));
        cbLocation.setOnCheckedChangeListener((b, on) -> {
            if (on && !LocationHelper.hasPermission(this)) {
                locationPermission.launch(new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION});
            }
        });
        actNeed.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < shownNeeds.size()) viewModel.select(shownNeeds.get(position));
        });
        btnSend.setOnClickListener(v -> confirmSend());

        viewModel = new ViewModelProvider(this).get(SosViewModel.class);
        viewModel.getUi().observe(this, this::render);
        viewModel.getMessages().observe(this, e -> {
            String m = e.getIfNotHandled();
            if (m != null) Snackbar.make(layoutReport, m, Snackbar.LENGTH_LONG).show();
        });
        viewModel.getSent().observe(this, e -> {
            if (e.getIfNotHandled() != null) etNote.setText("");
        });
        viewModel.start(getIntent().getStringExtra(Constants.EXTRA_NEED_ID));
        askNotificationPermission();
    }

    /** Để nhận báo khẩn của bên kia khi đang ở màn hình khác (Android 13+ cần cho phép). */
    private void askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this,
                Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    private void render(SosViewModel.Ui s) {
        current = s;
        progress.setVisibility(s.loading || s.sending || s.locating ? View.VISIBLE : View.GONE);
        boolean hasNeeds = !s.needs.isEmpty();
        tvNoNeeds.setVisibility(!s.loading && !hasNeeds ? View.VISIBLE : View.GONE);
        layoutReport.setVisibility(hasNeeds ? View.VISIBLE : View.GONE);
        if (!hasNeeds) return;

        // Danh sách nhu cầu để chọn
        shownNeeds = s.needs;
        List<String> labels = new ArrayList<>();
        for (NeedSummary n : s.needs) labels.add(label(n));
        actNeed.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, labels));
        if (s.selected != null) actNeed.setText(label(s.selected), false);

        String called = viewModel.getLastCalled();
        tvCalled.setVisibility(called != null ? View.VISIBLE : View.GONE);
        if (called != null) tvCalled.setText(getString(R.string.sos_called_note, called));
        btnSend.setEnabled(s.selected != null && !s.sending && !s.locating);

        renderAlerts(s);
    }

    private void renderAlerts(SosViewModel.Ui s) {
        layoutAlerts.removeAllViews();
        tvNoAlerts.setVisibility(s.alerts.isEmpty() ? View.VISIBLE : View.GONE);
        Membership me = s.membership;
        LayoutInflater inf = LayoutInflater.from(this);
        for (SosAlert a : s.alerts) {
            View row = inf.inflate(R.layout.item_sos_alert, layoutAlerts, false);
            TextView status = row.findViewById(R.id.tvAlertStatus);
            TextView title = row.findViewById(R.id.tvAlertTitle);
            TextView note = row.findViewById(R.id.tvAlertNote);
            TextView meta = row.findViewById(R.id.tvAlertMeta);
            View map = row.findViewById(R.id.btnAlertMap);
            View ack = row.findViewById(R.id.btnAlertAck);
            View resolve = row.findViewById(R.id.btnAlertResolve);

            int fg, bg, label;
            if (a.isResolved()) {
                label = R.string.sos_status_resolved; fg = R.color.status_completed; bg = R.color.status_completed_bg;
            } else if (SosAlert.ACK.equals(a.getStatus())) {
                label = R.string.sos_status_ack; fg = R.color.status_accepted; bg = R.color.status_accepted_bg;
            } else {
                label = R.string.sos_status_open; fg = R.color.sos_red; bg = R.color.sos_red_bg;
            }
            status.setText(label);
            status.setTextColor(ContextCompat.getColor(this, fg));
            status.setBackgroundTintList(android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this, bg)));
            androidx.core.widget.TextViewCompat.setCompoundDrawableTintList(status,
                    android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this, fg)));

            title.setText(getString(R.string.sos_alert_title, getString(kindLabel(a.getKind())),
                    nz(a.getByName()), DateTimeUtils.formatDateTime(a.getCreatedAtMillis())));
            note.setText(a.getNote());
            note.setVisibility(TextUtils.isEmpty(a.getNote()) ? View.GONE : View.VISIBLE);

            StringBuilder m = new StringBuilder();
            if (a.getCalledNumber() != null) m.append(getString(R.string.sos_meta_called, a.getCalledNumber()));
            if (a.getAckByName() != null) {
                if (m.length() > 0) m.append(" · ");
                m.append(getString(R.string.sos_meta_ack, a.getAckByName()));
            }
            if (a.isResolved()) {
                if (m.length() > 0) m.append('\n');
                m.append(getString(R.string.sos_meta_resolved, nz(a.getResolvedByName()), nz(a.getResolveNote())));
            }
            meta.setText(m);
            meta.setVisibility(m.length() > 0 ? View.VISIBLE : View.GONE);

            boolean mine = me != null && me.getUid().equals(a.getByUid());
            map.setVisibility(a.getLocation() != null ? View.VISIBLE : View.GONE);
            map.setOnClickListener(v -> openMap(a));
            ack.setVisibility(!mine && a.isOpen() ? View.VISIBLE : View.GONE);
            ack.setOnClickListener(v -> {
                if (requireOnline()) viewModel.acknowledge(a);
            });
            resolve.setVisibility(!a.isResolved() ? View.VISIBLE : View.GONE);
            resolve.setOnClickListener(v -> askResolve(a));
            row.setContentDescription(getString(label) + ". " + title.getText());
            layoutAlerts.addView(row);
        }
    }

    // =====================================================================
    // Thao tác
    // =====================================================================

    /** Mở trình quay số với số điền sẵn; người dùng tự bấm gọi (không cần quyền CALL_PHONE). */
    private void dial(String number) {
        viewModel.setLastCalled(number);
        if (current != null) render(current);
        try {
            startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + number)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, getString(R.string.sos_no_dialer, number), Toast.LENGTH_LONG).show();
        }
    }

    private void confirmSend() {
        if (current == null || current.selected == null || !requireOnline()) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.sos_confirm_title)
                .setMessage(getString(R.string.sos_confirm_message, label(current.selected)))
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.sos_send, (d, w) -> send())
                .show();
    }

    private void send() {
        String kind = kindFromChip(chipsKind.getCheckedChipId());
        Editable e = etNote.getText();
        String note = e != null ? e.toString() : "";
        if (cbLocation.isChecked() && LocationHelper.hasPermission(this)) {
            viewModel.setLocating(true);
            LocationHelper.getOnce(this, location -> {
                if (isFinishing()) return;
                if (location == null) {
                    Snackbar.make(layoutReport, R.string.sos_location_unavailable, Snackbar.LENGTH_LONG).show();
                }
                viewModel.send(kind, note, location);
            });
        } else {
            viewModel.send(kind, note, null);
        }
    }

    private void askResolve(SosAlert a) {
        if (!requireOnline()) return;
        View content = getLayoutInflater().inflate(R.layout.dialog_text_input, null);
        EditText input = findEditText(content);
        if (input != null) input.setHint(R.string.sos_resolve_hint);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.sos_resolve)
                .setMessage(R.string.sos_resolve_message)
                .setView(content)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.sos_resolve, (d, w) ->
                        viewModel.resolve(a, input != null ? input.getText().toString() : ""))
                .show();
    }

    @Nullable
    private static EditText findEditText(View v) {
        if (v instanceof EditText) return (EditText) v;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                EditText e = findEditText(g.getChildAt(i));
                if (e != null) return e;
            }
        }
        return null;
    }

    private void openMap(SosAlert a) {
        if (a.getLocation() == null) return;
        double lat = a.getLocation().getLatitude(), lng = a.getLocation().getLongitude();
        Uri uri = Uri.parse(String.format(Locale.US, "geo:%f,%f?q=%f,%f(SOS)", lat, lng, lat, lng));
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException e) {
            Snackbar.make(layoutReport, getString(R.string.sos_coordinates, lat, lng), Snackbar.LENGTH_LONG).show();
        }
    }

    private boolean requireOnline() {
        if (NetworkUtils.isNetworkAvailable(this)) return true;
        Snackbar.make(layoutReport, R.string.sos_offline, Snackbar.LENGTH_LONG).show();
        return false;
    }

    private String label(NeedSummary n) {
        String code = n.getCaseCode() != null ? n.getCaseCode() : "";
        return TextUtils.isEmpty(n.getTitle()) ? code : code + " · " + n.getTitle();
    }

    private static String kindFromChip(int id) {
        if (id == R.id.chipSafety) return SosAlert.KIND_SAFETY;
        if (id == R.id.chipFire) return SosAlert.KIND_FIRE;
        if (id == R.id.chipOther) return SosAlert.KIND_OTHER;
        return SosAlert.KIND_MEDICAL;
    }

    public static int kindLabel(@Nullable String kind) {
        if (SosAlert.KIND_SAFETY.equals(kind)) return R.string.sos_kind_safety;
        if (SosAlert.KIND_FIRE.equals(kind)) return R.string.sos_kind_fire;
        if (SosAlert.KIND_OTHER.equals(kind)) return R.string.sos_kind_other;
        return R.string.sos_kind_medical;
    }

    private static String nz(@Nullable String s) {
        return s != null ? s : "";
    }
}
