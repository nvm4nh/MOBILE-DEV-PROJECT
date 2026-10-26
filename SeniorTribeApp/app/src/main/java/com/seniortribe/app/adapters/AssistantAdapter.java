package com.seniortribe.app.adapters;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.seniortribe.app.R;
import com.seniortribe.app.models.AssistantMessage;
import com.seniortribe.app.models.NeedSummary;
import com.seniortribe.app.utils.NeedStatus;

import java.util.ArrayList;
import java.util.List;

/** E05 · Cuộc trò chuyện với trợ lý: bong bóng hai phía, thẻ nhu cầu gợi ý (dùng lại item_need), chú thích. */
public class AssistantAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface OnNeedClickListener {
        void onNeedClick(NeedSummary need);
    }

    private final Context context;
    private final OnNeedClickListener listener;
    private List<AssistantMessage> items = new ArrayList<>();

    public AssistantAdapter(Context context, OnNeedClickListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setItems(List<AssistantMessage> list) {
        items = list != null ? new ArrayList<>(list) : new ArrayList<>();
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).type;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(context);
        int layout;
        switch (viewType) {
            case AssistantMessage.TYPE_ME: layout = R.layout.item_my_bubble; break;
            case AssistantMessage.TYPE_NEED: layout = R.layout.item_need; break;
            case AssistantMessage.TYPE_CAPTION: layout = R.layout.item_assistant_caption; break;
            default: layout = R.layout.item_assistant_bubble; break;
        }
        View v = inflater.inflate(layout, parent, false);
        return viewType == AssistantMessage.TYPE_NEED ? new NeedHolder(v) : new TextHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        AssistantMessage m = items.get(position);
        if (holder instanceof NeedHolder) {
            NeedHolder h = (NeedHolder) holder;
            NeedSummary n = m.need;
            h.tvCaseCode.setText(n.getCaseCode() != null ? n.getCaseCode() : "");
            NeedStatus.bindChip(h.tvStatus, n.getStatus());
            h.tvTitle.setText(n.getTitle());
            h.tvArea.setText(n.getArea());
            h.tvTime.setText(n.getDesiredTime());
            h.tvTime.setVisibility(TextUtils.isEmpty(n.getDesiredTime()) ? View.GONE : View.VISIBLE);
            h.tvExtra.setVisibility(TextUtils.isEmpty(n.getTopic()) ? View.GONE : View.VISIBLE);
            h.tvExtra.setText(n.getTopic());
            h.itemView.setOnClickListener(v -> listener.onNeedClick(n));
        } else {
            ((TextHolder) holder).tv.setText(m.text);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class TextHolder extends RecyclerView.ViewHolder {
        final TextView tv;

        TextHolder(View v) {
            super(v);
            TextView bubble = v.findViewById(R.id.tvBubble);
            tv = bubble != null ? bubble : (TextView) v.findViewById(R.id.tvCaption);
        }
    }

    static class NeedHolder extends RecyclerView.ViewHolder {
        final TextView tvCaseCode, tvStatus, tvTitle, tvArea, tvTime, tvExtra;

        NeedHolder(View v) {
            super(v);
            tvCaseCode = v.findViewById(R.id.tvCaseCode);
            tvStatus = v.findViewById(R.id.tvStatus);
            tvTitle = v.findViewById(R.id.tvTitle);
            tvArea = v.findViewById(R.id.tvArea);
            tvTime = v.findViewById(R.id.tvTime);
            tvExtra = v.findViewById(R.id.tvExtra);
        }
    }
}
