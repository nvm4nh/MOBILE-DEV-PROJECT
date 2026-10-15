package com.seniortribe.app.fragments;

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
import com.seniortribe.app.adapters.ScheduleAdapter;
import com.seniortribe.app.models.ScheduleSession;

import java.util.ArrayList;
import java.util.List;

public class ScheduleFragment extends Fragment {

    private RecyclerView rvSchedules;
    private ScheduleAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_schedule, container, false);
        rvSchedules = view.findViewById(R.id.rvSchedules);

        adapter = new ScheduleAdapter(requireContext());
        rvSchedules.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvSchedules.setAdapter(adapter);

        loadSchedules();
        return view;
    }

    private void loadSchedules() {
        List<ScheduleSession> list = new ArrayList<>();
        ScheduleSession s1 = new ScheduleSession();
        s1.setScheduleId("sch_01");
        s1.setNeedId("need_01");
        s1.setProposedTime(System.currentTimeMillis() + 86400000 * 2);
        s1.setAgreedByElderly(true);
        s1.setLocation("Hẻm 45 đường Phạm Thế Hiển, Phường 3, Quận 8");
        s1.setNotes("Thăm hỏi sức khỏe và tặng quà bánh");
        list.add(s1);

        adapter.setSessions(list);
    }
}
