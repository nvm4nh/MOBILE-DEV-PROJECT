package com.seniortribe.app.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.seniortribe.app.R;
import com.seniortribe.app.models.NeedSummary;
import com.seniortribe.app.utils.DateTimeUtils;
import com.seniortribe.app.utils.NeedStatus;

import java.util.ArrayList;
import java.util.List;

/** F15 · Lịch hẹn đã thống nhất và các lần hỗ trợ đã thực hiện. */
public class ScheduleAdapter extends RecyclerView.Adapter<ScheduleAdapter.ScheduleViewHolder> {

    public interface OnScheduleClickListener {
        void onScheduleClick(NeedSummary need);
    }

    private final Context context;
    private final OnScheduleClickListener listener;
    private List<NeedSummary> items = new ArrayList<>();

    public ScheduleAdapter(Context context, OnScheduleClickListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setNeeds(List<NeedSummary> needs) {
        this.items = needs != null ? needs : new ArrayList<>();
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
        NeedSummary n = items.get(position);
        holder.tvDateTime.setText(n.getScheduledAtMillis() > 0
                ? DateTimeUtils.formatDateTime(n.getScheduledAtMillis())
                : context.getString(R.string.schedule_no_time));
        NeedStatus.bindChip(holder.tvStatus, n.getStatus());
        holder.tvLocation.setText(context.getString(R.string.schedule_item_title, safe(n.getCaseCode()), safe(n.getTitle())));
        holder.tvNotes.setText(context.getString(R.string.schedule_item_meta, safe(n.getScheduledPlace()),
                safe(n.getAssigneeName()), safe(n.getAssignedOrgName())));
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onScheduleClick(n);
        });
    }

    private static String safe(String s) {
        return s != null ? s : "";
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ScheduleViewHolder extends RecyclerView.ViewHolder {
        TextView tvDateTime, tvLocation, tvNotes, tvStatus;

        ScheduleViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDateTime = itemView.findViewById(R.id.tvDateTime);
            tvLocation = itemView.findViewById(R.id.tvLocation);
            tvNotes = itemView.findViewById(R.id.tvNotes);
            tvStatus = itemView.findViewById(R.id.tvStatus);
        }
    }
}
