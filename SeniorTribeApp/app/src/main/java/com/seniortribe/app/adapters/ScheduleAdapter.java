package com.seniortribe.app.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.seniortribe.app.R;
import com.seniortribe.app.models.ScheduleSession;
import com.seniortribe.app.utils.DateTimeUtils;

import java.util.ArrayList;
import java.util.List;

public class ScheduleAdapter extends RecyclerView.Adapter<ScheduleAdapter.ScheduleViewHolder> {

    private final Context context;
    private List<ScheduleSession> sessionList = new ArrayList<>();

    public ScheduleAdapter(Context context) {
        this.context = context;
    }

    public void setSessions(List<ScheduleSession> sessions) {
        this.sessionList = sessions != null ? sessions : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ScheduleViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_schedule, parent, false);
        return new ScheduleViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ScheduleViewHolder holder, int position) {
        ScheduleSession session = sessionList.get(position);
        holder.tvDateTime.setText(DateTimeUtils.formatDateTime(session.getProposedTime()));
        holder.tvLocation.setText(session.getLocation() != null ? session.getLocation() : "Địa điểm thăm hỏi");
        holder.tvNotes.setText(session.getNotes() != null ? session.getNotes() : "Thăm hỏi và trò chuyện cùng cụ");
        holder.tvStatus.setText(session.isAgreedByElderly() ? "Cụ đã đồng ý" : "Chờ xác nhận");
    }

    @Override
    public int getItemCount() {
        return sessionList.size();
    }

    static class ScheduleViewHolder extends RecyclerView.ViewHolder {
        TextView tvDateTime, tvLocation, tvNotes, tvStatus;
        public ScheduleViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDateTime = itemView.findViewById(R.id.tvDateTime);
            tvLocation = itemView.findViewById(R.id.tvLocation);
            tvNotes = itemView.findViewById(R.id.tvNotes);
            tvStatus = itemView.findViewById(R.id.tvStatus);
        }
    }
}
