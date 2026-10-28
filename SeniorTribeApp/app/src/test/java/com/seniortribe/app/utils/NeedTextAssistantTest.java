package com.seniortribe.app.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** E05 · Gợi ý viết lại nội dung nhu cầu (chạy trên máy). */
public class NeedTextAssistantTest {

    private static NeedTextAssistant.Suggestion rewrite(String content) {
        NeedTextAssistant.Input in = new NeedTextAssistant.Input();
        in.content = content;
        return NeedTextAssistant.rewrite(in);
    }

    private static boolean has(NeedTextAssistant.Suggestion s, String code) {
        for (NeedTextAssistant.Change c : s.changes) if (c.code.equals(code)) return true;
        return false;
    }

    @Test
    public void figmaExample_removesAddressAndPhone_expandsAndCapitalizes() {
        NeedTextAssistant.Suggestion s = rewrite(
                "cụ ở 1 mình buồn lắm, cần người tới nói chuyện. nhà hẻm 45 Nguyễn Thị Minh Khai, sđt 09xx xxx xxx");
        assertEquals("Cụ ở một mình buồn lắm, cần người tới nói chuyện.", s.text);
        assertTrue(has(s, NeedTextAssistant.CHANGE_ADDRESS_PHONE));
        assertTrue(has(s, NeedTextAssistant.CHANGE_ABBREV));
        assertTrue(s.removedPrivate);
    }

    @Test
    public void removesBarePhoneNumber_soPrivacyWarningGoesAway() {
        NeedTextAssistant.Suggestion s = rewrite("Cần người đưa cụ đi tái khám, gọi 0909 123 456 trước khi đến");
        // Cùng mẫu với PrivacyGuard (không gọi thẳng vì PrivacyGuard dùng android.text.TextUtils)
        assertFalse(java.util.regex.Pattern.compile("(?:\\d[\\s.\\-]?){9,}").matcher(s.text).find());
        assertTrue(has(s, NeedTextAssistant.CHANGE_PHONE));
        assertEquals("Cần người đưa cụ đi tái khám, gọi trước khi đến.", s.text);
    }

    @Test
    public void keepsWardAndDistrict() {
        NeedTextAssistant.Suggestion s = rewrite("Cụ ở Phường 9 Quận 3, cần người đọc báo cùng vào sáng t3");
        assertEquals("Cụ ở Phường 9 Quận 3, cần người đọc báo cùng vào sáng thứ 3.", s.text);
        assertFalse(s.removedPrivate);
    }

    @Test
    public void replacesElderNameKeepingHonorific() {
        NeedTextAssistant.Input in = new NeedTextAssistant.Input();
        in.content = "Cụ Lê Văn Ba sống một mình, mong có người trò chuyện mỗi tuần";
        in.elderlyName = "Ông Lê Văn Ba";
        NeedTextAssistant.Suggestion s = NeedTextAssistant.rewrite(in);
        assertEquals("Cụ sống một mình, mong có người trò chuyện mỗi tuần.", s.text);
        assertTrue(has(s, NeedTextAssistant.CHANGE_NAME));
    }

    @Test
    public void expandsCommonAbbreviationsAndHours() {
        NeedTextAssistant.Suggestion s = rewrite("cụ ko có ng thân ở gần, cần ng tới vs cụ lúc 8h30 t7");
        assertEquals("Cụ không có người thân ở gần, cần người tới với cụ lúc 8 giờ 30 thứ 7.", s.text);
    }

    @Test
    public void removesHouseNumberWithStreet() {
        NeedTextAssistant.Suggestion s = rewrite("Nhà ở 123/4 Lê Văn Sỹ. Cụ cần người dọn nhà sáng chủ nhật hằng tuần");
        assertEquals("Cụ cần người dọn nhà sáng chủ nhật hằng tuần.", s.text);
        assertTrue(has(s, NeedTextAssistant.CHANGE_ADDRESS));
    }

    @Test
    public void removesAddressWithWardTail_andLoneHonorific() {
        NeedTextAssistant.Suggestion s = rewrite("Bà ở số 12 đường Lê Lợi, P.Bến Nghé. bà cần người đi chợ giúp chiều thứ 6");
        assertEquals("Bà cần người đi chợ giúp chiều thứ 6.", s.text);
    }

    @Test
    public void removesPrivateAddressFieldEvenWithoutDiacritics() {
        NeedTextAssistant.Input in = new NeedTextAssistant.Input();
        in.content = "Cụ cần người đi chợ giúp, nhà o 45 nguyen trai, chiều thứ 5";
        in.address = "45 Nguyễn Trãi";
        NeedTextAssistant.Suggestion s = NeedTextAssistant.rewrite(in);
        assertFalse(s.text.toLowerCase().contains("trai"));
        assertTrue(s.removedPrivate);
    }

    @Test
    public void hintsWhenTimeMissingAndTextShort() {
        NeedTextAssistant.Suggestion s = rewrite("cụ cần người trò chuyện");
        assertTrue(has(s, NeedTextAssistant.HINT_TIME));
        assertTrue(has(s, NeedTextAssistant.HINT_DETAIL));
    }

    @Test
    public void desiredTimeFieldSuppressesTimeHint() {
        NeedTextAssistant.Input in = new NeedTextAssistant.Input();
        in.content = "Cụ cần người trò chuyện, đọc báo cùng cụ khoảng hai tiếng.";
        in.desiredTime = "Sáng thứ 3";
        assertFalse(has(NeedTextAssistant.rewrite(in), NeedTextAssistant.HINT_TIME));
    }

    @Test
    public void cleanTextIsUnchanged() {
        String clean = "Cụ ông sống một mình, mong có người đến trò chuyện vào sáng thứ 3 hoặc thứ 5, khoảng hai tiếng mỗi lần.";
        NeedTextAssistant.Suggestion s = rewrite(clean);
        assertEquals(clean, s.text);
        assertFalse(s.differs());
        assertTrue(has(s, NeedTextAssistant.NO_CHANGE));
    }

    @Test
    public void newLinesBecomeSentences() {
        NeedTextAssistant.Suggestion s = rewrite("cần người đưa cụ đi bv\nthứ 4 hằng tuần");
        assertEquals("Cần người đưa cụ đi bệnh viện. Thứ 4 hằng tuần.", s.text);
    }

    @Test
    public void emptyInput() {
        NeedTextAssistant.Suggestion s = rewrite("   ");
        assertEquals("", s.text);
    }
}
