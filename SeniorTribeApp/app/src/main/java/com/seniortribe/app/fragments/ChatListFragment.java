package com.seniortribe.app.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.seniortribe.app.R;
import com.seniortribe.app.activities.ChatActivity;
import com.seniortribe.app.adapters.NeedAdapter;
import com.seniortribe.app.models.Membership;
import com.seniortribe.app.models.NeedSummary;
import com.seniortribe.app.utils.Constants;
import com.seniortribe.app.utils.StateView;
import com.seniortribe.app.viewmodels.MyCoordinationViewModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * F11 · Các cuộc trò chuyện của tài khoản: nhu cầu mà mình là đầu mối đã ghi nhận hoặc
 * là người được giao phụ trách. Đang phối hợp hiện trước, đã kết thúc (chỉ đọc) hiện sau.
 */
public class ChatListFragment extends Fragment implements NeedAdapter.OnNeedClickListener {

    private NeedAdapter adapter;
    private StateView stateView;
    private MyCoordinationViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_chat_list, container, false);
        RecyclerView rv = view.findViewById(R.id.rvChatConversations);
        adapter = new NeedAdapter(requireContext(), this);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setAdapter(adapter);
        stateView = new StateView(view.findViewById(R.id.frameChatList));
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(this).get(MyCoordinationViewModel.class);
        viewModel.getUi().observe(getViewLifecycleOwner(), this::render);
        viewModel.start();
    }

    private void render(MyCoordinationViewModel.Ui s) {
        if (s.unauthorized) {
            adapter.setNeeds(new ArrayList<>());
            stateView.showMessage(R.drawable.ic_lock, getString(R.string.needs_state_no_membership_title),
                    getString(R.string.needs_state_no_membership_message), null, null);
            return;
        }
        if (s.error != null) {
            stateView.showError(getString(R.string.state_error_title), s.error,
                    getString(R.string.action_retry), v -> viewModel.retry());
            return;
        }
        if (s.loading) {
            stateView.showLoading();
            return;
        }
        List<NeedSummary> list = conversations(s.membership, s.needs);
        adapter.setNeeds(list);
        if (list.isEmpty()) {
            stateView.showMessage(R.drawable.ic_chat, getString(R.string.chat_list_empty_title),
                    getString(R.string.chat_list_empty_message), null, null);
        } else {
            stateView.hide();
        }
    }

    /** Chỉ hai người trong luồng chat: đầu mối đã ghi nhận và người được giao phụ trách (R08). */
    static List<NeedSummary> conversations(Membership me, List<NeedSummary> needs) {
        List<NeedSummary> out = new ArrayList<>();
        if (me == null) return out;
        for (NeedSummary n : needs) {
            if (n.getCurrentAssignmentId() == null) continue;
            if (!me.getUid().equals(n.getRecorderUid()) && !me.getUid().equals(n.getAssigneeUid())) continue;
            out.add(n);
        }
        Collections.sort(out, (a, b) -> {
            if (a.isCoordinating() != b.isCoordinating()) return a.isCoordinating() ? -1 : 1;
            long ta = a.getUpdatedAt() != null ? a.getUpdatedAt().getTime() : 0;
            long tb = b.getUpdatedAt() != null ? b.getUpdatedAt().getTime() : 0;
            return Long.compare(tb, ta);
        });
        return out;
    }

    @Override
    public void onNeedClick(NeedSummary need) {
        Intent intent = new Intent(requireContext(), ChatActivity.class);
        intent.putExtra(Constants.EXTRA_NEED_ID, need.getNeedId());
        intent.putExtra(Constants.EXTRA_ASSIGNMENT_ID, need.getCurrentAssignmentId());
        intent.putExtra(Constants.EXTRA_NEED, need);
        startActivity(intent);
    }
}
