package com.seniortribe.app.adapters;

import android.content.Context;
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
import java.util.List;

/**
 * Thẻ nhu cầu cho danh sách (F08, F09). Chỉ hiển thị phần tóm tắt:
 * mã trường hợp, trạng thái, tiêu đề, khu vực mức phường và thời gian mong muốn.
 */
public class NeedAdapter extends RecyclerView.Adapter<NeedAdapter.NeedViewHolder> {

    public interface OnNeedClickListener {
        void onNeedClick(NeedSummary need);
    }

    private final Context context;
    private final OnNeedClickListener listener;
    private List<NeedSummary> needList = new ArrayList<>();

    public NeedAdapter(Context context, OnNeedClickListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setNeeds(List<NeedSummary> needs) {
        this.needList = needs != null ? new ArrayList<>(needs) : new ArrayList<>();
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

        holder.tvCaseCode.setText(need.getCaseCode() != null ? need.getCaseCode() : "");
        holder.tvTitle.setText(need.getTitle());
        NeedStatus.bindChip(holder.tvStatus, need.getStatus());

        holder.tvArea.setText(TextUtils.isEmpty(need.getArea()) ? context.getString(R.string.need_area_unknown) : need.getArea());

        if (!TextUtils.isEmpty(need.getDesiredTime())) {
            holder.tvTime.setText(need.getDesiredTime());
        } else if (need.getCreatedAtMillis() > 0) {
            holder.tvTime.setText(DateTimeUtils.formatRelativeTime(need.getCreatedAtMillis()));
        } else {
            holder.tvTime.setText(R.string.need_time_unknown);
        }

        bindExtra(holder.tvExtra, need);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onNeedClick(need);
        });
    }

    /** Dòng phụ: cần bổ sung, đơn vị đang phụ trách hoặc lý do hủy. */
    private void bindExtra(TextView tv, NeedSummary need) {
        String status = need.getStatus();
        String text = null;
        int color = R.color.text_secondary;

        if (Constants.STATUS_REVIEW.equals(status) && need.isNeedsRevision()) {
            text = context.getString(R.string.need_card_needs_revision);
            color = R.color.warning_text;
        } else if (!TextUtils.isEmpty(need.getAssignedOrgName())
                && (Constants.STATUS_ACCEPTED.equals(status) || Constants.STATUS_SCHEDULED.equals(status)
                || Constants.STATUS_DELIVERED.equals(status) || Constants.STATUS_COMPLETED.equals(status))) {
            text = context.getString(R.string.need_card_assignee, safe(need.getAssigneeName()), need.getAssignedOrgName());
        } else if (Constants.STATUS_CANCELLED.equals(status) && !TextUtils.isEmpty(need.getCancelReason())) {
            text = context.getString(R.string.need_card_cancel_reason, need.getCancelReason());
        }

        if (text == null) {
            tv.setVisibility(View.GONE);
        } else {
            tv.setVisibility(View.VISIBLE);
            tv.setText(text);
            tv.setTextColor(ContextCompat.getColor(context, color));
        }
    }

    private static String safe(String s) {
        return s != null ? s : "";
    }

    @Override
    public int getItemCount() {
        return needList.size();
    }

    static class NeedViewHolder extends RecyclerView.ViewHolder {
        final TextView tvCaseCode, tvStatus, tvTitle, tvArea, tvTime, tvExtra;

        NeedViewHolder(@NonNull View itemView) {
            super(itemView);
            tvCaseCode = itemView.findViewById(R.id.tvCaseCode);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvArea = itemView.findViewById(R.id.tvArea);
            tvTime = itemView.findViewById(R.id.tvTime);
            tvExtra = itemView.findViewById(R.id.tvExtra);
        }
    }
}
