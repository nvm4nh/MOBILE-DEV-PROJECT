package com.seniortribe.app.dialogs;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.radiobutton.MaterialRadioButton;
import com.seniortribe.app.R;
import com.seniortribe.app.data.MembershipRepository;
import com.seniortribe.app.models.OrgMember;

import java.util.ArrayList;
import java.util.List;

/**
 * F10 · Hộp thoại xác nhận tiếp nhận: chọn một đại diện của tổ chức để phụ trách.
 * Kết quả trả về Activity qua Fragment Result API (giữ được khi xoay màn hình).
 */
public class AcceptNeedDialog extends AppCompatDialogFragment {

    public static final String RESULT_KEY = "accept_need_result";
    public static final String RESULT_UID = "uid";
    public static final String RESULT_NAME = "name";

    private static final String ARG_CASE_CODE = "case_code";
    private static final String ARG_ORG_ID = "org_id";
    private static final String ARG_ORG_NAME = "org_name";
    private static final String ARG_MY_UID = "my_uid";
    private static final String ARG_MY_NAME = "my_name";
    private static final String STATE_SELECTED = "selected_uid";

    private final List<OrgMember> members = new ArrayList<>();
    private RadioGroup rgAssignee;
    private View progress;
    private TextView tvNote;
    private String selectedUid;

    public static AcceptNeedDialog newInstance(String caseCode, String orgId, String orgName,
                                               String myUid, String myName) {
        Bundle args = new Bundle();
        args.putString(ARG_CASE_CODE, caseCode);
        args.putString(ARG_ORG_ID, orgId);
        args.putString(ARG_ORG_NAME, orgName);
        args.putString(ARG_MY_UID, myUid);
        args.putString(ARG_MY_NAME, myName);
        AcceptNeedDialog dialog = new AcceptNeedDialog();
        dialog.setArguments(args);
        return dialog;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Bundle args = requireArguments();
        selectedUid = savedInstanceState != null
                ? savedInstanceState.getString(STATE_SELECTED) : args.getString(ARG_MY_UID);

        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_accept_need, null, false);
        TextView tvMessage = view.findViewById(R.id.tvAcceptMessage);
        tvMessage.setText(getString(R.string.accept_message, args.getString(ARG_CASE_CODE), args.getString(ARG_ORG_NAME)));
        rgAssignee = view.findViewById(R.id.rgAssignee);
        progress = view.findViewById(R.id.progressMembers);
        tvNote = view.findViewById(R.id.tvMembersNote);

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.accept_title)
                .setView(view)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.accept_confirm, (d, which) -> sendResult())
                .create();
        dialog.setOnShowListener(d -> setConfirmEnabled(!members.isEmpty()));

        loadMembers(args);
        return dialog;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_SELECTED, selectedUid);
    }

    private void loadMembers(Bundle args) {
        progress.setVisibility(View.VISIBLE);
        MembershipRepository.get().loadRepresentatives(args.getString(ARG_ORG_ID)).addOnCompleteListener(task -> {
            if (!isAdded()) return;
            progress.setVisibility(View.GONE);
            members.clear();
            if (task.isSuccessful() && task.getResult() != null) members.addAll(task.getResult());

            // Không tải được danh sách: người đang tiếp nhận tự phụ trách
            String myUid = args.getString(ARG_MY_UID);
            boolean hasMe = false;
            for (OrgMember m : members) if (m.getUid().equals(myUid)) hasMe = true;
            if (!hasMe) {
                members.add(0, new OrgMember(myUid, getString(R.string.accept_me, args.getString(ARG_MY_NAME))));
                if (!task.isSuccessful()) {
                    tvNote.setVisibility(View.VISIBLE);
                    tvNote.setText(R.string.accept_members_fallback);
                }
            }
            showMembers();
        });
    }

    private void showMembers() {
        rgAssignee.removeAllViews();
        // Mặc định chọn người đã chọn trước đó (sau khi xoay), nếu không có thì chọn người đầu tiên
        int selectedIndex = 0;
        for (int i = 0; i < members.size(); i++) {
            if (members.get(i).getUid().equals(selectedUid)) selectedIndex = i;
        }
        int checkedId = View.NO_ID;
        for (int i = 0; i < members.size(); i++) {
            OrgMember m = members.get(i);
            RadioButton rb = new MaterialRadioButton(requireContext());
            rb.setId(View.generateViewId());
            rb.setText(m.getDisplayName());
            rb.setTextSize(16);
            rb.setMinHeight((int) (48 * getResources().getDisplayMetrics().density));
            rb.setTag(i);
            rgAssignee.addView(rb);
            if (i == selectedIndex) checkedId = rb.getId();
        }
        if (!members.isEmpty()) selectedUid = members.get(selectedIndex).getUid();
        rgAssignee.setOnCheckedChangeListener((group, id) -> {
            View rb = group.findViewById(id);
            if (rb != null) selectedUid = members.get((Integer) rb.getTag()).getUid();
        });
        if (checkedId != View.NO_ID) rgAssignee.check(checkedId);
        setConfirmEnabled(!members.isEmpty());
    }

    private void setConfirmEnabled(boolean enabled) {
        Dialog d = getDialog();
        if (d instanceof AlertDialog) {
            Button ok = ((AlertDialog) d).getButton(AlertDialog.BUTTON_POSITIVE);
            if (ok != null) ok.setEnabled(enabled);
        }
    }

    private void sendResult() {
        OrgMember chosen = null;
        for (OrgMember m : members) if (m.getUid().equals(selectedUid)) chosen = m;
        if (chosen == null && !members.isEmpty()) chosen = members.get(0);
        if (chosen == null) return;

        String name = chosen.getDisplayName();
        // Bỏ hậu tố "(bạn)" khi lưu tên người phụ trách
        String myUid = requireArguments().getString(ARG_MY_UID);
        if (chosen.getUid().equals(myUid)) name = requireArguments().getString(ARG_MY_NAME);

        Bundle result = new Bundle();
        result.putString(RESULT_UID, chosen.getUid());
        result.putString(RESULT_NAME, name);
        getParentFragmentManager().setFragmentResult(RESULT_KEY, result);
    }
}
