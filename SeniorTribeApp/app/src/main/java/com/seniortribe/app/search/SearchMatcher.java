package com.seniortribe.app.search;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * E08 · So khớp từ khóa tiếng Việt không phân biệt dấu, hoa / thường, cho nhiều từ.
 *
 * Khác VnText.normalize (bỏ dấu cả chuỗi), lớp này bỏ dấu TỪNG KÝ TỰ nên vị trí trong chuỗi đã
 * chuẩn hóa trùng với chuỗi gốc: tìm "kham mat" vẫn tô đậm đúng chữ "khám mắt" trong kết quả.
 * Java thuần, có unit test.
 */
public final class SearchMatcher {

    /** Một đoạn khớp [start, end) trong chuỗi gốc. */
    public static final class Range {
        public final int start, end;

        Range(int start, int end) {
            this.start = start;
            this.end = end;
        }
    }

    private final List<String> terms;

    public SearchMatcher(String query) {
        terms = new ArrayList<>();
        String folded = fold(query == null ? "" : query);
        for (String t : folded.split("\\s+")) {
            if (!t.isEmpty() && !terms.contains(t)) terms.add(t);
        }
    }

    public boolean isEmpty() {
        return terms.isEmpty();
    }

    public List<String> terms() {
        return Collections.unmodifiableList(terms);
    }

    /** Bỏ dấu, đổi đ → d, viết thường; giữ nguyên độ dài chuỗi. */
    public static String fold(String s) {
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) out.append(foldChar(s.charAt(i)));
        return out.toString();
    }

    static char foldChar(char c) {
        if (c == 'đ' || c == 'Đ') return 'd';
        if (c < 128) return Character.toLowerCase(c);
        String base = Normalizer.normalize(String.valueOf(c), Normalizer.Form.NFD);
        char first = base.isEmpty() ? c : base.charAt(0);
        return String.valueOf(first).toLowerCase(Locale.ROOT).charAt(0);
    }

    /**
     * Mọi từ khóa đều xuất hiện ở ít nhất một trong các trường.
     */
    public boolean matchesAll(String... fields) {
        if (terms.isEmpty()) return false;
        List<String> folded = new ArrayList<>();
        for (String f : fields) folded.add(f == null ? "" : fold(f));
        for (String t : terms) {
            boolean found = false;
            for (String f : folded) {
                if (f.contains(t)) {
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return true;
    }

    /**
     * Điểm xếp hạng: khớp ở tiêu đề cao hơn nội dung; khớp đầu từ cao hơn giữa từ; cả cụm khớp
     * liền nhau được cộng thêm.
     */
    public int score(String title, String... others) {
        String ft = fold(title == null ? "" : title);
        int score = 0;
        for (String t : terms) {
            int i = ft.indexOf(t);
            if (i >= 0) score += (i == 0 || ft.charAt(i - 1) == ' ') ? 30 : 20;
            for (String o : others) {
                if (o != null && fold(o).contains(t)) {
                    score += 5;
                    break;
                }
            }
        }
        if (terms.size() > 1) {
            StringBuilder phrase = new StringBuilder();
            for (String t : terms) {
                if (phrase.length() > 0) phrase.append(' ');
                phrase.append(t);
            }
            if (ft.contains(phrase.toString())) score += 25;
        }
        return score;
    }

    /** Các đoạn cần tô đậm trong chuỗi gốc (đã gộp đoạn chồng nhau, theo thứ tự). */
    public List<Range> highlights(String text) {
        List<Range> out = new ArrayList<>();
        if (text == null || terms.isEmpty()) return out;
        String f = fold(text);
        List<int[]> raw = new ArrayList<>();
        for (String t : terms) {
            int from = 0;
            while (true) {
                int i = f.indexOf(t, from);
                if (i < 0) break;
                raw.add(new int[]{i, i + t.length()});
                from = i + t.length();
            }
        }
        Collections.sort(raw, (a, b) -> a[0] != b[0] ? Integer.compare(a[0], b[0]) : Integer.compare(a[1], b[1]));
        int s = -1, e = -1;
        for (int[] r : raw) {
            if (s < 0) {
                s = r[0];
                e = r[1];
            } else if (r[0] <= e) {
                e = Math.max(e, r[1]);
            } else {
                out.add(new Range(s, e));
                s = r[0];
                e = r[1];
            }
        }
        if (s >= 0) out.add(new Range(s, e));
        return out;
    }

    /**
     * Đoạn trích quanh chỗ khớp đầu tiên (để hiện dưới tiêu đề), tối đa khoảng maxLen ký tự.
     */
    public String snippet(String text, int maxLen) {
        if (text == null) return "";
        String clean = text.replaceAll("\\s+", " ").trim();
        if (clean.length() <= maxLen) return clean;
        List<Range> hl = highlights(clean);
        int center = hl.isEmpty() ? 0 : hl.get(0).start;
        int start = Math.max(0, center - maxLen / 3);
        int end = Math.min(clean.length(), start + maxLen);
        start = Math.max(0, end - maxLen);
        String cut = clean.substring(start, end);
        return (start > 0 ? "…" : "") + cut + (end < clean.length() ? "…" : "");
    }
}
