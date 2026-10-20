package com.seniortribe.app.adapters;

import android.content.Context;
import android.content.res.ColorStateList;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.seniortribe.app.R;
import com.seniortribe.app.models.NeedSummary;
import com.seniortribe.app.utils.Constants;
import com.seniortribe.app.utils.DateTimeUtils;
import com.seniortribe.app.utils.NeedStatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * F15 · Lịch (Figma): các lịch hẹn nhóm theo ngày, mỗi lịch có ô giờ bên trái.
 *  - Sắp tới: lịch đã thống nhất (SCHEDULED), sớm nhất trước; dưới cùng là nhóm "Gần đây"
 *    gồm vài lần hỗ trợ vừa thực hiện.
 *  - Lịch sử: đã báo hỗ trợ (DELIVERED) hoặc đã hoàn thành (COMPLETED), mới nhất trước.
 */
public class ScheduleAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface OnScheduleClickListener {
        void onScheduleClick(NeedSummary need);
    }

    /** Số lần hỗ trợ gần nhất hiện thêm dưới tab "Sắp tới". */
    public static final int RECENT_IN_UPCOMING = 3;

    private static final int TYPE_HEADER = 1;
    private static final int TYPE_ITEM = 2;

    static final class Row {
        final int type;
        final String label;
        final NeedSummary need;
        final boolean upcoming;

        Row(String label) {
            this.type = TYPE_HEADER;
            this.label = label;
            this.need = null;
            this.upcoming = false;
        }

        Row(NeedSummary need, boolean upcoming) {
            this.type = TYPE_ITEM;
            this.label = null;
            this.need = need;
            this.upcoming = upcoming;
        }
    }

    private final Context context;
    private final OnScheduleClickListener listener;
    private List<Row> rows = new ArrayList<>();

    public ScheduleAdapter(Context context, OnScheduleClickListener listener) {
        this.context = context;
        this.listener = listener;
    }

    /** @return số lịch hiển thị (không tính dòng tiêu đề) */
    public int show(List<NeedSummary> all, boolean history) {
        List<Row> out = new ArrayList<>();
        int count;
        if (history) {
            List<NeedSummary> done = history(all);
            addGroupedByDay(out, done, false);
            count = done.size();
        } else {
            List<NeedSummary> next = upcoming(all);
            addGroupedByDay(out, next, true);
            count = next.size();
            List<NeedSummary> recent = history(all);
            if (next.isEmpty() && !recent.isEmpty()) {
                out.add(new Row(context.getString(R.string.schedule_empty_upcoming_inline)));
            }
            if (!recent.isEmpty()) {
                out.add(new Row(context.getString(R.string.schedule_group_recent)));
                for (int i = 0; i < Math.min(RECENT_IN_UPCOMING, recent.size()); i++) {
                    out.add(new Row(recent.get(i), false));
                }
            }
        }
        rows = out;
        notifyDataSetChanged();
        return count;
    }

    private void addGroupedByDay(List<Row> out, List<NeedSummary> needs, boolean upcoming) {
        long lastDay = Long.MIN_VALUE;
        for (NeedSummary n : needs) {
            long at = n.getScheduledAtMillis();
            if (at == 0) {
                if (lastDay != 0) out.add(new Row(context.getString(R.string.schedule_no_time)));
                lastDay = 0;
            } else if (lastDay == Long.MIN_VALUE || lastDay == 0 || !DateTimeUtils.isSameDay(lastDay, at)) {
                out.add(new Row(DateTimeUtils.formatWeekdayDate(at)));
                lastDay = at;
            }
            out.add(new Row(n, upcoming));
        }
    }

    /** Lịch đã thống nhất, sớm nhất trước. */
    public static List<NeedSummary> upcoming(List<NeedSummary> needs) {
        List<NeedSummary> out = new ArrayList<>();
        for (NeedSummary n : needs) {
            if (Constants.STATUS_SCHEDULED.equals(n.getStatus())) out.add(n);
        }
        Collections.sort(out, (a, b) -> Long.compare(a.getScheduledAtMillis(), b.getScheduledAtMillis()));
        return out;
    }

    /** Đã báo hỗ trợ hoặc đã hoàn thành, mới nhất trước. */
    public static List<NeedSummary> history(List<NeedSummary> needs) {
        List<NeedSummary> out = new ArrayList<>();
        for (NeedSummary n : needs) {
            if (Constants.STATUS_DELIVERED.equals(n.getStatus()) || Constants.STATUS_COMPLETED.equals(n.getStatus())) {
                out.add(n);
            }
        }
        Collections.sort(out, (a, b) -> Long.compare(b.getScheduledAtMillis(), a.getScheduledAtMillis()));
        return out;
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position).type;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(context);
        if (viewType == TYPE_HEADER) {
            return new HeaderViewHolder(inflater.inflate(R.layout.item_schedule_header, parent, false));
        }
        return new ScheduleViewHolder(inflater.inflate(R.layout.item_schedule, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Row row = rows.get(position);
        if (holder instanceof HeaderViewHolder) {
            ((HeaderViewHolder) holder).tvLabel.setText(row.label);
            return;
        }
        ScheduleViewHolder vh = (ScheduleViewHolder) holder;
        NeedSummary n = row.need;
        long start = n.getScheduledAtMillis();
        long end = n.getScheduledEndAtMillis();
        vh.tvStart.setText(start > 0 ? DateTimeUtils.formatTime(start) : "--:--");
        vh.tvEnd.setText(end > start ? DateTimeUtils.formatTime(end) : "");
        vh.tvEnd.setVisibility(end > start ? View.VISIBLE : View.GONE);
        // Sắp tới: ô giờ xanh nhạt; đã qua: ô giờ xám (Figma)
        vh.timeCell.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(context,
                row.upcoming ? R.color.primary_light : R.color.surface_variant)));
        vh.tvStart.setTextColor(ContextCompat.getColor(context, row.upcoming ? R.color.primary : R.color.text_secondary));

        vh.tvTitle.setText(titleWithArea(n));
        vh.tvAssignee.setText(context.getString(R.string.schedule_item_assignee, safe(n.getAssigneeName())));
        NeedStatus.bindChip(vh.tvStatus, n.getStatus());

        String when = start > 0 ? DateTimeUtils.formatWeekdayDate(start) + ", "
                + DateTimeUtils.formatTimeRange(start, end) : context.getString(R.string.schedule_no_time);
        vh.itemView.setContentDescription(when + ". " + vh.tvTitle.getText() + ". "
                + vh.tvAssignee.getText() + ". " + vh.tvStatus.getText());
        vh.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onScheduleClick(n);
        });
    }

    /** "Trò chuyện với cụ bà · Phường 7": tên việc và phần đầu của khu vực. */
    private String titleWithArea(NeedSummary n) {
        String area = n.getArea();
        if (!TextUtils.isEmpty(area) && area.contains(",")) area = area.substring(0, area.indexOf(',')).trim();
        return TextUtils.isEmpty(area) ? safe(n.getTitle()) : safe(n.getTitle()) + " · " + area;
    }

    private static String safe(String s) {
        return s != null ? s : "";
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        final TextView tvLabel;

        HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            tvLabel = itemView.findViewById(R.id.tvLabel);
        }
    }

    static class ScheduleViewHolder extends RecyclerView.ViewHolder {
        final View timeCell;
        final TextView tvStart, tvEnd, tvTitle, tvAssignee, tvStatus;

        ScheduleViewHolder(@NonNull View itemView) {
            super(itemView);
            timeCell = itemView.findViewById(R.id.layoutTimeCell);
            tvStart = itemView.findViewById(R.id.tvStartTime);
            tvEnd = itemView.findViewById(R.id.tvEndTime);
            tvTitle = itemView.findViewById(R.id.tvScheduleTitle);
            tvAssignee = itemView.findViewById(R.id.tvScheduleAssignee);
            tvStatus = itemView.findViewById(R.id.tvStatus);
        }
    }
}
