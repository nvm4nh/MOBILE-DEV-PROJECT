package com.seniortribe.app.adapters;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;
import androidx.core.widget.TextViewCompat;
import androidx.collection.LruCache;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.Blob;
import com.seniortribe.app.data.ChatMediaRepository;
import com.seniortribe.app.models.CallRecord;

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
 *
 * E06 · Tin ảnh hiện ảnh nhỏ (giải mã từ "thumb", chỉ giữ trong bộ nhớ), tin tệp hiện tên và
 * dung lượng; nhật ký cuộc gọi dựng từ needs_assignments/{id}/calls như tin hệ thống.
 */
public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface OnScheduleClickListener {
        void onScheduleClick();

        /** E02 · Bấm tin hệ thống về yêu cầu đổi lịch. */
        default void onRescheduleClick() { onScheduleClick(); }

        /** E06 · Bấm ảnh (xem lớn) hoặc tệp (mở / lưu). */
        default void onAttachmentClick(ChatMessage message) {}
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
        boolean reschedule;      // TYPE_SYSTEM: dòng về đổi lịch (E02)
        boolean call;            // TYPE_SYSTEM: nhật ký cuộc gọi (E06), bấm không mở lịch

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
    /** Ảnh nhỏ đã giải mã, chỉ trong bộ nhớ (không lưu xuống máy, F16). */
    private final LruCache<String, Bitmap> thumbs = new LruCache<String, Bitmap>(8 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap value) { return value.getByteCount(); }
    };

    public ChatAdapter(Context context, String currentUserId, @Nullable OnScheduleClickListener scheduleListener) {
        this.context = context;
        this.currentUserId = currentUserId != null ? currentUserId : "";
        this.scheduleListener = scheduleListener;
    }

    /**
     * @param recorderUid đầu mối ghi nhận, để ghi "· Đầu mối" sau tên người gửi
     */
    public void setData(List<ChatMessage> messages, List<ScheduleSession> schedules, @Nullable String recorderUid) {
        setData(messages, schedules, null, recorderUid);
    }

    /**
     * @param calls nhật ký cuộc gọi (E06), dựng thành dòng hệ thống như lịch hẹn
     */
    public void setData(List<ChatMessage> messages, List<ScheduleSession> schedules,
                        @Nullable List<CallRecord> calls, @Nullable String recorderUid) {
        List<Row> events = new ArrayList<>();
        if (calls != null) {
            for (CallRecord c : calls) addCallRow(events, c);
        }
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
        boolean reschedule = s.isReschedule();

        Row proposed = new Row(TYPE_SYSTEM, s.getCreatedAtMillis());
        proposed.text = context.getString(reschedule ? R.string.chat_system_resched_proposed
                : R.string.chat_system_proposed, slot);
        proposed.icon = R.drawable.ic_schedule;
        proposed.fg = R.color.status_scheduled;
        proposed.bg = R.color.status_scheduled_bg;
        proposed.reschedule = reschedule;
        out.add(proposed);

        long decidedAt = s.getDecidedAtMillis();
        if (decidedAt == 0) return;
        // Lịch đã chốt rồi được đổi (RESCHEDULED) vẫn hiện dòng "Đã chốt lịch" lúc chốt
        if (ScheduleSession.CONFIRMED.equals(s.getStatus()) || ScheduleSession.RESCHEDULED.equals(s.getStatus())) {
            Row r = new Row(TYPE_SYSTEM, decidedAt);
            r.reschedule = reschedule;
            r.text = context.getString(reschedule ? R.string.chat_system_resched_confirmed
                    : R.string.chat_system_confirmed, slot);
            r.icon = R.drawable.ic_check;
            r.fg = R.color.status_completed;
            r.bg = R.color.status_completed_bg;
            out.add(r);
        } else if (ScheduleSession.DECLINED.equals(s.getStatus())) {
            Row r = new Row(TYPE_SYSTEM, decidedAt);
            r.reschedule = reschedule;
            r.text = context.getString(reschedule ? R.string.chat_system_resched_declined
                    : R.string.chat_system_declined, s.getDeclineReason() != null ? s.getDeclineReason() : "");
            r.icon = R.drawable.ic_warning;
            r.fg = R.color.warning_text;
            r.bg = R.color.warning_bg;
            out.add(r);
        }
    }

    /** E06 · "Cuộc gọi video · 3 phút 12 giây", "Cuộc gọi nhỡ từ Mai"… */
    private void addCallRow(List<Row> out, CallRecord c) {
        String caller = !TextUtils.isEmpty(c.getCallerName()) ? c.getCallerName()
                : context.getString(R.string.chat_sender_unknown);
        Row r = new Row(TYPE_SYSTEM, c.getEndedAtMillis() > 0 ? c.getEndedAtMillis() : c.getCreatedAtMillis());
        r.call = true;
        r.icon = c.isVideo() ? R.drawable.ic_videocam : R.drawable.ic_call;
        r.fg = R.color.text_secondary;
        r.bg = R.color.surface_variant;
        String status = c.getStatus() != null ? c.getStatus() : "";
        switch (status) {
            case CallRecord.ENDED: {
                long sec = c.getDurationSec() != null ? c.getDurationSec() : 0;
                String d = sec >= 60 ? context.getString(R.string.call_duration_min, (int) (sec / 60), (int) (sec % 60))
                        : context.getString(R.string.call_duration_sec, (int) sec);
                r.text = context.getString(c.isVideo() ? R.string.chat_call_log_video : R.string.chat_call_log_voice, d);
                break;
            }
            case CallRecord.MISSED:
                r.text = context.getString(R.string.chat_call_log_missed, caller);
                r.icon = R.drawable.ic_call_missed;
                r.fg = R.color.error;
                r.bg = R.color.error_container;
                break;
            case CallRecord.DECLINED:
                r.text = context.getString(R.string.chat_call_log_declined,
                        currentUserId.equals(c.getCalleeUid()) ? context.getString(R.string.chat_you) : context.getString(R.string.chat_other_side));
                break;
            case CallRecord.CANCELLED:
                r.text = context.getString(R.string.chat_call_log_cancelled, caller);
                break;
            case CallRecord.RINGING:
            case CallRecord.ACCEPTED:
                r.text = context.getString(R.string.chat_call_log_ringing, caller);
                break;
            default:
                return;
        }
        out.add(r);
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
            bindAttachment(vh, msg);
            boolean hasText = !TextUtils.isEmpty(msg.getText());
            vh.tvMessage.setVisibility(hasText || !msg.hasAttachment() ? View.VISIBLE : View.GONE);
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
                final boolean reschedule = row.reschedule;
                vh.itemView.setOnClickListener(scheduleListener == null || row.call ? null : v -> {
                    if (reschedule) scheduleListener.onRescheduleClick();
                    else scheduleListener.onScheduleClick();
                });
            }
        }
    }

    /** E06 · Ảnh nhỏ hoặc dòng tệp trong bong bóng; bấm để xem / mở. */
    private void bindAttachment(MessageViewHolder vh, ChatMessage msg) {
        if (vh.ivAttachment == null || vh.layoutFile == null) return;
        boolean image = msg.isImage();
        boolean file = msg.isFile();
        vh.ivAttachment.setVisibility(image ? View.VISIBLE : View.GONE);
        vh.layoutFile.setVisibility(file ? View.VISIBLE : View.GONE);
        if (image) {
            Bitmap bmp = thumb(msg);
            if (bmp != null) vh.ivAttachment.setImageBitmap(bmp);
            else vh.ivAttachment.setImageResource(R.drawable.ic_image);
            String sender = !TextUtils.isEmpty(msg.getSenderName()) ? msg.getSenderName() : "";
            vh.ivAttachment.setContentDescription(context.getString(R.string.chat_image_content_description, sender));
            vh.ivAttachment.setOnClickListener(msg.isPending() || scheduleListener == null ? null
                    : v -> scheduleListener.onAttachmentClick(msg));
        } else {
            vh.ivAttachment.setImageDrawable(null);
            vh.ivAttachment.setOnClickListener(null);
        }
        if (file) {
            vh.tvFileName.setText(msg.getFileName());
            vh.tvFileSize.setText(fileLabel(msg));
            vh.layoutFile.setContentDescription(context.getString(R.string.chat_attach_file_label, msg.getFileName()));
            vh.layoutFile.setOnClickListener(msg.isPending() || scheduleListener == null ? null
                    : v -> scheduleListener.onAttachmentClick(msg));
        } else {
            vh.layoutFile.setOnClickListener(null);
        }
    }

    @Nullable
    private Bitmap thumb(ChatMessage msg) {
        String key = msg.getMessageId() != null ? msg.getMessageId() : msg.getAttachmentId();
        if (key == null) return null;
        Bitmap cached = thumbs.get(key);
        if (cached != null) return cached;
        Blob blob = msg.getThumb();
        if (blob == null) return null;
        byte[] bytes = blob.toBytes();
        Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        if (bmp != null) thumbs.put(key, bmp);
        return bmp;
    }

    /** "PDF · 240 KB" */
    private static String fileLabel(ChatMessage msg) {
        String name = msg.getFileName() != null ? msg.getFileName() : "";
        int dot = name.lastIndexOf('.');
        String ext = dot >= 0 && dot < name.length() - 1 ? name.substring(dot + 1).toUpperCase(java.util.Locale.ROOT) : "";
        String size = ChatMediaRepository.formatSize(msg.getSize());
        return ext.isEmpty() ? size : ext + " · " + size;
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
        final TextView tvSender, tvMessage, tvTime, tvFileName, tvFileSize;
        final ImageView ivAttachment;
        final View layoutFile;

        MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAttachment = itemView.findViewById(R.id.ivAttachment);
            layoutFile = itemView.findViewById(R.id.layoutFile);
            tvFileName = itemView.findViewById(R.id.tvFileName);
            tvFileSize = itemView.findViewById(R.id.tvFileSize);
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
