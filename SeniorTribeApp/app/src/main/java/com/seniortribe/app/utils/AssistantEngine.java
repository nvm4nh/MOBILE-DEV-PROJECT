package com.seniortribe.app.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * E05 · Trợ lý SeniorTribe chạy ngay trên máy: trả lời câu hỏi thao tác theo bộ câu trả lời soạn sẵn
 * và nhận ra khi người dùng muốn được gợi ý nhu cầu phù hợp (khi đó màn hình tự tìm trong các nhu cầu
 * đang chờ tiếp nhận, chỉ dùng bản tóm tắt).
 *
 * Trợ lý không duyệt hoàn cảnh, không xác nhận sự đồng ý, không cấp quyền: mọi quyết định vẫn do người dùng.
 * Không phụ thuộc Android nên kiểm thử được bằng JUnit thường.
 */
public final class AssistantEngine {

    public static final class Reply {
        public final String text;
        public final List<String> chips;
        /** Người dùng muốn gợi ý nhu cầu: màn hình tìm trong danh sách đang chờ tiếp nhận. */
        public final boolean wantsNeeds;

        Reply(String text, List<String> chips, boolean wantsNeeds) {
            this.text = text;
            this.chips = chips;
            this.wantsNeeds = wantsNeeds;
        }
    }

    /** Một nhu cầu đang chờ tiếp nhận, chỉ gồm phần tóm tắt. */
    public static final class Candidate {
        public final String id;
        public final String title;
        public final String topic;
        public final String area;
        public final String content;
        public final String desiredTime;
        public final long createdAt;

        public Candidate(String id, String title, String topic, String area, String content,
                         String desiredTime, long createdAt) {
            this.id = id;
            this.title = title;
            this.topic = topic;
            this.area = area;
            this.content = content;
            this.desiredTime = desiredTime;
            this.createdAt = createdAt;
        }
    }

    public static final class Match {
        public final Candidate candidate;
        public final int score;
        public final List<String> reasons;

        Match(Candidate candidate, int score, List<String> reasons) {
            this.candidate = candidate;
            this.score = score;
            this.reasons = reasons;
        }
    }

    public static final String CHIP_RETURN = "Cách trả lại nhu cầu";
    public static final String CHIP_RECONCILE = "Đối soát là gì?";
    public static final String CHIP_SUGGEST = "Gợi ý nhu cầu phù hợp";
    public static final String CHIP_PRIVACY = "Thông tin riêng ai xem được?";
    public static final String CHIP_RECORD = "Cách ghi nhận nhu cầu";
    public static final String CHIP_SCHEDULE = "Chốt lịch với cụ";
    public static final String CHIP_FEEDBACK = "Gửi phản ánh";
    public static final String CHIP_EVENTS = "Sự kiện cộng đồng";

    public static final String DISCLAIMER = "Trợ lý chạy ngay trên máy, chỉ dùng bản tóm tắt nhu cầu; "
            + "không xem thông tin riêng của cụ và không thay bạn quyết định.";

    private AssistantEngine() {}

    public static Reply welcome() {
        return new Reply("Chào bạn! Mình hướng dẫn thao tác và gợi ý nhu cầu phù hợp. "
                + "Mình không xem thông tin riêng của cụ và không thay bạn quyết định.",
                Arrays.asList(CHIP_SUGGEST, CHIP_RETURN, CHIP_RECONCILE, CHIP_PRIVACY), false);
    }

    // =====================================================================
    // Trả lời
    // =====================================================================

    public static Reply answer(String question) {
        String q = " " + VnText.normalize(question).replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim() + " ";
        if (q.trim().isEmpty()) return fallback();

        boolean howTo = any(q, "cach", "lam sao", "the nao", "huong dan", "o dau", "la gi", "nghia la", "bang cach", "duoc khong");

        if (!howTo && wantsNeeds(q)) {
            return new Reply("Để mình tìm trong các nhu cầu đang chờ tiếp nhận…",
                    Arrays.asList(CHIP_SUGGEST, CHIP_PRIVACY), true);
        }
        if (any(q, "xin chao", " chao ", "hello", " hi ", "alo")) {
            return new Reply("Chào bạn! Bạn cần hướng dẫn thao tác nào, hay muốn mình gợi ý nhu cầu phù hợp với tổ chức?",
                    Arrays.asList(CHIP_SUGGEST, CHIP_RECORD, CHIP_RETURN), false);
        }
        if (any(q, "cam on", "thank", "tks")) {
            return new Reply("Không có gì ạ! Cần gì bạn cứ hỏi nhé.", Arrays.asList(CHIP_SUGGEST, CHIP_RECONCILE), false);
        }
        if (any(q, "tra lai", "tra nhu cau", "khong lam duoc nua", "khong nhan nua")) {
            return new Reply("Trả lại nhu cầu: mở chi tiết nhu cầu tổ chức bạn đang phụ trách → menu ⋮ → Trả lại, "
                    + "chọn lý do (có thể ghi thêm). Chỉ đại diện tổ chức trả lại được, khi nhu cầu đang “Đã tiếp nhận” "
                    + "hoặc “Đã chốt lịch”. Lịch đã chốt sẽ bị xóa và nhu cầu quay về danh sách chờ để tổ chức khác nhận.",
                    Arrays.asList(CHIP_SCHEDULE, CHIP_RECONCILE), false);
        }
        if (any(q, "doi soat", "hoan thanh", "bao ho tro", "bao cao ho tro", "xac nhan ket qua")) {
            return new Reply("Đối soát là bước đầu mối ghi nhận kiểm tra báo cáo hỗ trợ của tổ chức (đã đến chưa, "
                    + "làm những gì, cụ thấy thế nào) rồi bấm Hoàn thành, hoặc ghi chú điều cần làm rõ. "
                    + "Mở chat của nhu cầu → Tiến độ để xem và đối soát.",
                    Arrays.asList(CHIP_FEEDBACK, CHIP_RETURN), false);
        }
        if (any(q, "lich su", "nhat ky")) {
            return new Reply("Lịch sử hỗ trợ: mở chi tiết nhu cầu → menu ⋮ → Lịch sử hỗ trợ để xem các bước ghi nhận, "
                    + "rà soát, tiếp nhận, trả lại, lịch hẹn và kết quả. Hai bên của nhu cầu xem được.",
                    Arrays.asList(CHIP_RECONCILE, CHIP_FEEDBACK), false);
        }
        if (any(q, "lich", " hen ", "hen gio", "chot", "dong y cua cu", "su dong y")) {
            return new Reply("Thống nhất lịch: tổ chức phụ trách đề xuất ngày, khung giờ và hình thức trong chat. "
                    + "Đầu mối hỏi ý kiến cụ (hoặc người nhà), đánh dấu “Cụ đồng ý”, ghi cách hỏi rồi bấm Chốt lịch. "
                    + "Chưa có đồng ý của cụ thì không chốt được.",
                    Arrays.asList(CHIP_RECONCILE, CHIP_RETURN), false);
        }
        if (any(q, "thong tin rieng", "rieng tu", "bao mat", "so dien thoai", "dia chi", "ten cu", "ai xem")) {
            return new Reply("Thông tin riêng của cụ (tên, liên hệ, địa chỉ) lưu tách khỏi phần tóm tắt. "
                    + "Chỉ đơn vị ghi nhận và người được giao phụ trách xem được. Đừng ghi những thông tin này vào "
                    + "nội dung chung, bảng tin hay chat nhóm. Mình cũng không đọc thông tin riêng.",
                    Arrays.asList(CHIP_RECORD, CHIP_SUGGEST), false);
        }
        if (any(q, "phan anh", "khieu nai", "danh gia", "cham diem")) {
            return new Reply("Sau khi nhu cầu hoàn thành, đầu mối hỏi ý kiến cụ rồi đánh giá tổ chức (1–5 sao). "
                    + "Nếu có vấn đề như không đến đúng hẹn, thái độ chưa phù hợp hay lộ thông tin riêng, hai bên gửi "
                    + "phản ánh ở chi tiết nhu cầu → menu ⋮ → Gửi phản ánh; điều phối viên của đơn vị ghi nhận sẽ xử lý.",
                    Arrays.asList(CHIP_RECONCILE, CHIP_EVENTS), false);
        }
        if (any(q, "su kien", "ngay hoi", "hoat dong chung", "dang ky tham gia")) {
            return new Reply("Sự kiện cộng đồng ở tab Bảng tin → nút Sự kiện. Đại diện tổ chức tạo sự kiện hoặc đăng ký "
                    + "số thành viên tham gia; nếu sự kiện cho phép, có thể hủy trước giờ bắt đầu ít nhất 24 giờ.",
                    Arrays.asList(CHIP_SUGGEST, CHIP_FEEDBACK), false);
        }
        if (any(q, " huy ")) {
            return new Reply("Hủy nhu cầu: chỉ đầu mối của đơn vị ghi nhận, khi nhu cầu chưa hoàn thành. "
                    + "Mở chi tiết nhu cầu → menu ⋮ → Hủy nhu cầu, chọn lý do. Nhu cầu đã hủy không mở lại được.",
                    Arrays.asList(CHIP_RETURN, CHIP_RECORD), false);
        }
        if (any(q, "ra soat", "duyet", "bo sung")) {
            return new Reply("Rà soát: điều phối viên của đơn vị (không phải người đã ghi nhận) đọc phần tóm tắt, "
                    + "kiểm tra không lộ thông tin riêng rồi Duyệt hoặc Yêu cầu bổ sung. Mình không duyệt thay.",
                    Arrays.asList(CHIP_RECORD, CHIP_PRIVACY), false);
        }
        if (any(q, "ghi nhan", "tao nhu cau", "nhap nhu cau", "them nhu cau", "dang nhu cau", "giong noi", "noi de nhap", "mic")) {
            return new Reply("Ghi nhận: đầu mối bấm “Ghi nhận nhu cầu” ở tab Nhu cầu. Phần Tóm tắt sẽ hiện cho mạng lưới "
                    + "sau khi duyệt; thông tin riêng nhập ở mục riêng. Có thể bấm “Nói để nhập nội dung” hoặc "
                    + "“Nhờ trợ lý viết lại” ngay dưới ô Nội dung.",
                    Arrays.asList(CHIP_PRIVACY, CHIP_SCHEDULE), false);
        }
        if (any(q, "tiep nhan", "nhan ho tro", "nhan nhu cau")) {
            return new Reply("Tiếp nhận: đại diện tổ chức mở tab Nhu cầu → Tìm để tiếp nhận, chọn nhu cầu → "
                    + "Tiếp nhận nhu cầu và chọn thành viên phụ trách. Sau đó hai bên trao đổi trong chat để chốt lịch.",
                    Arrays.asList(CHIP_SUGGEST, CHIP_SCHEDULE), false);
        }
        if (any(q, "dang bai", "bang tin", "bai viet")) {
            return new Reply("Bảng tin: đại diện tổ chức bấm “Đăng bài” ở tab Bảng tin để chia sẻ hoạt động. "
                    + "Có thể sửa hoặc ẩn bài của tổ chức mình. Không đăng ảnh hay thông tin riêng của các cụ.",
                    Arrays.asList(CHIP_EVENTS, CHIP_PRIVACY), false);
        }
        if (any(q, "chat", "nhan tin", "tin nhan")) {
            return new Reply("Chat phối hợp chỉ giữa đầu mối đã ghi nhận và thành viên được giao phụ trách. "
                    + "Mở từ chi tiết nhu cầu → Mở chat. Khi nhu cầu kết thúc, chat chỉ còn đọc.",
                    Arrays.asList(CHIP_SCHEDULE, CHIP_RECONCILE), false);
        }
        if (any(q, "offline", "mat mang", "khong co mang", "mat ket noi", "wifi")) {
            return new Reply("Khi mất mạng, ứng dụng vẫn hiện dữ liệu đã tải gần nhất và báo “Đang offline”. "
                    + "Các thao tác gửi đi (ghi nhận, tiếp nhận, chốt lịch…) cần có mạng.",
                    Arrays.asList(CHIP_SUGGEST, CHIP_RECORD), false);
        }
        if (wantsNeeds(q)) {
            return new Reply("Để mình tìm trong các nhu cầu đang chờ tiếp nhận…",
                    Arrays.asList(CHIP_SUGGEST, CHIP_PRIVACY), true);
        }
        return fallback();
    }

    private static Reply fallback() {
        return new Reply("Mình chưa hiểu câu hỏi này. Bạn có thể hỏi về ghi nhận, tiếp nhận, chốt lịch, trả lại, "
                + "đối soát, phản ánh, sự kiện, hoặc nhờ mình gợi ý nhu cầu phù hợp.",
                Arrays.asList(CHIP_SUGGEST, CHIP_RECORD, CHIP_RETURN, CHIP_RECONCILE), false);
    }

    private static boolean wantsNeeds(String q) {
        boolean need = any(q, "nhu cau", " viec ", "ho tro nao", "giup gi", "can giup");
        boolean ask = any(q, " nao ", "goi y", "phu hop", " hop ", " tim ", " co ", "khong", " dang cho", "tuan nay",
                "cuoi tuan", "gan ", "quan ", "phuong ", "huyen ", " xa ", " o ");
        return need && ask;
    }

    // =====================================================================
    // Gợi ý nhu cầu phù hợp
    // =====================================================================

    private static final Set<String> STOPWORDS = new HashSet<>(Arrays.asList(
            "co", "nhu", "cau", "nao", "khong", "cho", "nay", "hop", "phu", "voi", "cua", "doan", "toi", "minh",
            "ban", "la", "va", "o", "nhung", "gi", "can", "ho", "tro", "tim", "goi", "y", "giup", "dang", "duoc",
            "the", "thi", "nhe", "a", "oi", "ve", "cac", "mot", "to", "chuc", "chung", "em", "anh", "chi",
            "viec", "nhan", "tiep", "ma", "di", "den", "tren", "trong", "hay", "hoac", "neu", "lam", "muon", "xem"));

    /** Ghép thời gian người dùng nói với cách ghi thời gian mong muốn trên nhu cầu. */
    private static final String[][] TIME_SYNONYMS = {
            {"cuoi tuan", "thu 7", "chu nhat", "cuoi tuan"},
            {"sang", "sang"},
            {"chieu", "chieu"},
            {"toi", "toi"},
            {"ngay thuong", "thu 2", "thu 3", "thu 4", "thu 5", "thu 6"},
    };

    /**
     * Xếp hạng nhu cầu theo mức khớp câu hỏi: khu vực (+4), chủ đề (+3), thời gian (+2), từ trong tiêu đề /
     * nội dung (+1). Không có nhu cầu nào khớp thì trả về các nhu cầu mới nhất (score = 0).
     */
    public static List<Match> rank(String question, List<Candidate> open, int limit) {
        String q = " " + VnText.normalize(question).replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim() + " ";
        List<String> tokens = new ArrayList<>();
        for (String w : q.trim().split(" ")) if (w.length() >= 2 && !STOPWORDS.contains(w)) tokens.add(w);

        List<Match> out = new ArrayList<>();
        for (Candidate c : open) {
            int score = 0;
            List<String> reasons = new ArrayList<>();
            String area = " " + clean(c.area) + " ";
            String topic = " " + clean(c.topic) + " ";
            String text = " " + clean(c.title) + " " + clean(c.content) + " ";
            String time = " " + clean(c.desiredTime) + " " + clean(c.content) + " ";

            // Khu vực: "quan 3", "phuong 7", tên phường / quận
            for (String place : places(q)) {
                if (area.contains(" " + place + " ")) {
                    score += 4;
                    reasons.add("khu vực");
                    break;
                }
            }
            for (String t : tokens) {
                if (topic.contains(" " + t + " ")) {
                    score += 3;
                    if (!reasons.contains("chủ đề")) reasons.add("chủ đề");
                } else if (text.contains(" " + t + " ")) {
                    score += 1;
                    if (!reasons.contains("nội dung")) reasons.add("nội dung");
                }
            }
            for (String[] syn : TIME_SYNONYMS) {
                if (!q.contains(" " + syn[0] + " ")) continue;
                for (int i = 1; i < syn.length; i++) {
                    if (time.contains(" " + syn[i] + " ")) {
                        score += 2;
                        if (!reasons.contains("thời gian")) reasons.add("thời gian");
                        break;
                    }
                }
            }
            out.add(new Match(c, score, reasons));
        }
        Collections.sort(out, (a, b) -> a.score != b.score
                ? Integer.compare(b.score, a.score)
                : Long.compare(b.candidate.createdAt, a.candidate.createdAt));
        boolean anyMatch = !out.isEmpty() && out.get(0).score > 0;
        List<Match> top = new ArrayList<>();
        for (Match m : out) {
            if (top.size() >= limit) break;
            if (anyMatch && m.score == 0) break;
            top.add(m);
        }
        return top;
    }

    /** "quan 3", "phuong 7", "q3", "p7" trong câu hỏi. */
    private static List<String> places(String q) {
        List<String> out = new ArrayList<>();
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile(" (quan|phuong|huyen|xa|q|p) ?(\\d{1,2}|[a-z]+(?: [a-z]+)?) ").matcher(q);
        int from = 0;
        while (m.find(from)) {
            String kind = m.group(1);
            String full = kind.equals("q") ? "quan" : kind.equals("p") ? "phuong" : kind;
            out.add(full + " " + m.group(2));
            if (m.group(2).contains(" ")) out.add(full + " " + m.group(2).split(" ")[0]);
            from = m.end() - 1;
        }
        return out;
    }

    private static String clean(String s) {
        return VnText.normalize(s == null ? "" : s).replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
    }

    private static boolean any(String haystack, String... needles) {
        for (String n : needles) if (haystack.contains(n)) return true;
        return false;
    }
}
