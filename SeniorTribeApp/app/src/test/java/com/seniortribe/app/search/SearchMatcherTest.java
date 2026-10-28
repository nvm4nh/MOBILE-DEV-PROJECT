package com.seniortribe.app.search;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

/** E08 · Tìm kiếm không dấu, nhiều từ, tô đậm đúng vị trí. */
public class SearchMatcherTest {

    @Test
    public void boDauGiuNguyenDoDai() {
        String s = "Đưa cụ đi khám mắt ở Bệnh viện Mắt";
        assertEquals(s.length(), SearchMatcher.fold(s).length());
        assertEquals("dua cu di kham mat o benh vien mat", SearchMatcher.fold(s));
    }

    @Test
    public void timKhongDauVaNhieuTu() {
        SearchMatcher m = new SearchMatcher("kham  MAT");
        assertTrue(m.matchesAll("Đưa cụ đi khám mắt"));
        assertTrue(m.matchesAll("Khám bệnh định kỳ", "Bệnh viện Mắt"));   // mỗi từ ở một trường
        assertFalse(m.matchesAll("Khám bệnh định kỳ"));                  // thiếu "mắt"
    }

    @Test
    public void tuKhoaRongKhongKhopGiCa() {
        assertTrue(new SearchMatcher("   ").isEmpty());
        assertFalse(new SearchMatcher("").matchesAll("bất kỳ"));
    }

    @Test
    public void toDamDungChoCoDau() {
        SearchMatcher m = new SearchMatcher("mat");
        String text = "Khám mắt, Bệnh viện Mắt";
        List<SearchMatcher.Range> r = m.highlights(text);
        assertEquals(2, r.size());
        assertEquals("mắt", text.substring(r.get(0).start, r.get(0).end));
        assertEquals("Mắt", text.substring(r.get(1).start, r.get(1).end));
    }

    @Test
    public void gopDoanChongNhau() {
        SearchMatcher m = new SearchMatcher("kham kha");
        List<SearchMatcher.Range> r = m.highlights("khám");
        assertEquals(1, r.size());
        assertEquals(0, r.get(0).start);
        assertEquals(4, r.get(0).end);
    }

    @Test
    public void tieuDeDuocUuTien() {
        SearchMatcher m = new SearchMatcher("tro chuyen");
        int title = m.score("Trò chuyện cuối tuần", "");
        int body = m.score("Hỗ trợ sinh hoạt", "Cụ thích trò chuyện");
        assertTrue(title > body);
    }

    @Test
    public void doanTrichQuanhChoKhop() {
        SearchMatcher m = new SearchMatcher("cai luong");
        String text = "Cụ bà sống một mình ở phường 7, con cháu ở xa. Cụ thích nghe cải lương và kể chuyện ngày xưa, "
                + "mong có người đến trò chuyện cuối tuần.";
        String sn = m.snippet(text, 60);
        assertTrue(sn.length() <= 62);
        assertTrue(SearchMatcher.fold(sn).contains("cai luong"));
    }
}
