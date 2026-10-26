package com.seniortribe.app.adapters;

import android.content.Context;
import android.content.res.ColorStateList;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.seniortribe.app.R;
import com.seniortribe.app.data.CoordinationRepository;
import com.seniortribe.app.data.NeedRepository;
import com.seniortribe.app.models.NeedEvent;
import com.seniortribe.app.utils.DateTimeUtils;
import com.seniortribe.app.utils.NeedStatus;

import java.util.ArrayList;
import java.util.List;

/** F15 · Dòng thời gian lịch sử của một nhu cầu. */
public class NeedEventAdapter extends RecyclerView.Adapter<NeedEventAdapter.EventViewHolder> {

    private final Context context;
    private List<NeedEvent> events = new ArrayList<>();

    public NeedEventAdapter(Context context) {
        this.context = context;
    }

    public void setEvents(List<NeedEvent> list) {
        this.events = list != null ? list : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public EventViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new EventViewHolder(LayoutInflater.from(context).inflate(R.layout.item_need_event, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull EventViewHolder h, int position) {
        NeedEvent e = events.get(position);
        h.tvTitle.setText(label(e.getType()));
        String time = e.getAtMillis() > 0 ? DateTimeUtils.formatDateTime(e.getAtMillis()) : "";
        String by = e.getByName() != null ? e.getByName() : "";
        h.tvMeta.setText(TextUtils.isEmpty(time) ? by : by + " · " + time);
        h.tvNote.setVisibility(TextUtils.isEmpty(e.getNote()) ? View.GONE : View.VISIBLE);
        h.tvNote.setText(e.getNote());
        int color = e.getToStatus() != null ? NeedStatus.textColor(e.getToStatus()) : R.color.primary;
        h.ivDot.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(context, color)));
        h.viewLine.setVisibility(position == events.size() - 1 ? View.INVISIBLE : View.VISIBLE);
    }

    /** Nhãn tiếng Việt của một loại sự kiện; dùng chung cho thông báo (E01). */
    public static int label(String type) {
        if (type == null) return R.string.event_unknown;
        switch (type) {
            case NeedRepository.EVENT_SUBMITTED: return R.string.event_submitted;
            case NeedRepository.EVENT_RESUBMITTED: return R.string.event_resubmitted;
            case NeedRepository.EVENT_APPROVED: return R.string.event_approved;
            case NeedRepository.EVENT_REVISION_REQUESTED: return R.string.event_revision_requested;
            case NeedRepository.EVENT_ACCEPTED: return R.string.event_accepted;
            case NeedRepository.EVENT_RETURNED: return R.string.event_returned;
            case NeedRepository.EVENT_CANCELLED: return R.string.event_cancelled;
            case CoordinationRepository.EVENT_SCHEDULE_PROPOSED: return R.string.event_schedule_proposed;
            case CoordinationRepository.EVENT_SCHEDULE_DECLINED: return R.string.event_schedule_declined;
            case CoordinationRepository.EVENT_SCHEDULED: return R.string.event_scheduled;
            case CoordinationRepository.EVENT_DELIVERED: return R.string.event_delivered;
            case CoordinationRepository.EVENT_RECONCILE_PENDING: return R.string.event_reconcile_pending;
            case CoordinationRepository.EVENT_COMPLETED: return R.string.event_completed;
            case CoordinationRepository.EVENT_RESCHEDULE_PROPOSED: return R.string.event_reschedule_proposed;
            case CoordinationRepository.EVENT_RESCHEDULE_DECLINED: return R.string.event_reschedule_declined;
            case CoordinationRepository.EVENT_RESCHEDULE_WITHDRAWN: return R.string.event_reschedule_withdrawn;
            case CoordinationRepository.EVENT_RESCHEDULED: return R.string.event_rescheduled;
            default: return R.string.event_unknown;
        }
    }

    @Override
    public int getItemCount() {
        return events.size();
    }

    static class EventViewHolder extends RecyclerView.ViewHolder {
        final ImageView ivDot;
        final View viewLine;
        final TextView tvTitle, tvMeta, tvNote;

        EventViewHolder(@NonNull View itemView) {
            super(itemView);
            ivDot = itemView.findViewById(R.id.ivDot);
            viewLine = itemView.findViewById(R.id.viewLine);
            tvTitle = itemView.findViewById(R.id.tvEventTitle);
            tvMeta = itemView.findViewById(R.id.tvEventMeta);
            tvNote = itemView.findViewById(R.id.tvEventNote);
        }
    }
}
