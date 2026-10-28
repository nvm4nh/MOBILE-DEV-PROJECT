package com.seniortribe.app.utils;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * E05 · Gợi ý viết lại phần "Nội dung nhu cầu" ngay trên máy (theo quy tắc, không gửi nội dung ra ngoài).
 *
 *  - Bỏ số điện thoại, địa chỉ nhà và tên cụ khỏi nội dung chung (quy tắc R03: thông tin riêng nhập ở mục riêng).
 *  - Viết đầy đủ chữ viết tắt thường gặp khi gõ nhanh ("ko" → "không", "1 mình" → "một mình", "8h" → "8 giờ").
 *  - Viết hoa đầu câu, thêm dấu chấm, gọn khoảng trắng.
 *  - Nhắc bổ sung thời gian mong muốn / mô tả rõ hơn nếu còn thiếu.
 *
 * Không phụ thuộc Android nên kiểm thử được bằng JUnit thường. Trợ lý chỉ gợi ý: người nhập đọc lại
 * và tự quyết định dùng hay bỏ qua; điều phối viên vẫn rà soát ở F07.
 */
public final class NeedTextAssistant {

    public static final int MAX_LENGTH = 1000;

    /** Loại thay đổi, để màn hình chọn biểu tượng: dấu tích xanh, mắt gạch vàng, gợi ý thêm. */
    public static final int KIND_STYLE = 0;
    public static final int KIND_PRIVACY = 1;
    public static final int KIND_HINT = 2;

    /** Mã thay đổi; màn hình đổi sang câu chữ trong strings_assist.xml. */
    public static final String CHANGE_ABBREV = "abbrev";
    public static final String CHANGE_STYLE = "style";
    public static final String CHANGE_PHONE = "phone";
    public static final String CHANGE_ADDRESS = "address";
    public static final String CHANGE_ADDRESS_PHONE = "address_phone";
    public static final String CHANGE_NAME = "name";
    public static final String HINT_TIME = "hint_time";
    public static final String HINT_DETAIL = "hint_detail";
    public static final String NO_CHANGE = "no_change";

    public static final class Change {
        public final String code;
        public final int kind;
        /** Ví dụ cho CHANGE_ABBREV: "“ko” → “không”, “1 mình” → “một mình”". */
        public final String detail;

        Change(String code, int kind, String detail) {
            this.code = code;
            this.kind = kind;
            this.detail = detail;
        }
    }

    /** Thông tin để nhận ra dữ liệu riêng lỡ ghi vào nội dung chung. Các ô có thể rỗng. */
    public static final class Input {
        public String content;
        public String elderlyName;
        public String address;
        public String contact;
        public String desiredTime;
    }

    public static final class Suggestion {
        public final String original;
        public final String text;
        public final List<Change> changes;
        public final boolean removedPrivate;

        Suggestion(String original, String text, List<Change> changes, boolean removedPrivate) {
            this.original = original;
            this.text = text;
            this.changes = changes;
            this.removedPrivate = removedPrivate;
        }

        /** Gợi ý có khác nội dung đang nhập không (so sau khi bỏ khoảng trắng hai đầu). */
        public boolean differs() {
            return !text.equals(original == null ? "" : original.trim());
        }
    }

    private NeedTextAssistant() {}

    // =====================================================================
    // Mẫu nhận diện
    // =====================================================================

    private static final String NOT_WORD_BEFORE = "(?<![\\p{L}\\d])";
    private static final String NOT_WORD_AFTER = "(?![\\p{L}\\d])";

    /** "sđt 0909 123 456", "đt: 09xx xxx xxx", "zalo 0909.123.456" (cả số đã che bằng x). */
    private static final Pattern PHONE_LABELED = Pattern.compile(
            "(?:[,;]\\s*)?" + NOT_WORD_BEFORE
                    + "(?iu:s[đd]t|[đd]t|số\\s*điện\\s*thoại|so\\s*dien\\s*thoai|điện\\s*thoại|dien\\s*thoai|zalo|phone|hotline|liên\\s*hệ|lien\\s*he)"
                    + "\\s*[:：.]?\\s*(?:\\+?84[\\s.\\-]?)?[\\dxX*]{2,}(?:[\\s.\\-]+[\\dxX*]{2,})*" + NOT_WORD_AFTER);

    /** Dãy từ 9 chữ số trở lên (khớp với PrivacyGuard để không còn cảnh báo sau khi dùng gợi ý). */
    private static final Pattern PHONE_BARE = Pattern.compile(
            "(?:[,;]\\s*)?(?<!\\d)(?:\\+?84[\\s.\\-]?)?(?:\\d[\\s.\\-]?){8,}\\d(?!\\d)");

    /** "địa chỉ: 12 Nguyễn Trãi, phường 5" — bỏ tới hết câu. */
    private static final Pattern ADDRESS_LABELED = Pattern.compile(
            "(?:[,;]\\s*)?" + NOT_WORD_BEFORE + "(?iu:địa\\s*chỉ|dia\\s*chi)\\s*(?:nhà\\s*)?[:：]?[^.\\n;!?]*");

    /** Tên đường viết hoa: "Nguyễn Thị Minh Khai", "Lê Lợi", "Cách Mạng Tháng 8". */
    private static final String NOT_PLACE_OR_TIME = "(?!(?iu:phường|phuong|quận|quan|huyện|huyen|xã|xa|tỉnh|tinh|thành|tp|thứ|chủ|giờ|tuổi|tháng|ngày|tuần|lần|người|buổi|tiếng)(?![\\p{L}\\d]))";
    private static final String STREET = NOT_PLACE_OR_TIME + "\\p{Lu}\\p{Ll}*(?:\\s+(?:" + NOT_PLACE_OR_TIME
            + "\\p{Lu}\\p{Ll}*|\\d{1,2}(?![\\p{L}\\d])))*";

    /** Phần phường / quận viết liền sau địa chỉ: ", P.Bến Nghé", ", quận 1". */
    private static final String WARD_TAIL = "(?:\\s*,\\s*(?iu:p\\.|q\\.|f\\.|phường|phuong|quận|quan|huyện|huyen|tp\\.?|thành\\s*phố)\\s*[^,.;!?\\n]*)*";

    /** "nhà hẻm 45 Nguyễn Thị Minh Khai", "ngõ 15/2 Trần Duy Hưng", "số 12 đường Lê Lợi". */
    private static final Pattern ADDRESS_ALLEY = Pattern.compile(
            "(?:[,;]\\s*)?" + NOT_WORD_BEFORE + "(?:(?iu:nhà|ở|tại)\\s+)?(?:(?iu:ở|tại)\\s+)?"
                    + "(?iu:hẻm|hem|ngõ|ngo|ngách|ngach|kiệt|kiet|số\\s*nhà|so\\s*nha|số|so)\\s*\\d+[\\p{L}]?(?:/\\d+[\\p{L}]?)*"
                    + "(?:\\s+(?iu:đường|duong|phố|pho))?(?:\\s+" + STREET + ")?" + WARD_TAIL);

    /** "45/3 Lê Văn Sỹ", "12 đường Nguyễn Trãi" — số nhà phải đi kèm tên đường viết hoa. */
    private static final Pattern ADDRESS_NUMBER_STREET = Pattern.compile(
            "(?:[,;]\\s*)?" + NOT_WORD_BEFORE + "(?:(?iu:nhà|ở|tại)\\s+)?(?:(?iu:ở|tại)\\s+)?"
                    + "\\d+[\\p{L}]?(?:/\\d+[\\p{L}]?)*\\s+(?:(?iu:đường|duong|phố|pho)\\s+)?" + STREET + WARD_TAIL);

    /** Chữ viết tắt thường gặp; chỉ thay khi đứng riêng thành một từ. */
    private static final Map<String, String> ABBREVIATIONS = new LinkedHashMap<>();

    static {
        ABBREVIATIONS.put("ko", "không");
        ABBREVIATIONS.put("k", "không");
        ABBREVIATIONS.put("kh", "không");
        ABBREVIATIONS.put("khg", "không");
        ABBREVIATIONS.put("hok", "không");
        ABBREVIATIONS.put("dc", "được");
        ABBREVIATIONS.put("đc", "được");
        ABBREVIATIONS.put("đươc", "được");
        ABBREVIATIONS.put("vs", "với");
        ABBREVIATIONS.put("ng", "người");
        ABBREVIATIONS.put("mn", "mọi người");
        ABBREVIATIONS.put("j", "gì");
        ABBREVIATIONS.put("cx", "cũng");
        ABBREVIATIONS.put("cũg", "cũng");
        ABBREVIATIONS.put("đag", "đang");
        ABBREVIATIONS.put("trc", "trước");
        ABBREVIATIONS.put("tg", "thời gian");
        ABBREVIATIONS.put("bv", "bệnh viện");
        ABBREVIATIONS.put("cn", "chủ nhật");
        ABBREVIATIONS.put("sn", "sinh nhật");
    }

    private static final Pattern WORD = Pattern.compile(NOT_WORD_BEFORE + "([\\p{L}]+)" + NOT_WORD_AFTER);
    private static final Pattern WEEKDAY_SHORT = Pattern.compile(NOT_WORD_BEFORE + "([tT])([2-7])" + NOT_WORD_AFTER);
    private static final Pattern HOUR_SHORT = Pattern.compile("(?<![\\p{L}\\d])(\\d{1,2})\\s?[hg](\\d{2})?(?![\\p{L}\\d])");
    private static final Pattern ONE_ALONE = Pattern.compile(NOT_WORD_BEFORE + "1\\s+mình" + NOT_WORD_AFTER, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /** Chữ chỉ thời gian; thiếu hết thì nhắc ghi thời gian mong muốn. */
    private static final Pattern TIME_WORDS = Pattern.compile(
            "(?iu)(thứ|chủ nhật|cuối tuần|sáng|trưa|chiều|tối|giờ|tuần|tháng|ngày|hằng|hàng|mỗi|buổi|lúc|\\d{1,2}\\s*:\\s*\\d{2})");

    private static final String[] HONORIFICS = {"cụ", "ông", "bà", "bác", "chú", "cô", "dì"};

    // =====================================================================
    // Gợi ý
    // =====================================================================

    public static Suggestion rewrite(Input in) {
        String original = in != null && in.content != null ? in.content : "";
        String t = Normalizer.normalize(original, Normalizer.Form.NFC);
        List<Change> changes = new ArrayList<>();

        // 1. Thông tin riêng
        boolean phone = false;
        boolean address = false;
        boolean name = false;
        if (in != null && !isBlank(in.address)) {
            String removed = removeFolded(t, in.address.trim());
            if (!removed.equals(t)) {
                address = true;
                t = removed;
            }
        }
        if (in != null && !isBlank(in.contact)) {
            String removed = removeFolded(t, in.contact.trim());
            if (!removed.equals(t)) {
                phone = true;
                t = removed;
            }
        }
        String before = t;
        t = PHONE_LABELED.matcher(t).replaceAll("");
        t = PHONE_BARE.matcher(t).replaceAll("");
        if (!t.equals(before)) phone = true;

        before = t;
        t = ADDRESS_LABELED.matcher(t).replaceAll("");
        t = ADDRESS_ALLEY.matcher(t).replaceAll("");
        t = ADDRESS_NUMBER_STREET.matcher(t).replaceAll("");
        if (!t.equals(before)) address = true;

        if (in != null && !isBlank(in.elderlyName)) {
            String replaced = replaceName(t, in.elderlyName);
            if (!replaced.equals(t)) {
                name = true;
                t = replaced;
            }
        }

        // 2. Chữ viết tắt
        List<String> examples = new ArrayList<>();
        t = expandAbbreviations(t, examples);

        // 3. Dấu câu, viết hoa
        String punctuated = tidy(t);
        String beforeStyle = tidy(t, false);
        t = punctuated;
        if (t.length() > MAX_LENGTH) t = t.substring(0, MAX_LENGTH).trim();

        if (!examples.isEmpty()) {
            changes.add(new Change(CHANGE_ABBREV, KIND_STYLE, join(examples, 2)));
        }
        if (!punctuated.equals(beforeStyle) && !punctuated.isEmpty()) {
            changes.add(new Change(CHANGE_STYLE, KIND_STYLE, null));
        }
        if (address && phone) changes.add(new Change(CHANGE_ADDRESS_PHONE, KIND_PRIVACY, null));
        else if (address) changes.add(new Change(CHANGE_ADDRESS, KIND_PRIVACY, null));
        else if (phone) changes.add(new Change(CHANGE_PHONE, KIND_PRIVACY, null));
        if (name) changes.add(new Change(CHANGE_NAME, KIND_PRIVACY, null));

        // 4. Nhắc bổ sung
        boolean hasTime = TIME_WORDS.matcher(t).find() || (in != null && !isBlank(in.desiredTime));
        if (!t.isEmpty() && !hasTime) changes.add(new Change(HINT_TIME, KIND_HINT, null));
        if (!t.isEmpty() && t.length() < 40) changes.add(new Change(HINT_DETAIL, KIND_HINT, null));
        if (changes.isEmpty()) changes.add(new Change(NO_CHANGE, KIND_STYLE, null));

        return new Suggestion(original, t, changes, phone || address || name);
    }

    // =====================================================================
    // Hàm hỗ trợ
    // =====================================================================

    private static String expandAbbreviations(String text, List<String> examples) {
        String t = text;
        Matcher one = ONE_ALONE.matcher(t);
        if (one.find()) {
            addExample(examples, one.group(), "một mình");
            t = ONE_ALONE.matcher(t).replaceAll("một mình");
        }

        StringBuffer sb = new StringBuffer();
        Matcher m = WEEKDAY_SHORT.matcher(t);
        while (m.find()) {
            String rep = (Character.isUpperCase(m.group(1).charAt(0)) ? "Thứ " : "thứ ") + m.group(2);
            addExample(examples, m.group(), rep);
            m.appendReplacement(sb, Matcher.quoteReplacement(rep));
        }
        m.appendTail(sb);
        t = sb.toString();

        sb = new StringBuffer();
        m = HOUR_SHORT.matcher(t);
        while (m.find()) {
            String rep = m.group(1) + " giờ" + (m.group(2) != null && !"00".equals(m.group(2)) ? " " + m.group(2) : "");
            addExample(examples, m.group(), rep);
            m.appendReplacement(sb, Matcher.quoteReplacement(rep));
        }
        m.appendTail(sb);
        t = sb.toString();

        sb = new StringBuffer();
        m = WORD.matcher(t);
        while (m.find()) {
            String word = m.group(1);
            String full = ABBREVIATIONS.get(word.toLowerCase(Locale.forLanguageTag("vi")));
            if (full == null) {
                m.appendReplacement(sb, Matcher.quoteReplacement(word));
                continue;
            }
            String rep = Character.isUpperCase(word.charAt(0)) ? capitalize(full) : full;
            addExample(examples, word, rep);
            m.appendReplacement(sb, Matcher.quoteReplacement(rep));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static void addExample(List<String> examples, String from, String to) {
        String e = "“" + from.trim() + "” → “" + to + "”";
        if (!examples.contains(e)) examples.add(e);
    }

    /** Gọn khoảng trắng, bỏ dấu phẩy thừa (sau khi cắt thông tin riêng), viết hoa đầu câu, thêm dấu chấm. */
    static String tidy(String text) {
        return tidy(text, true);
    }

    private static String tidy(String text, boolean sentenceCase) {
        String t = text.replace('\r', '\n');
        t = t.replaceAll("[ \\t\\u00A0]+", " ");
        t = t.replaceAll(" *\\n *", "\n");
        t = t.replaceAll("\\s+([,.;:!?…])", "$1");
        t = t.replaceAll("([,;:])(?:\\s*[,;:])+", "$1");
        t = t.replaceAll("[,;:]+\\s*([.!?…])", "$1");
        t = t.replaceAll("([.!?…])(?:\\s*[,;:])+", "$1");
        t = t.replaceAll("\\.{2,}(?!\\.)", ".");
        t = t.replaceAll("([,;:.!?])(?=[\\p{L}\\d])", "$1 ");
        t = t.replaceAll("\\(\\s*\\)", "");

        List<String> sentences = new ArrayList<>();
        for (String line : t.split("\n")) {
            for (String part : line.split("(?<=[.!?…])\\s+")) {
                String l = part.trim().replaceAll("^[,;:.\\-–—\\s]+", "").replaceAll("[,;:\\-–—\\s]+$", "").trim();
                if (l.isEmpty()) continue;
                if (!sentenceCase) {
                    sentences.add(l);
                    continue;
                }
                if (isOnlyHonorific(l)) continue;
                int first = 0;
                while (first < l.length() && !Character.isLetter(l.charAt(first))) first++;
                String sentence = first < l.length()
                        ? l.substring(0, first) + Character.toUpperCase(l.charAt(first)) + l.substring(first + 1) : l;
                char last = sentence.charAt(sentence.length() - 1);
                if (last != '.' && last != '!' && last != '?' && last != '…' && last != ')') sentence = sentence + ".";
                sentences.add(sentence);
            }
        }
        return join(sentences, " ");
    }

    /** Câu chỉ còn một từ xưng hô ("Bà.") sau khi cắt địa chỉ thì bỏ. */
    private static boolean isOnlyHonorific(String sentence) {
        String w = fold(sentence).replaceAll("[^a-z]", "");
        for (String h : HONORIFICS) if (w.equals(fold(h))) return true;
        return false;
    }

    /** Thay tên cụ (không phân biệt dấu, hoa thường) bằng "cụ"; giữ cách xưng hô đứng trước nếu có. */
    private static String replaceName(String text, String fullName) {
        String name = fullName.trim();
        String folded = fold(name);
        for (String h : HONORIFICS) {
            if (folded.startsWith(fold(h) + " ")) {
                name = name.substring(h.length()).trim();
                folded = fold(name);
                break;
            }
        }
        if (folded.split("\\s+").length < 2) return text;
        String hay = fold(text);
        StringBuilder out = new StringBuilder();
        int from = 0;
        int idx;
        while ((idx = indexOfWord(hay, folded, from)) >= 0) {
            String head = text.substring(from, idx);
            String trimmedHead = head.replaceAll("\\s+$", "");
            boolean hasHonorific = false;
            String foldedHead = fold(trimmedHead);
            for (String h : HONORIFICS) {
                String fh = fold(h);
                if (foldedHead.equals(fh) || foldedHead.endsWith(" " + fh)) {
                    hasHonorific = true;
                    break;
                }
            }
            out.append(hasHonorific ? trimmedHead : head + "cụ");
            from = idx + folded.length();
        }
        out.append(text.substring(from));
        return out.toString();
    }

    /** Bỏ một chuỗi (so không dấu, không phân biệt hoa thường) khỏi đoạn văn. */
    private static String removeFolded(String text, String needle) {
        String n = fold(needle);
        if (n.trim().length() < 4) return text;
        String hay = fold(text);
        StringBuilder out = new StringBuilder();
        int from = 0;
        int idx;
        while ((idx = hay.indexOf(n, from)) >= 0) {
            out.append(text, from, idx);
            from = idx + n.length();
        }
        out.append(text.substring(from));
        return out.toString();
    }

    private static int indexOfWord(String hay, String needle, int from) {
        int idx = hay.indexOf(needle, from);
        while (idx >= 0) {
            boolean okBefore = idx == 0 || !Character.isLetterOrDigit(hay.charAt(idx - 1));
            int end = idx + needle.length();
            boolean okAfter = end >= hay.length() || !Character.isLetterOrDigit(hay.charAt(end));
            if (okBefore && okAfter) return idx;
            idx = hay.indexOf(needle, idx + 1);
        }
        return -1;
    }

    /** Bỏ dấu và hạ chữ thường từng ký tự, giữ nguyên độ dài để vị trí khớp với chuỗi gốc. */
    static String fold(String s) {
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == 'đ' || c == 'Đ') {
                out.append('d');
                continue;
            }
            String base = Normalizer.normalize(String.valueOf(c), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
            char b = base.isEmpty() ? c : base.charAt(0);
            out.append(Character.toLowerCase(b));
        }
        return out.toString();
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String join(List<String> parts, int max) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size() && i < max; i++) {
            if (i > 0) sb.append(", ");
            sb.append(parts.get(i));
        }
        return sb.toString();
    }

    private static String join(List<String> parts, String sep) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) sb.append(sep);
            sb.append(parts.get(i));
        }
        return sb.toString();
    }
}
