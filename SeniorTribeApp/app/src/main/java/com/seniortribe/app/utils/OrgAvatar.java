package com.seniortribe.app.utils;

import android.widget.TextView;

import androidx.annotation.Nullable;

import com.seniortribe.app.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Avatar chữ viết tắt và tích xác minh của tổ chức trên Bảng tin (Figma F04, F05). */
public final class OrgAvatar {

    // Từ chỉ loại tổ chức, bỏ qua khi lấy chữ viết tắt: "Hội Chữ thập đỏ" → "CT"
    private static final String[] PREFIXES = {"hội", "câu lạc bộ", "clb", "tổ", "nhóm", "quỹ"};

    private OrgAvatar() {}

    /** "Đoàn Thanh niên Phường 7" → "ĐT", "Hội Chữ thập đỏ Quận 3" → "CT". */
    public static String initials(@Nullable String orgName) {
        if (orgName == null || orgName.trim().isEmpty()) return "?";
        String name = orgName.trim();
        String lower = name.toLowerCase(new Locale("vi"));
        for (String prefix : PREFIXES) {
            if (lower.startsWith(prefix + " ") && name.length() > prefix.length() + 1) {
                name = name.substring(prefix.length() + 1).trim();
                break;
            }
        }
        List<String> letters = new ArrayList<>();
        for (String word : name.split("\\s+")) {
            if (word.isEmpty() || !Character.isLetter(word.charAt(0))) continue;
            letters.add(word.substring(0, 1).toUpperCase(new Locale("vi")));
            if (letters.size() == 2) break;
        }
        if (letters.isEmpty()) return name.substring(0, 1).toUpperCase(new Locale("vi"));
        StringBuilder sb = new StringBuilder();
        for (String l : letters) sb.append(l);
        return sb.toString();
    }

    /** Tên tổ chức kèm tích xanh phía sau nếu tổ chức đã được xác minh. */
    public static void bindName(TextView view, @Nullable String orgName, boolean verified) {
        view.setText(orgName != null ? orgName : "");
        view.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, verified ? R.drawable.ic_verified_small : 0, 0);
        view.setContentDescription(verified && orgName != null
                ? view.getContext().getString(R.string.post_org_verified_cd, orgName) : orgName);
    }
}
