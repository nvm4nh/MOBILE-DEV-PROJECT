package com.seniortribe.app.adapters;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;
import androidx.core.widget.TextViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.seniortribe.app.R;
import com.seniortribe.app.models.ChatMessage;
import com.seniortribe.app.models.ScheduleSession;
import com.seniortribe.app.utils.DateTimeUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * F11 · Luồng chat: tin của mình, tin của bên kia, dòng chia ngày ("Hôm nay") và tin hệ thống
 * về lịch hẹn ("Đề xuất lịch: Thứ 7, 19/10 · 14:00 – 16:00").
 *
 * Tin hệ thống không lưu trong messages (rules chỉ cho người dùng gửi tin văn bản của chính
 * mình); chúng được dựng từ các đề xuất trong needs_assignments/{id}/schedules và xếp theo
 * thời gian cùng tin nhắn, nên không thể bị làm giả từ phía client.
 */
public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface OnScheduleClickListener {
        void onScheduleClick();
    }

    private static final int TYPE_ME = 1;
    private static final int TYPE_OTHER = 2;
    private static final int TYPE_DAY = 3;
    private static final int TYPE_SYSTEM = 4;

    /** Một dòng trong luồng chat. */
    static final class Row {
        final int type;
        final long at;
        ChatMessage message;     // TYPE_ME, TYPE_OTHER
        String text;             // TYPE_DAY, TYPE_SYSTEM
        @DrawableRes int icon;   // TYPE_SYSTEM
        @ColorRes int fg, bg;    // TYPE_SYSTEM

        Row(int type, long at) {
            this.type = type;
            this.at = at;
        }
    }

    private final Context context;
    private final String currentUserId;
    @Nullable private final OnScheduleClickListener scheduleListener;
    private List<Row> rows = new ArrayList<>();
    @Nullable private String recorderUid;

    public ChatAdapter(Context context, String currentUserId, @Nullable OnScheduleClickListener scheduleListener) {
        this.context = context;
        this.currentUserId = currentUserId != null ? currentUserId : "";
        this.scheduleListener = scheduleListener;
    }

    /**
     * @param recorderUid đầu mối ghi nhận, để ghi "· Đầu mối" sau tên người gửi
     */
    public void setData(List<ChatMessage> messages, List<ScheduleSession> schedules, @Nullable String recorderUid) {
        List<Row> events = new ArrayList<>();
        if (messages != null) {
            for (ChatMessage m : messages) {
                Row r = new Row(currentUserId.equals(m.getSenderUid()) ? TYPE_ME : TYPE_OTHER, m.getTimestamp());
                r.message = m;
                events.add(r);
            }
        }
        if (schedules != null) {
            for (ScheduleSession s : schedules) addScheduleRows(events, s);
        }
        // Sắp xếp ổn định: cùng thời điểm thì giữ thứ tự thêm vào
        Collections.sort(events, (a, b) -> Long.compare(a.at, b.at));

        List<Row> out = new ArrayList<>();
        long lastDay = Long.MIN_VALUE;
        for (Row r : events) {
            if (lastDay == Long.MIN_VALUE || !DateTimeUtils.isSameDay(lastDay, r.at)) {
                Row day = new Row(TYPE_DAY, r.at);
                day.text = DateTimeUtils.formatDayLabel(r.at);
                out.add(day);
                lastDay = r.at;
            }
            out.add(r);
        }
        this.recorderUid = recorderUid;
        this.rows = out;
        notifyDataSetChanged();
    }

    private void addScheduleRows(List<Row> out, ScheduleSession s) {
        if (s.getStartAtMillis() == 0) return;
        String slot = DateTimeUtils.formatSlot(s.getStartAtMillis(), s.getEndAtMillis());

        Row proposed = new Row(TYPE_SYSTEM, s.getCreatedAtMillis());
        proposed.text = context.getString(R.string.chat_system_proposed, slot);
        proposed.icon = R.drawable.ic_schedule;
        proposed.fg = R.color.status_scheduled;
        proposed.bg = R.color.status_scheduled_bg;
        out.add(proposed);

        long decidedAt = s.getDecidedAtMillis();
        if (decidedAt == 0) return;
        if (ScheduleSession.CONFIRMED.equals(s.getStatus())) {
            Row r = new Row(TYPE_SYSTEM, decidedAt);
            r.text = context.getString(R.string.chat_system_confirmed, slot);
            r.icon = R.drawable.ic_check;
            r.fg = R.color.status_completed;
            r.bg = R.color.status_completed_bg;
            out.add(r);
        } else if (ScheduleSession.DECLINED.equals(s.getStatus())) {
            Row r = new Row(TYPE_SYSTEM, decidedAt);
            r.text = context.getString(R.string.chat_system_declined,
                    s.getDeclineReason() != null ? s.getDeclineReason() : "");
            r.icon = R.drawable.ic_warning;
            r.fg = R.color.warning_text;
            r.bg = R.color.warning_bg;
            out.add(r);
        }
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position).type;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(context);
        switch (viewType) {
            case TYPE_ME:
                return new MessageViewHolder(inflater.inflate(R.layout.item_chat_message_me, parent, false));
            case TYPE_OTHER:
                return new MessageViewHolder(inflater.inflate(R.layout.item_chat_message_other, parent, false));
            case TYPE_DAY:
                return new LabelViewHolder(inflater.inflate(R.layout.item_chat_day, parent, false));
            case TYPE_SYSTEM:
            default:
                return new LabelViewHolder(inflater.inflate(R.layout.item_chat_system, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Row row = rows.get(position);
        if (holder instanceof MessageViewHolder) {
            MessageViewHolder vh = (MessageViewHolder) holder;
            ChatMessage msg = row.message;
            vh.tvMessage.setText(msg.getText());
            vh.tvTime.setText(msg.isPending()
                    ? context.getString(R.string.chat_sending)
                    : DateTimeUtils.formatTime(msg.getTimestamp()));
            if (vh.tvSender != null) vh.tvSender.setText(senderLabel(msg));
        } else {
            LabelViewHolder vh = (LabelViewHolder) holder;
            vh.tvLabel.setText(row.text);
            if (row.type == TYPE_SYSTEM) {
                int fg = ContextCompat.getColor(context, row.fg);
                vh.tvLabel.setTextColor(fg);
                vh.tvLabel.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(context, row.bg)));
                Drawable icon = AppCompatResources.getDrawable(context, row.icon);
                if (icon != null) {
                    icon = icon.mutate();
                    int size = context.getResources().getDimensionPixelSize(R.dimen.chat_system_icon);
                    icon.setBounds(0, 0, size, size);
                }
                vh.tvLabel.setCompoundDrawablesRelative(icon, null, null, null);
                TextViewCompat.setCompoundDrawableTintList(vh.tvLabel, ColorStateList.valueOf(fg));
                vh.itemView.setOnClickListener(scheduleListener != null ? v -> scheduleListener.onScheduleClick() : null);
            }
        }
    }

    /** "Trần Thị Mai · Đầu mối" hoặc "Nguyễn Minh Khoa · Đại diện". */
    private String senderLabel(ChatMessage msg) {
        String name = !TextUtils.isEmpty(msg.getSenderName()) ? msg.getSenderName()
                : context.getString(R.string.chat_sender_unknown);
        boolean recorder = recorderUid != null && recorderUid.equals(msg.getSenderUid());
        return context.getString(R.string.chat_sender_role, name,
                context.getString(recorder ? R.string.chat_role_recorder : R.string.chat_role_representative));
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    static class MessageViewHolder extends RecyclerView.ViewHolder {
        final TextView tvSender, tvMessage, tvTime;

        MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSender = itemView.findViewById(R.id.tvSender); // không có ở tin của mình
            tvMessage = itemView.findViewById(R.id.tvMessage);
            tvTime = itemView.findViewById(R.id.tvTime);
        }
    }

    static class LabelViewHolder extends RecyclerView.ViewHolder {
        final TextView tvLabel;

        LabelViewHolder(@NonNull View itemView) {
            super(itemView);
            tvLabel = itemView.findViewById(R.id.tvLabel);
        }
    }
}
