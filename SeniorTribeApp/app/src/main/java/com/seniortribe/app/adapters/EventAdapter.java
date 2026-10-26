package com.seniortribe.app.adapters;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.seniortribe.app.R;
import com.seniortribe.app.models.CommunityEvent;
import com.seniortribe.app.utils.DateTimeUtils;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/** E07 · Thẻ sự kiện sắp diễn ra. */
public class EventAdapter extends RecyclerView.Adapter<EventAdapter.Holder> {

    public interface OnEventClickListener {
        void onEventClick(CommunityEvent event);
    }

    private final Context context;
    private final OnEventClickListener listener;
    private List<CommunityEvent> events = new ArrayList<>();
    @Nullable private String myOrgId;

    public EventAdapter(Context context, OnEventClickListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setData(@Nullable List<CommunityEvent> list, @Nullable String myOrgId) {
        this.events = list != null ? new ArrayList<>(list) : new ArrayList<>();
        this.myOrgId = myOrgId;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(context).inflate(R.layout.item_event, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        CommunityEvent e = events.get(position);
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(e.getStartMillis());
        h.tvDay.setText(String.valueOf(c.get(Calendar.DAY_OF_MONTH)));
        h.tvMonth.setText(context.getString(R.string.event_month, c.get(Calendar.MONTH) + 1));
        h.tvTitle.setText(e.getTitle());
        h.tvOrg.setText(e.getOrgName());
        h.tvTime.setText(context.getString(R.string.event_time_range,
                DateTimeUtils.weekday(e.getStartMillis()),
                DateTimeUtils.formatTimeRange(e.getStartMillis(), e.getEndMillis())));
        String place = e.getPlace();
        if (!TextUtils.isEmpty(e.getArea())) place = place + ", " + e.getArea();
        h.tvPlace.setText(place);
        h.progress.setProgress(e.getFillPercent());
        h.tvCapacity.setText(e.isFull()
                ? context.getString(R.string.event_capacity_full, (int) e.getRegisteredCount(), (int) e.getCapacity())
                : context.getString(R.string.event_capacity, (int) e.getRegisteredCount(), (int) e.getCapacity()));
        boolean mine = myOrgId != null && myOrgId.equals(e.getOrgId());
        h.tvMine.setVisibility(mine ? View.VISIBLE : View.GONE);
        if (mine) h.tvMine.setText(R.string.event_my_org_owner);
        h.itemView.setOnClickListener(v -> listener.onEventClick(e));
    }

    @Override
    public int getItemCount() {
        return events.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView tvDay, tvMonth, tvTitle, tvOrg, tvTime, tvPlace, tvCapacity, tvMine;
        final LinearProgressIndicator progress;

        Holder(View v) {
            super(v);
            tvDay = v.findViewById(R.id.tvEventDay);
            tvMonth = v.findViewById(R.id.tvEventMonth);
            tvTitle = v.findViewById(R.id.tvEventTitle);
            tvOrg = v.findViewById(R.id.tvEventOrg);
            tvTime = v.findViewById(R.id.tvEventTime);
            tvPlace = v.findViewById(R.id.tvEventPlace);
            tvCapacity = v.findViewById(R.id.tvEventCapacity);
            tvMine = v.findViewById(R.id.tvEventMine);
            progress = v.findViewById(R.id.progressCapacity);
        }
    }
}
