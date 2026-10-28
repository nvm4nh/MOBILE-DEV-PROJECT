package com.seniortribe.app.adapters;

import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.BackgroundColorSpan;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.seniortribe.app.R;
import com.seniortribe.app.search.SearchMatcher;
import com.seniortribe.app.search.SearchResult;
import com.seniortribe.app.utils.NeedStatus;
import com.seniortribe.app.viewmodels.SearchViewModel;

import java.util.ArrayList;
import java.util.List;

/**
 * E08 · Danh sách kết quả tìm kiếm theo nhóm, tô đậm chữ khớp (kể cả khi gõ không dấu).
 */
public class SearchAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface Listener {
        void onResultClick(SearchResult result);
        void onSeeAll(int filter);
    }

    private static final int TYPE_HEADER = 1;
    private static final int TYPE_RESULT = 2;

    private static final class Row {
        final int type;
        String header;
        int filter;
        boolean more;
        SearchResult result;

        Row(int type) { this.type = type; }
    }

    private final Context context;
    private final Listener listener;
    private List<Row> rows = new ArrayList<>();
    private SearchMatcher matcher = new SearchMatcher("");

    public SearchAdapter(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void show(SearchViewModel.Ui s) {
        matcher = new SearchMatcher(s.query);
        List<Row> out = new ArrayList<>();
        boolean all = s.filter == SearchViewModel.FILTER_ALL;
        if (all || s.filter == SearchViewModel.FILTER_NEEDS) {
            group(out, R.string.search_group_needs, SearchViewModel.FILTER_NEEDS, s.needs, all);
        }
        if (all || s.filter == SearchViewModel.FILTER_SCHEDULES) {
            group(out, R.string.search_group_schedules, SearchViewModel.FILTER_SCHEDULES, s.schedules, all);
        }
        if (all || s.filter == SearchViewModel.FILTER_POSTS) {
            group(out, R.string.search_group_posts, SearchViewModel.FILTER_POSTS, s.posts, all);
        }
        rows = out;
        notifyDataSetChanged();
    }

    private void group(List<Row> out, int title, int filter, List<SearchResult> items, boolean preview) {
        if (items.isEmpty()) return;
        Row h = new Row(TYPE_HEADER);
        h.header = context.getString(R.string.search_group_count, context.getString(title), items.size());
        h.filter = filter;
        h.more = preview && items.size() > SearchViewModel.PREVIEW_PER_GROUP;
        out.add(h);
        int n = preview ? Math.min(items.size(), SearchViewModel.PREVIEW_PER_GROUP) : items.size();
        for (int i = 0; i < n; i++) {
            Row r = new Row(TYPE_RESULT);
            r.result = items.get(i);
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
        LayoutInflater inf = LayoutInflater.from(context);
        if (viewType == TYPE_HEADER) {
            return new HeaderHolder(inf.inflate(R.layout.item_search_header, parent, false));
        }
        return new ResultHolder(inf.inflate(R.layout.item_search_result, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Row row = rows.get(position);
        if (holder instanceof HeaderHolder) {
            HeaderHolder h = (HeaderHolder) holder;
            h.title.setText(row.header);
            h.more.setVisibility(row.more ? View.VISIBLE : View.GONE);
            h.more.setOnClickListener(v -> listener.onSeeAll(row.filter));
            return;
        }
        ResultHolder h = (ResultHolder) holder;
        SearchResult r = row.result;
        h.icon.setImageResource(r.kind == SearchResult.Kind.POST ? R.drawable.ic_feed
                : r.kind == SearchResult.Kind.SCHEDULE ? R.drawable.ic_schedule : R.drawable.ic_needs);
        h.title.setText(highlight(r.title));
        h.meta.setText(highlight(r.meta));
        h.meta.setVisibility(TextUtils.isEmpty(r.meta) ? View.GONE : View.VISIBLE);
        h.snippet.setText(highlight(r.snippet));
        h.snippet.setVisibility(TextUtils.isEmpty(r.snippet) ? View.GONE : View.VISIBLE);
        if (r.status != null) {
            h.status.setVisibility(View.VISIBLE);
            NeedStatus.bindChip(h.status, r.status);
        } else {
            h.status.setVisibility(View.GONE);
        }
        h.itemView.setContentDescription(context.getString(kindLabel(r.kind)) + ". " + r.title
                + (TextUtils.isEmpty(r.meta) ? "" : ". " + r.meta));
        h.itemView.setOnClickListener(v -> listener.onResultClick(r));
    }

    private int kindLabel(SearchResult.Kind kind) {
        switch (kind) {
            case POST: return R.string.search_kind_post_one;
            case SCHEDULE: return R.string.search_kind_schedule_one;
            default: return R.string.search_kind_need_one;
        }
    }

    /** Tô đậm và tô nền nhạt các chữ khớp từ khóa. */
    private CharSequence highlight(String text) {
        if (TextUtils.isEmpty(text)) return text;
        SpannableString s = new SpannableString(text);
        int bg = ContextCompat.getColor(context, R.color.primary_light);
        for (SearchMatcher.Range r : matcher.highlights(text)) {
            s.setSpan(new StyleSpan(Typeface.BOLD), r.start, r.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            s.setSpan(new BackgroundColorSpan(bg), r.start, r.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return s;
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    static class HeaderHolder extends RecyclerView.ViewHolder {
        final TextView title;
        final View more;

        HeaderHolder(View v) {
            super(v);
            title = v.findViewById(R.id.tvHeader);
            more = v.findViewById(R.id.btnMore);
        }
    }

    static class ResultHolder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView title, meta, snippet, status;

        ResultHolder(View v) {
            super(v);
            icon = v.findViewById(R.id.ivKind);
            title = v.findViewById(R.id.tvTitle);
            meta = v.findViewById(R.id.tvMeta);
            snippet = v.findViewById(R.id.tvSnippet);
            status = v.findViewById(R.id.tvStatus);
        }
    }
}
