package com.seniortribe.app.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.seniortribe.app.R;
import com.seniortribe.app.activities.ChatActivity;
import com.seniortribe.app.adapters.NeedAdapter;
import com.seniortribe.app.models.NeedSummary;
import com.seniortribe.app.utils.Constants;

import java.util.ArrayList;
import java.util.List;

public class ChatListFragment extends Fragment implements NeedAdapter.OnNeedClickListener {

    private RecyclerView rvChatConversations;
    private NeedAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_chat_list, container, false);
        rvChatConversations = view.findViewById(R.id.rvChatConversations);

        adapter = new NeedAdapter(requireContext(), this);
        rvChatConversations.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvChatConversations.setAdapter(adapter);

        loadConversations();
        return view;
    }

    private void loadConversations() {
        List<NeedSummary> list = new ArrayList<>();
        NeedSummary n = new NeedSummary();
        n.setNeedId("need_02");
        n.setCaseCode("#ST-CHAT-002");
        n.setTitle("Trao đổi ca hỗ trợ #ST-2026-002 cùng Điều phối viên");
        n.setCategory("Đại diện Vầng Trăng Khuyết");
        n.setDistrict("Quận 8");
        n.setStatus(Constants.STATUS_ACCEPTED);
        n.setCreatedAt(System.currentTimeMillis() - 3600000);
        list.add(n);

        adapter.setNeeds(list);
    }

    @Override
    public void onNeedClick(NeedSummary need) {
        Intent intent = new Intent(requireContext(), ChatActivity.class);
        intent.putExtra(Constants.EXTRA_NEED_ID, need.getNeedId());
        intent.putExtra(Constants.EXTRA_NEED, need);
        startActivity(intent);
    }
}
