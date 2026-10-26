package com.seniortribe.app.search;

import androidx.annotation.Nullable;

import com.seniortribe.app.models.NeedSummary;
import com.seniortribe.app.models.Post;

/** E08 · Một kết quả tìm kiếm (nhu cầu, bài đăng hoặc lịch hẹn). */
public final class SearchResult {

    public enum Kind { NEED, POST, SCHEDULE }

    public final Kind kind;
    public final String key;
    public final String title;
    public final String meta;       // dòng phụ: mã hồ sơ · khu vực, tổ chức · ngày đăng, giờ hẹn
    public final String snippet;    // đoạn trích quanh chỗ khớp
    public final int score;
    public final long time;         // để xếp các kết quả cùng điểm, mới trước
    @Nullable public final String status;
    @Nullable public final NeedSummary need;
    @Nullable public final Post post;

    public SearchResult(Kind kind, String key, String title, String meta, String snippet, int score, long time,
                        @Nullable String status, @Nullable NeedSummary need, @Nullable Post post) {
        this.kind = kind;
        this.key = key;
        this.title = title;
        this.meta = meta;
        this.snippet = snippet;
        this.score = score;
        this.time = time;
        this.status = status;
        this.need = need;
        this.post = post;
    }
}
