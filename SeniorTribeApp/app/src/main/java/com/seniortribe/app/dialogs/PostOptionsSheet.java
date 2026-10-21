package com.seniortribe.app.dialogs;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.seniortribe.app.R;

/**
 * F05 · Bảng tùy chọn của một bài: Sửa bài, Ẩn bài khỏi bảng tin, Đóng.
 * Chỉ mở cho đại diện của tổ chức đã đăng bài. Kết quả trả về qua Fragment Result API
 * nên vẫn nhận được sau khi xoay màn hình.
 */
public class PostOptionsSheet extends BottomSheetDialogFragment {

    public static final String RESULT_KEY = "post_options_result";
    public static final String RESULT_ACTION = "action";
    public static final String RESULT_POST_ID = "post_id";
    public static final String ACTION_EDIT = "EDIT";
    public static final String ACTION_HIDE = "HIDE";

    private static final String ARG_POST_ID = "post_id";
    private static final String ARG_TITLE = "title";
    private static final String ARG_ORG = "org";

    public static PostOptionsSheet newInstance(String postId, String title, String orgName) {
        Bundle args = new Bundle();
        args.putString(ARG_POST_ID, postId);
        args.putString(ARG_TITLE, title);
        args.putString(ARG_ORG, orgName);
        PostOptionsSheet sheet = new PostOptionsSheet();
        sheet.setArguments(args);
        return sheet;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_post_options, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Bundle args = requireArguments();
        TextView tvTitle = view.findViewById(R.id.tvOptionTitle);
        TextView tvNote = view.findViewById(R.id.tvOptionNote);
        tvTitle.setText(args.getString(ARG_TITLE));
        tvNote.setText(getString(R.string.post_options_note, args.getString(ARG_ORG)));

        view.findViewById(R.id.optionEdit).setOnClickListener(v -> send(ACTION_EDIT));
        view.findViewById(R.id.optionHide).setOnClickListener(v -> send(ACTION_HIDE));
        view.findViewById(R.id.optionClose).setOnClickListener(v -> dismiss());
    }

    private void send(String action) {
        Bundle result = new Bundle();
        result.putString(RESULT_ACTION, action);
        result.putString(RESULT_POST_ID, requireArguments().getString(ARG_POST_ID));
        getParentFragmentManager().setFragmentResult(RESULT_KEY, result);
        dismiss();
    }
}
