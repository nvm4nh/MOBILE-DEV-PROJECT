package com.seniortribe.app.models;

/** E05 · Một dòng trong màn hình Trợ lý: câu hỏi, câu trả lời, thẻ nhu cầu gợi ý hoặc chú thích. */
public class AssistantMessage {

    public static final int TYPE_ME = 0;
    public static final int TYPE_ASSISTANT = 1;
    public static final int TYPE_NEED = 2;
    public static final int TYPE_CAPTION = 3;

    public final int type;
    public final String text;
    public final NeedSummary need;

    private AssistantMessage(int type, String text, NeedSummary need) {
        this.type = type;
        this.text = text;
        this.need = need;
    }

    public static AssistantMessage me(String text) { return new AssistantMessage(TYPE_ME, text, null); }
    public static AssistantMessage assistant(String text) { return new AssistantMessage(TYPE_ASSISTANT, text, null); }
    public static AssistantMessage need(NeedSummary need, String reason) { return new AssistantMessage(TYPE_NEED, reason, need); }
    public static AssistantMessage caption(String text) { return new AssistantMessage(TYPE_CAPTION, text, null); }
}
