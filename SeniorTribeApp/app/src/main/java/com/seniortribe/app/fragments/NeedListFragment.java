package com.seniortribe.app.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.seniortribe.app.R;
import com.seniortribe.app.activities.CreateNeedActivity;
import com.seniortribe.app.activities.NeedDetailActivity;
import com.seniortribe.app.adapters.NeedAdapter;
import com.seniortribe.app.models.NeedSummary;
import com.seniortribe.app.utils.Constants;

import java.util.ArrayList;
import java.util.List;

public class NeedListFragment extends Fragment implements NeedAdapter.OnNeedClickListener {

    private EditText etSearch;
    private ChipGroup chipGroupStatus;
    private SwipeRefreshLayout swipeRefreshNeeds;
    private RecyclerView rvNeeds;
    private FloatingActionButton fabCreateNeed;

    private NeedAdapter adapter;
    private List<NeedSummary> allNeeds = new ArrayList<>();
    private FirebaseFirestore firestore;
    private String currentFilterStatus = "ALL";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_need_list, container, false);

        etSearch = view.findViewById(R.id.etSearch);
        chipGroupStatus = view.findViewById(R.id.chipGroupStatus);
        swipeRefreshNeeds = view.findViewById(R.id.swipeRefreshNeeds);
        rvNeeds = view.findViewById(R.id.rvNeeds);
        fabCreateNeed = view.findViewById(R.id.fabCreateNeed);

        try { firestore = FirebaseFirestore.getInstance(); } catch (Exception ignored) {}

        adapter = new NeedAdapter(requireContext(), this);
        rvNeeds.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvNeeds.setAdapter(adapter);

        fabCreateNeed.setOnClickListener(v -> startActivity(new Intent(requireContext(), CreateNeedActivity.class)));
        swipeRefreshNeeds.setOnRefreshListener(this::loadNeeds);

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filterNeeds(); }
            @Override public void afterTextChanged(Editable s) {}
        });

        chipGroupStatus.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty() || checkedIds.contains(R.id.chipAll)) {
                currentFilterStatus = "ALL";
            } else if (checkedIds.contains(R.id.chipOpen)) {
                currentFilterStatus = Constants.STATUS_OPEN;
            } else if (checkedIds.contains(R.id.chipAccepted)) {
                currentFilterStatus = Constants.STATUS_ACCEPTED;
            } else if (checkedIds.contains(R.id.chipCompleted)) {
                currentFilterStatus = Constants.STATUS_COMPLETED;
            }
            filterNeeds();
        });

        loadNeeds();
        return view;
    }

    private void loadNeeds() {
        swipeRefreshNeeds.setRefreshing(true);
        if (firestore == null) {
            allNeeds = getSampleNeeds();
            filterNeeds();
            swipeRefreshNeeds.setRefreshing(false);
            return;
        }

        firestore.collection(Constants.COL_NEEDS_SUMMARY)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    allNeeds.clear();
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        NeedSummary n = doc.toObject(NeedSummary.class);
                        n.setNeedId(doc.getId());
                        allNeeds.add(n);
                    }
                    if (allNeeds.isEmpty()) {
                        allNeeds = getSampleNeeds();
                    }
                    filterNeeds();
                    swipeRefreshNeeds.setRefreshing(false);
                })
                .addOnFailureListener(e -> {
                    if (allNeeds.isEmpty()) allNeeds = getSampleNeeds();
                    filterNeeds();
                    swipeRefreshNeeds.setRefreshing(false);
                });
    }

    private void filterNeeds() {
        String query = etSearch.getText().toString().trim().toLowerCase();
        List<NeedSummary> filtered = new ArrayList<>();
        for (NeedSummary item : allNeeds) {
            boolean matchStatus = "ALL".equals(currentFilterStatus) || currentFilterStatus.equalsIgnoreCase(item.getStatus());
            boolean matchQuery = query.isEmpty()
                    || (item.getTitle() != null && item.getTitle().toLowerCase().contains(query))
                    || (item.getCaseCode() != null && item.getCaseCode().toLowerCase().contains(query))
                    || (item.getDistrict() != null && item.getDistrict().toLowerCase().contains(query));

            if (matchStatus && matchQuery) {
                filtered.add(item);
            }
        }
        adapter.setNeeds(filtered);
    }

    private List<NeedSummary> getSampleNeeds() {
        List<NeedSummary> list = new ArrayList<>();
        NeedSummary n1 = new NeedSummary();
        n1.setNeedId("need_01");
        n1.setCaseCode("#ST-2026-001");
        n1.setTitle("Cụ bà neo đơn cần người tâm sự, trò chuyện cuối tuần");
        n1.setCategory(Constants.CAT_CONVERSATION);
        n1.setDistrict("Quận 8, TP.HCM");
        n1.setUrgency(Constants.URGENCY_MEDIUM);
        n1.setStatus(Constants.STATUS_OPEN);
        n1.setCreatedAt(System.currentTimeMillis() - 86400000);
        list.add(n1);

        NeedSummary n2 = new NeedSummary();
        n2.setNeedId("need_02");
        n2.setCaseCode("#ST-2026-002");
        n2.setTitle("Hỗ trợ sinh hoạt và thuốc men cơ bản cho cụ ông 78 tuổi");
        n2.setCategory(Constants.CAT_MEDICAL);
        n2.setDistrict("Quận 5, TP.HCM");
        n2.setUrgency(Constants.URGENCY_HIGH);
        n2.setStatus(Constants.STATUS_ACCEPTED);
        n2.setCreatedAt(System.currentTimeMillis() - 172800000);
        list.add(n2);
        return list;
    }

    @Override
    public void onNeedClick(NeedSummary need) {
        Intent intent = new Intent(requireContext(), NeedDetailActivity.class);
        intent.putExtra(Constants.EXTRA_NEED, need);
        startActivity(intent);
    }
}
