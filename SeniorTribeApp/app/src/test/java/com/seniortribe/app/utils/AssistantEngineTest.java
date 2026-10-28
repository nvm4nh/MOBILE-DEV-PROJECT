package com.seniortribe.app.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/** E05 · Trợ lý: nhận ý định câu hỏi và xếp hạng nhu cầu gợi ý. */
public class AssistantEngineTest {

    @Test
    public void howToQuestionsGetGuides() {
        assertTrue(AssistantEngine.answer("Cách trả lại nhu cầu?").text.startsWith("Trả lại nhu cầu"));
        assertTrue(AssistantEngine.answer("Đối soát là gì?").text.startsWith("Đối soát"));
        assertTrue(AssistantEngine.answer("thông tin riêng ai xem được").text.startsWith("Thông tin riêng"));
        assertFalse(AssistantEngine.answer("Cách trả lại nhu cầu?").wantsNeeds);
    }

    @Test
    public void suggestionQuestionsAskForNeeds() {
        assertTrue(AssistantEngine.answer("Tuần này có nhu cầu nào hợp với Đoàn TN Phường 7 không?").wantsNeeds);
        assertTrue(AssistantEngine.answer(AssistantEngine.CHIP_SUGGEST).wantsNeeds);
    }

    @Test
    public void unknownQuestionFallsBack() {
        AssistantEngine.Reply r = AssistantEngine.answer("thời tiết hôm nay");
        assertFalse(r.wantsNeeds);
        assertTrue(r.text.startsWith("Mình chưa hiểu"));
    }

    @Test
    public void ranksByAreaTopicAndTime() {
        List<AssistantEngine.Candidate> open = Arrays.asList(
                new AssistantEngine.Candidate("a", "Đi chợ giúp cụ bà", "Việc nhà", "Phường 2, Quận 10", "Mua đồ ăn", "Sáng thứ 2", 3),
                new AssistantEngine.Candidate("b", "Trò chuyện cuối tuần", "Trò chuyện", "Phường 7, Quận 3", "Cụ thích nhạc xưa", "Chiều thứ 7", 2),
                new AssistantEngine.Candidate("c", "Đọc báo cùng cụ", "Trò chuyện", "Phường 9, Quận 3", "Đọc báo", "Sáng thứ 3", 1));
        List<AssistantEngine.Match> top = AssistantEngine.rank("Có nhu cầu trò chuyện nào ở quận 3 vào cuối tuần không?", open, 3);
        assertEquals("b", top.get(0).candidate.id);
        assertEquals("c", top.get(1).candidate.id);
        assertEquals(2, top.size());
    }

    @Test
    public void noMatchReturnsNewest() {
        List<AssistantEngine.Candidate> open = Arrays.asList(
                new AssistantEngine.Candidate("old", "A", "", "", "", "", 1),
                new AssistantEngine.Candidate("new", "B", "", "", "", "", 9));
        List<AssistantEngine.Match> top = AssistantEngine.rank("gợi ý nhu cầu phù hợp", open, 3);
        assertEquals("new", top.get(0).candidate.id);
        assertEquals(0, top.get(0).score);
    }
}
