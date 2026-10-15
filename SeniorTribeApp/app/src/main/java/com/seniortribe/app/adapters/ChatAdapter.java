package com.seniortribe.app.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.seniortribe.app.R;
import com.seniortribe.app.models.ChatMessage;
import com.seniortribe.app.utils.DateTimeUtils;

import java.util.ArrayList;
import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_ME = 1;
    private static final int TYPE_OTHER = 2;

    private final Context context;
    private final String currentUserId;
    private List<ChatMessage> messageList = new ArrayList<>();

    public ChatAdapter(Context context, String currentUserId) {
        this.context = context;
        this.currentUserId = currentUserId != null ? currentUserId : "";
    }

    public void setMessages(List<ChatMessage> messages) {
        this.messageList = messages != null ? messages : new ArrayList<>();
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        ChatMessage msg = messageList.get(position);
        if (currentUserId.equals(msg.getSenderUid())) {
            return TYPE_ME;
        }
        return TYPE_OTHER;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_ME) {
            View view = LayoutInflater.from(context).inflate(R.layout.item_chat_message_me, parent, false);
            return new MeViewHolder(view);
        } else {
            View view = LayoutInflater.from(context).inflate(R.layout.item_chat_message_other, parent, false);
            return new OtherViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage msg = messageList.get(position);
        if (holder instanceof MeViewHolder) {
            MeViewHolder vh = (MeViewHolder) holder;
            vh.tvMessage.setText(msg.getText());
            vh.tvTime.setText(DateTimeUtils.formatTime(msg.getTimestamp()));
        } else if (holder instanceof OtherViewHolder) {
            OtherViewHolder vh = (OtherViewHolder) holder;
            vh.tvSender.setText(msg.getSenderName() != null ? msg.getSenderName() : "Đầu mối");
            vh.tvMessage.setText(msg.getText());
            vh.tvTime.setText(DateTimeUtils.formatTime(msg.getTimestamp()));
        }
    }

    @Override
    public int getItemCount() {
        return messageList.size();
    }

    static class MeViewHolder extends RecyclerView.ViewHolder {
        TextView tvMessage, tvTime;
        public MeViewHolder(@NonNull View itemView) {
            super(itemView);
            tvMessage = itemView.findViewById(R.id.tvMessage);
            tvTime = itemView.findViewById(R.id.tvTime);
        }
    }

    static class OtherViewHolder extends RecyclerView.ViewHolder {
        TextView tvSender, tvMessage, tvTime;
        public OtherViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSender = itemView.findViewById(R.id.tvSender);
            tvMessage = itemView.findViewById(R.id.tvMessage);
            tvTime = itemView.findViewById(R.id.tvTime);
        }
    }
}
