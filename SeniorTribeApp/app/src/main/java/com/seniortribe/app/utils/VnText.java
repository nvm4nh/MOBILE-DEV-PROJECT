package com.seniortribe.app.utils;

import java.text.Normalizer;
import java.util.Locale;

/** Chuẩn hóa chuỗi tiếng Việt để tìm kiếm không phân biệt dấu và hoa thường. */
public final class VnText {

    private VnText() {}

    public static String normalize(String s) {
        if (s == null) return "";
        String noAccent = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return noAccent.replace('đ', 'd').replace('Đ', 'D')
                .toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
}
