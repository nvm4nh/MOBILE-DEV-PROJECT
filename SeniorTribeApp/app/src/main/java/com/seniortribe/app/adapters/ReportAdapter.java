package com.seniortribe.app.adapters;

import android.content.Context;
import android.content.res.ColorStateList;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.core.widget.TextViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.seniortribe.app.R;
import com.seniortribe.app.models.FeedbackReport;
import com.seniortribe.app.utils.DateTimeUtils;

import java.util.ArrayList;
import java.util.List;

/** E03 · Thẻ phản ánh trong danh sách "Cần xử lý" / "Tôi đã gửi". */
public class ReportAdapter extends RecyclerView.Adapter<ReportAdapter.Holder> {

    public interface OnReportClickListener {
        void onReportClick(FeedbackReport report);
    }

    private final Context context;
    private final OnReportClickListener listener;
    private List<FeedbackReport> reports = new ArrayList<>();

    public ReportAdapter(Context context, OnReportClickListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setReports(@Nullable List<FeedbackReport> list) {
        reports = list != null ? new ArrayList<>(list) : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(context).inflate(R.layout.item_report, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        FeedbackReport r = reports.get(position);
        h.tvCode.setText(r.getCaseCode() != null ? r.getCaseCode() : "");
        bindStatus(h.tvStatus, r.getStatus());
        h.tvType.setText(typeLabel(r.getType()));
        h.tvExcerpt.setText(r.getDescription());
        String time = context.getString(R.string.report_item_time, DateTimeUtils.formatRelativeTime(r.getUpdatedAtMillis()));
        h.tvMeta.setText(TextUtils.isEmpty(r.getReportedOrgName())
                ? time
                : context.getString(R.string.report_item_meta, time, r.getReportedOrgName()));
        h.itemView.setOnClickListener(v -> listener.onReportClick(r));
    }

    @Override
    public int getItemCount() {
        return reports.size();
    }

    // =====================================================================
    // Dùng chung với màn hình chi tiết
    // =====================================================================

    @StringRes
    public static int typeLabel(@Nullable String type) {
        if (FeedbackReport.TYPE_LATE.equals(type)) return R.string.report_type_late;
        if (FeedbackReport.TYPE_ATTITUDE.equals(type)) return R.string.report_type_attitude;
        if (FeedbackReport.TYPE_PRIVACY.equals(type)) return R.string.report_type_privacy;
        return R.string.report_type_other;
    }

    /** Mới (vàng như "Chờ rà soát") → Đang xử lý (xanh dương) → Đã đóng (xanh lá). */
    public static void bindStatus(TextView chip, @Nullable String status) {
        int label;
        int fgRes;
        int bgRes;
        if (FeedbackReport.STATUS_CLOSED.equals(status)) {
            label = R.string.report_status_closed;
            fgRes = R.color.status_completed;
            bgRes = R.color.status_completed_bg;
        } else if (FeedbackReport.STATUS_IN_PROGRESS.equals(status)) {
            label = R.string.report_status_in_progress;
            fgRes = R.color.status_accepted;
            bgRes = R.color.status_accepted_bg;
        } else {
            label = R.string.report_status_new;
            fgRes = R.color.status_review;
            bgRes = R.color.status_review_bg;
        }
        int fg = ContextCompat.getColor(chip.getContext(), fgRes);
        int bg = ContextCompat.getColor(chip.getContext(), bgRes);
        chip.setText(label);
        chip.setTextColor(fg);
        chip.setBackgroundTintList(ColorStateList.valueOf(bg));
        TextViewCompat.setCompoundDrawableTintList(chip, ColorStateList.valueOf(fg));
    }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView tvCode, tvStatus, tvType, tvExcerpt, tvMeta;

        Holder(View v) {
            super(v);
            tvCode = v.findViewById(R.id.tvReportCode);
            tvStatus = v.findViewById(R.id.tvReportStatus);
            tvType = v.findViewById(R.id.tvReportType);
            tvExcerpt = v.findViewById(R.id.tvReportExcerpt);
            tvMeta = v.findViewById(R.id.tvReportMeta);
        }
    }
}
