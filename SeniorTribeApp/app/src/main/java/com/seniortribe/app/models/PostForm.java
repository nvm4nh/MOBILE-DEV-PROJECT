package com.seniortribe.app.models;

import androidx.annotation.Nullable;

/** Nội dung người dùng nhập ở màn Đăng / sửa bài (F05). */
public class PostForm {
    public final String title;
    public final String content;
    @Nullable public final String eventTime;
    @Nullable public final String eventPlace;

    public PostForm(String title, String content, @Nullable String eventTime, @Nullable String eventPlace) {
        this.title = title;
        this.content = content;
        this.eventTime = blankToNull(eventTime);
        this.eventPlace = blankToNull(eventPlace);
    }

    @Nullable
    private static String blankToNull(@Nullable String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
