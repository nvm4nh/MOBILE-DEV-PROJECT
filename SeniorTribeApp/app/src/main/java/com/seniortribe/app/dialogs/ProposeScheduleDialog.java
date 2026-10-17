package com.seniortribe.app.dialogs;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDialogFragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.seniortribe.app.R;
import com.seniortribe.app.utils.DateTimeUtils;

import java.util.Calendar;

/**
 * F12 · Tổ chức phụ trách đề xuất lịch hẹn. Ngày giờ đã chọn được lưu khi xoay màn hình;
 * kết quả trả về Activity qua Fragment Result API.
 */
public class ProposeScheduleDialog extends AppCompatDialogFragment {

    public static final String RESULT_KEY = "propose_schedule_result";
    public static final String RESULT_START_AT = "start_at";
    public static final String RESULT_PLACE = "place";
    public static final String RESULT_NOTE = "note";

    private static final String ARG_DEFAULT_PLACE = "default_place";
    private static final String STATE_DATE_SET = "date_set";
    private static final String STATE_TIME_SET = "time_set";
    private static final String STATE_MILLIS = "millis";

    private final Calendar picked = Calendar.getInstance();
    private boolean dateSet, timeSet;

    private MaterialButton btnDate, btnTime;
    private TextView tvTimeError;
    private TextInputLayout tilPlace;
    private TextInputEditText etPlace, etNote;

    /** @param defaultPlace khu vực của nhu cầu, gợi ý sẵn cho ô địa điểm */
    public static ProposeScheduleDialog newInstance(@Nullable String defaultPlace) {
        Bundle args = new Bundle();
        args.putString(ARG_DEFAULT_PLACE, defaultPlace);
        ProposeScheduleDialog dialog = new ProposeScheduleDialog();
        dialog.setArguments(args);
        return dialog;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            dateSet = savedInstanceState.getBoolean(STATE_DATE_SET);
            timeSet = savedInstanceState.getBoolean(STATE_TIME_SET);
            picked.setTimeInMillis(savedInstanceState.getLong(STATE_MILLIS));
        } else {
            // Mặc định 9 giờ sáng ngày mai, người dùng chỉnh lại
            picked.add(Calendar.DAY_OF_MONTH, 1);
            picked.set(Calendar.HOUR_OF_DAY, 9);
            picked.set(Calendar.MINUTE, 0);
        }
        picked.set(Calendar.SECOND, 0);
        picked.set(Calendar.MILLISECOND, 0);

        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_propose_schedule, null, false);
        btnDate = view.findViewById(R.id.btnPickDate);
        btnTime = view.findViewById(R.id.btnPickTime);
        tvTimeError = view.findViewById(R.id.tvTimeError);
        tilPlace = view.findViewById(R.id.tilPlace);
        etPlace = view.findViewById(R.id.etPlace);
        etNote = view.findViewById(R.id.etNote);
        if (savedInstanceState == null) etPlace.setText(requireArguments().getString(ARG_DEFAULT_PLACE));

        btnDate.setOnClickListener(v -> new DatePickerDialog(requireContext(), (dp, y, m, d) -> {
            picked.set(y, m, d);
            dateSet = true;
            renderPicked();
        }, picked.get(Calendar.YEAR), picked.get(Calendar.MONTH), picked.get(Calendar.DAY_OF_MONTH)).show());
        btnTime.setOnClickListener(v -> new TimePickerDialog(requireContext(), (tp, h, min) -> {
            picked.set(Calendar.HOUR_OF_DAY, h);
            picked.set(Calendar.MINUTE, min);
            timeSet = true;
            renderPicked();
        }, picked.get(Calendar.HOUR_OF_DAY), picked.get(Calendar.MINUTE), true).show());
        renderPicked();

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.propose_title)
                .setView(view)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.propose_confirm, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> submit()));
        return dialog;
    }

    private void renderPicked() {
        if (dateSet) btnDate.setText(DateTimeUtils.formatDate(picked.getTimeInMillis()));
        if (timeSet) btnTime.setText(DateTimeUtils.formatTime(picked.getTimeInMillis()));
        tvTimeError.setVisibility(View.GONE);
    }

    private void submit() {
        boolean ok = true;
        if (!dateSet || !timeSet || picked.getTimeInMillis() <= System.currentTimeMillis()) {
            tvTimeError.setVisibility(View.VISIBLE);
            ok = false;
        }
        String place = etPlace.getText() != null ? etPlace.getText().toString().trim() : "";
        if (place.length() < 2) {
            tilPlace.setError(getString(R.string.propose_error_place));
            ok = false;
        } else {
            tilPlace.setError(null);
        }
        if (!ok) return;

        Bundle result = new Bundle();
        result.putLong(RESULT_START_AT, picked.getTimeInMillis());
        result.putString(RESULT_PLACE, place);
        result.putString(RESULT_NOTE, etNote.getText() != null ? etNote.getText().toString().trim() : "");
        getParentFragmentManager().setFragmentResult(RESULT_KEY, result);
        dismiss();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_DATE_SET, dateSet);
        outState.putBoolean(STATE_TIME_SET, timeSet);
        outState.putLong(STATE_MILLIS, picked.getTimeInMillis());
    }
}
