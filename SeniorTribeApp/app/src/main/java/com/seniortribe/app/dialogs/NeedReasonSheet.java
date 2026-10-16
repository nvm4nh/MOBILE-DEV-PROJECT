package com.seniortribe.app.dialogs;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputLayout;
import com.seniortribe.app.R;

/**
 * F14 · Bảng trả lại (tổ chức phụ trách) hoặc hủy (đầu mối) nhu cầu.
 * Bắt buộc chọn lý do; chọn "Lý do khác" thì phải ghi chú.
 */
public class NeedReasonSheet extends BottomSheetDialogFragment {

    public static final String MODE_RETURN = "RETURN";
    public static final String MODE_CANCEL = "CANCEL";

    public static final String RESULT_KEY = "need_reason_result";
    public static final String RESULT_MODE = "mode";
    public static final String RESULT_REASON = "reason";
    public static final String RESULT_NOTE = "note";

    private static final String ARG_MODE = "mode";
    private static final String ARG_CASE_CODE = "case_code";

    private RadioGroup activeGroup;
    private TextView tvReasonError;
    private TextInputLayout tilNote;
    private EditText etNote;

    public static NeedReasonSheet newInstance(String mode, String caseCode) {
        Bundle args = new Bundle();
        args.putString(ARG_MODE, mode);
        args.putString(ARG_CASE_CODE, caseCode);
        NeedReasonSheet sheet = new NeedReasonSheet();
        sheet.setArguments(args);
        return sheet;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_need_reason, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        boolean isReturn = MODE_RETURN.equals(requireArguments().getString(ARG_MODE));
        String caseCode = requireArguments().getString(ARG_CASE_CODE);

        TextView tvTitle = view.findViewById(R.id.tvSheetTitle);
        TextView tvMessage = view.findViewById(R.id.tvSheetMessage);
        RadioGroup rgReturn = view.findViewById(R.id.rgReturnReasons);
        RadioGroup rgCancel = view.findViewById(R.id.rgCancelReasons);
        MaterialButton btnConfirm = view.findViewById(R.id.btnConfirmReason);
        tvReasonError = view.findViewById(R.id.tvReasonError);
        tilNote = view.findViewById(R.id.tilReasonNote);
        etNote = view.findViewById(R.id.etReasonNote);

        tvTitle.setText(getString(isReturn ? R.string.reason_return_title : R.string.reason_cancel_title, caseCode));
        tvMessage.setText(isReturn ? R.string.reason_return_message : R.string.reason_cancel_message);
        btnConfirm.setText(isReturn ? R.string.action_return_need : R.string.action_cancel_need);
        rgReturn.setVisibility(isReturn ? View.VISIBLE : View.GONE);
        rgCancel.setVisibility(isReturn ? View.GONE : View.VISIBLE);
        activeGroup = isReturn ? rgReturn : rgCancel;

        activeGroup.setOnCheckedChangeListener((g, id) -> {
            tvReasonError.setVisibility(View.GONE);
            tilNote.setError(null);
        });
        btnConfirm.setOnClickListener(v -> confirm(isReturn));
        view.findViewById(R.id.btnDismissReason).setOnClickListener(v -> dismiss());
    }

    private void confirm(boolean isReturn) {
        int checkedId = activeGroup.getCheckedRadioButtonId();
        if (checkedId == View.NO_ID) {
            tvReasonError.setVisibility(View.VISIBLE);
            return;
        }
        RadioButton checked = activeGroup.findViewById(checkedId);
        String reason = checked.getText().toString();
        String note = etNote.getText() != null ? etNote.getText().toString().trim() : "";

        boolean isOther = checkedId == R.id.rbReturnOther || checkedId == R.id.rbCancelOther;
        if (isOther && note.isEmpty()) {
            tilNote.setError(getString(R.string.reason_error_note_required));
            etNote.requestFocus();
            return;
        }

        Bundle result = new Bundle();
        result.putString(RESULT_MODE, isReturn ? MODE_RETURN : MODE_CANCEL);
        result.putString(RESULT_REASON, reason);
        result.putString(RESULT_NOTE, note.isEmpty() ? null : note);
        getParentFragmentManager().setFragmentResult(RESULT_KEY, result);
        dismiss();
    }
}
