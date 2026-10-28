package com.seniortribe.app.viewmodels;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.firestore.ListenerRegistration;
import com.seniortribe.app.data.MembershipRepository;
import com.seniortribe.app.data.NeedErrors;
import com.seniortribe.app.data.NeedRepository;
import com.seniortribe.app.models.AssistantMessage;
import com.seniortribe.app.models.Membership;
import com.seniortribe.app.models.NeedSummary;
import com.seniortribe.app.utils.AssistantEngine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * E05 · Trợ lý SeniorTribe. Giữ cuộc trò chuyện qua xoay màn hình; theo dõi danh sách nhu cầu đang chờ
 * tiếp nhận (chỉ bản tóm tắt, cùng quyền đọc như F09) để gợi ý khi được hỏi.
 */
public class AssistantViewModel extends ViewModel {

    public static class Ui {
        public List<AssistantMessage> messages = new ArrayList<>();
        public List<String> chips = new ArrayList<>();
        public boolean searching;
    }

    private static final int SUGGEST_LIMIT = 3;

    private final NeedRepository needs = new NeedRepository();
    private final MutableLiveData<Ui> ui = new MutableLiveData<>(new Ui());
    private ListenerRegistration registration;
    private List<NeedSummary> openNeeds;
    private String needsError;
    private String pendingQuestion;
    private Membership membership;
    private boolean started;

    public LiveData<Ui> getUi() { return ui; }

    public void start() {
        if (started) return;
        started = true;
        AssistantEngine.Reply hello = AssistantEngine.welcome();
        Ui s = copy();
        s.messages.add(AssistantMessage.assistant(hello.text));
        s.messages.add(AssistantMessage.caption(AssistantEngine.DISCLAIMER));
        s.chips = new ArrayList<>(hello.chips);
        ui.setValue(s);

        MembershipRepository.get().loadCurrent(false).addOnCompleteListener(t -> {
            if (t.isSuccessful()) membership = t.getResult();
        });
        registration = needs.listenOpenNeeds(new NeedRepository.Callback<List<NeedSummary>>() {
            @Override public void onData(List<NeedSummary> data, boolean fromCache) {
                openNeeds = data;
                needsError = null;
                answerPending();
            }

            @Override public void onError(Exception error) {
                needsError = NeedErrors.message(error);
                answerPending();
            }
        });
    }

    public void ask(String question) {
        String q = question != null ? question.trim() : "";
        if (q.isEmpty()) return;
        Ui s = copy();
        s.messages.add(AssistantMessage.me(q));
        AssistantEngine.Reply reply = AssistantEngine.answer(q);
        s.chips = new ArrayList<>(reply.chips);
        if (reply.wantsNeeds) {
            ui.setValue(s);
            pendingQuestion = q;
            if (openNeeds != null || needsError != null) {
                answerPending();
            } else {
                Ui busy = copy();
                busy.searching = true;
                ui.setValue(busy);
            }
            return;
        }
        s.messages.add(AssistantMessage.assistant(reply.text));
        ui.setValue(s);
    }

    private void answerPending() {
        String q = pendingQuestion;
        if (q == null) return;
        pendingQuestion = null;
        Ui s = copy();
        s.searching = false;
        if (openNeeds == null) {
            s.messages.add(AssistantMessage.assistant("Mình chưa tải được danh sách nhu cầu"
                    + (needsError != null ? " (" + needsError + ")" : "") + ". Bạn thử lại sau nhé."));
            ui.setValue(s);
            return;
        }
        if (openNeeds.isEmpty()) {
            s.messages.add(AssistantMessage.assistant("Hiện chưa có nhu cầu nào đang chờ tiếp nhận. "
                    + "Khi đơn vị ghi nhận đăng nhu cầu mới và được duyệt, nhu cầu sẽ hiện ở tab Nhu cầu → Tìm để tiếp nhận."));
            ui.setValue(s);
            return;
        }
        List<AssistantEngine.Candidate> candidates = new ArrayList<>();
        Map<String, NeedSummary> byId = new HashMap<>();
        for (NeedSummary n : openNeeds) {
            if (n.getNeedId() == null) continue;
            byId.put(n.getNeedId(), n);
            candidates.add(new AssistantEngine.Candidate(n.getNeedId(), n.getTitle(), n.getTopic(), n.getArea(),
                    n.getContent(), n.getDesiredTime(), n.getCreatedAtMillis()));
        }
        List<AssistantEngine.Match> top = AssistantEngine.rank(q, candidates, SUGGEST_LIMIT);
        boolean matched = !top.isEmpty() && top.get(0).score > 0;
        if (matched) {
            s.messages.add(AssistantMessage.assistant("Có " + top.size() + " nhu cầu đang chờ tiếp nhận khớp với câu hỏi"
                    + reasonsOf(top) + ". Gần nhất:"));
        } else {
            s.messages.add(AssistantMessage.assistant("Mình chưa thấy nhu cầu khớp đúng câu hỏi. Đây là "
                    + top.size() + " nhu cầu mới nhất đang chờ tiếp nhận:"));
        }
        for (AssistantEngine.Match m : top) {
            NeedSummary n = byId.get(m.candidate.id);
            if (n != null) s.messages.add(AssistantMessage.need(n, null));
        }
        String caption = "Xếp theo mức khớp với khu vực, chủ đề và thời gian mong muốn. Bạn vẫn tự quyết định tiếp nhận.";
        if (membership == null || !membership.isRepresentative()) caption += " Chỉ đại diện tổ chức mới tiếp nhận được.";
        s.messages.add(AssistantMessage.caption(caption));
        s.chips = new ArrayList<>(Arrays.asList(AssistantEngine.CHIP_RECORD, AssistantEngine.CHIP_SCHEDULE,
                AssistantEngine.CHIP_PRIVACY));
        ui.setValue(s);
    }

    private static String reasonsOf(List<AssistantEngine.Match> top) {
        List<String> reasons = new ArrayList<>();
        for (AssistantEngine.Match m : top) for (String r : m.reasons) if (!reasons.contains(r)) reasons.add(r);
        if (reasons.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(" (");
        for (int i = 0; i < reasons.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(reasons.get(i));
        }
        return sb.append(")").toString();
    }

    private Ui copy() {
        Ui old = ui.getValue();
        Ui s = new Ui();
        if (old != null) {
            s.messages = new ArrayList<>(old.messages);
            s.chips = new ArrayList<>(old.chips);
            s.searching = old.searching;
        }
        return s;
    }

    @Override
    protected void onCleared() {
        if (registration != null) registration.remove();
        registration = null;
    }
}
