package com.seniortribe.app.search;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/**
 * E08 · Vài từ khóa tìm gần đây, lưu trên máy để bấm tìm lại nhanh.
 * Không lưu từ khóa trông như số điện thoại (≥ 9 chữ số) để tránh giữ thông tin liên hệ (R03);
 * người dùng xóa được toàn bộ; đăng xuất thì xóa.
 */
public final class SearchHistory {

    private static final String FILE = "search_history";
    private static final String KEY = "recent";
    private static final int MAX = 6;
    private static final String SEP = "\u001F";

    private final SharedPreferences prefs;

    public SearchHistory(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public List<String> recent() {
        List<String> out = new ArrayList<>();
        String raw = prefs.getString(KEY, "");
        if (raw == null || raw.isEmpty()) return out;
        for (String s : raw.split(SEP)) if (!s.isEmpty()) out.add(s);
        return out;
    }

    public void add(String query) {
        String q = query == null ? "" : query.trim().replaceAll("\\s+", " ");
        if (q.length() < 2 || q.length() > 60 || q.replaceAll("\\D", "").length() >= 9) return;
        List<String> list = recent();
        for (int i = list.size() - 1; i >= 0; i--) {
            if (SearchMatcher.fold(list.get(i)).equals(SearchMatcher.fold(q))) list.remove(i);
        }
        list.add(0, q);
        while (list.size() > MAX) list.remove(list.size() - 1);
        StringBuilder sb = new StringBuilder();
        for (String s : list) {
            if (sb.length() > 0) sb.append(SEP);
            sb.append(s);
        }
        prefs.edit().putString(KEY, sb.toString()).apply();
    }

    public void clear() {
        prefs.edit().clear().apply();
    }

    /** Xóa lịch sử tìm kiếm khi tài khoản đăng xuất (gọi một lần trong Application.onCreate). */
    public static void clearOnSignOut(Context context) {
        final SearchHistory history = new SearchHistory(context);
        final String[] last = {null};
        com.google.firebase.auth.FirebaseAuth.getInstance().addAuthStateListener(auth -> {
            String uid = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;
            if (last[0] != null && !last[0].equals(uid)) history.clear();
            last[0] = uid;
        });
    }
}
