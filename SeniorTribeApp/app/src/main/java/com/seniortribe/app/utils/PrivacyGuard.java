package com.seniortribe.app.utils;

import android.text.TextUtils;

import java.util.regex.Pattern;

/**
 * Kiểm tra nhanh phần tóm tắt có lỡ chứa thông tin nhận diện không (quy tắc R03).
 * Chỉ là lớp nhắc nhở ở máy; điều phối viên vẫn rà soát lại ở F07.
 */
public final class PrivacyGuard {

    // Chuỗi từ 9 chữ số trở lên, cho phép dấu cách/chấm/gạch giữa các số: 0909 123 456
    private static final Pattern PHONE = Pattern.compile("(?:\\d[\\s.\\-]?){9,}");

    private PrivacyGuard() {}

    public static boolean containsPhoneNumber(String text) {
        return !TextUtils.isEmpty(text) && PHONE.matcher(text).find();
    }

    /**
     * Tên người cao tuổi (từ 2 chữ trở lên) xuất hiện trong đoạn văn. Không phân biệt dấu, hoa thường,
     * bỏ qua cách xưng hô ở đầu ("Ông Lê Văn B." vẫn bắt được "lê văn b").
     */
    public static boolean containsName(String text, String fullName) {
        if (TextUtils.isEmpty(text) || TextUtils.isEmpty(fullName)) return false;
        String name = VnText.normalize(fullName).replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
        name = name.replaceFirst("^(ong|ba|cu|chu|co|bac|di|anh|chi) ", "");
        if (name.split(" ").length < 2) return false;
        String haystack = VnText.normalize(text).replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ");
        return (" " + haystack + " ").contains(" " + name + " ");
    }
}
