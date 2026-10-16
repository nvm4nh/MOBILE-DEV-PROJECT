package com.seniortribe.app.adapters;

import android.content.Context;
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

import java.util.ArrayList;
import java.util.List;

public class NeedAdapter extends RecyclerView.Adapter<NeedAdapter.NeedViewHolder> {

    public interface OnNeedClickListener {
        void onNeedClick(NeedSummary need);
    }

    private final Context context;
    private List<NeedSummary> needList = new ArrayList<>();
    private final OnNeedClickListener listener;

    public NeedAdapter(Context context, OnNeedClickListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setNeeds(List<NeedSummary> needs) {
        this.needList = needs != null ? needs : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public NeedViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_need, parent, false);
        return new NeedViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NeedViewHolder holder, int position) {
        NeedSummary need = needList.get(position);
        holder.tvCaseCode.setText(need.getCaseCode() != null ? need.getCaseCode() : "#ST-NEED");
        holder.tvTitle.setText(need.getTitle());
        holder.tvCategory.setText(need.getCategory() != null ? need.getCategory() : "Hỗ trợ");
        holder.tvDistrict.setText(need.getDistrict() != null ? need.getDistrict() : "TP.HCM");
        holder.tvTime.setText(DateTimeUtils.formatRelativeTime(need.getCreatedAt()));

        // Trạng thái theo workflow chuẩn
        bindStatus(holder.tvStatus, need.getStatus());

        // Mức độ cần thiết
        bindUrgency(holder.tvUrgency, need.getUrgency());

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onNeedClick(need);
        });
    }

    private void bindStatus(TextView tv, String status) {
        if (status == null) status = Constants.STATUS_OPEN;
        int colorRes;
        String text;
        switch (status) {
            case Constants.STATUS_REVIEW:
                colorRes = R.color.status_review;
                text = "Chờ rà soát";
                break;
            case Constants.STATUS_ACCEPTED:
                colorRes = R.color.status_accepted;
                text = "Đã tiếp nhận";
                break;
            case Constants.STATUS_SCHEDULED:
                colorRes = R.color.status_scheduled;
                text = "Đã hẹn lịch";
                break;
            case Constants.STATUS_DELIVERED:
                colorRes = R.color.status_delivered;
                text = "Chờ đối soát";
                break;
            case Constants.STATUS_COMPLETED:
                colorRes = R.color.status_completed;
                text = "Hoàn thành";
                break;
            case Constants.STATUS_CANCELLED:
                colorRes = R.color.status_cancelled;
                text = "Đã hủy";
                break;
            case Constants.STATUS_OPEN:
            default:
                colorRes = R.color.status_open;
                text = "Chờ tiếp nhận";
                break;
        }
        tv.setText(text);
        tv.setTextColor(ContextCompat.getColor(context, colorRes));
    }

    private void bindUrgency(TextView tv, String urgency) {
        if (urgency == null) urgency = Constants.URGENCY_LOW;
        int colorRes = R.color.urgency_low;
        if (Constants.URGENCY_HIGH.equalsIgnoreCase(urgency)) {
            colorRes = R.color.urgency_high;
        } else if (Constants.URGENCY_MEDIUM.equalsIgnoreCase(urgency)) {
            colorRes = R.color.urgency_medium;
        }
        tv.setText(urgency);
        tv.setTextColor(ContextCompat.getColor(context, colorRes));
    }

    @Override
    public int getItemCount() {
        return needList.size();
    }

    static class NeedViewHolder extends RecyclerView.ViewHolder {
        TextView tvCaseCode, tvStatus, tvTitle, tvCategory, tvDistrict, tvUrgency, tvTime;

        public NeedViewHolder(@NonNull View itemView) {
            super(itemView);
            tvCaseCode = itemView.findViewById(R.id.tvCaseCode);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvCategory = itemView.findViewById(R.id.tvCategory);
            tvDistrict = itemView.findViewById(R.id.tvDistrict);
            tvUrgency = itemView.findViewById(R.id.tvUrgency);
            tvTime = itemView.findViewById(R.id.tvTime);
        }
    }
}
