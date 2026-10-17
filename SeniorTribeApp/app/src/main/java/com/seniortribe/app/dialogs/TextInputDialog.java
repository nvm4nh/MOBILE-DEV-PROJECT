package com.seniortribe.app.dialogs;

import android.app.Dialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.seniortribe.app.R;

/**
 * Hộp thoại nhập một nội dung ngắn, dùng cho các bước phối hợp (F12, F13).
 * Kết quả trả về qua Fragment Result API với requestKey riêng của từng thao tác,
 * nên vẫn nhận được sau khi xoay màn hình. Nội dung đang gõ được EditText tự giữ.
 */
public class TextInputDialog extends AppCompatDialogFragment {

    public static final String RESULT_TEXT = "text";
    public static final String RESULT_TAG = "tag";

    private static final String ARG_KEY = "key";
    private static final String ARG_TITLE = "title";
    private static final String ARG_MESSAGE = "message";
    private static final String ARG_HINT = "hint";
    private static final String ARG_POSITIVE = "positive";
    private static final String ARG_MIN_LENGTH = "min_length";
    private static final String ARG_TAG = "tag";

    private TextInputLayout tilInput;
    private TextInputEditText etInput;

    /**
     * @param minLength 0 nếu không bắt buộc nhập
     * @param tag       dữ liệu kèm theo trả về nguyên vẹn (ví dụ id đề xuất lịch)
     */
    public static TextInputDialog newInstance(String requestKey, String title, String message, String hint,
                                              String positive, int minLength, @Nullable String tag) {
        Bundle args = new Bundle();
        args.putString(ARG_KEY, requestKey);
        args.putString(ARG_TITLE, title);
        args.putString(ARG_MESSAGE, message);
        args.putString(ARG_HINT, hint);
        args.putString(ARG_POSITIVE, positive);
        args.putInt(ARG_MIN_LENGTH, minLength);
        args.putString(ARG_TAG, tag);
        TextInputDialog dialog = new TextInputDialog();
        dialog.setArguments(args);
        return dialog;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Bundle args = requireArguments();
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_text_input, null, false);
        TextView tvMessage = view.findViewById(R.id.tvInputMessage);
        tilInput = view.findViewById(R.id.tilInput);
        etInput = view.findViewById(R.id.etInput);
        tvMessage.setText(args.getString(ARG_MESSAGE));
        tvMessage.setVisibility(TextUtils.isEmpty(args.getString(ARG_MESSAGE)) ? View.GONE : View.VISIBLE);
        tilInput.setHint(args.getString(ARG_HINT));

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(args.getString(ARG_TITLE))
                .setView(view)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(args.getString(ARG_POSITIVE), null)
                .create();
        // Tự xử lý nút chính để không đóng hộp thoại khi nội dung chưa hợp lệ
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> submit()));
        return dialog;
    }

    private void submit() {
        Bundle args = requireArguments();
        String text = etInput.getText() != null ? etInput.getText().toString().trim() : "";
        int minLength = args.getInt(ARG_MIN_LENGTH);
        if (text.length() < minLength) {
            tilInput.setError(minLength <= 1
                    ? getString(R.string.coord_input_required)
                    : getString(R.string.coord_input_min_length, minLength));
            return;
        }
        Bundle result = new Bundle();
        result.putString(RESULT_TEXT, text);
        result.putString(RESULT_TAG, args.getString(ARG_TAG));
        getParentFragmentManager().setFragmentResult(args.getString(ARG_KEY), result);
        dismiss();
    }
}
