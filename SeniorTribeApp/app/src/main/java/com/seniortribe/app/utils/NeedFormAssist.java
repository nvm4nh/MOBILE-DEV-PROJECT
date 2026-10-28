package com.seniortribe.app.utils;

import android.content.res.ColorStateList;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.snackbar.Snackbar;
import com.seniortribe.app.R;

/**
 * E04, E05 · Phần trợ giúp ngay dưới ô "Nội dung nhu cầu" của F06:
 *  - "Nói để nhập nội dung": chèn chữ từ giọng nói, có "Nói lại" và "Hoàn tác".
 *  - "Nhờ trợ lý viết lại": gợi ý câu chữ rõ ràng, bỏ thông tin riêng lỡ ghi vào nội dung chung.
 * Người nhập luôn đọc lại và tự quyết định; trợ lý không duyệt hoàn cảnh hay sự đồng ý.
 *
 * Tạo trong onCreate của CreateNeedActivity sau khi đã tìm các ô nhập.
 */
public final class NeedFormAssist {

    /** Giữ trạng thái qua xoay màn hình. */
    public static class State extends ViewModel {
        @Nullable String beforeVoice;
        @Nullable String afterVoice;
        boolean voiceInserted;
        @Nullable NeedTextAssistant.Suggestion suggestion;
    }

    private static final String VOICE_KEY = "need_form_voice";

    private final AppCompatActivity activity;
    private final View anchor;
    private final EditText etContent, etElderlyName, etAddress, etContact, etDesiredTime;
    private final TextView tvVoiceInserted, tvSuggestion, tvDisclaimer;
    private final Chip chipVoice, chipUndo, chipRewrite;
    private final MaterialCardView cardSuggestion;
    private final LinearLayout layoutChanges;
    private final MaterialButton btnUse, btnSkip;
    private final State state;
    private final VoiceInput voice;

    public NeedFormAssist(AppCompatActivity activity, View snackbarAnchor, EditText etContent,
                          @Nullable EditText etElderlyName, @Nullable EditText etAddress,
                          @Nullable EditText etContact, @Nullable EditText etDesiredTime) {
        this.activity = activity;
        this.anchor = snackbarAnchor;
        this.etContent = etContent;
        this.etElderlyName = etElderlyName;
        this.etAddress = etAddress;
        this.etContact = etContact;
        this.etDesiredTime = etDesiredTime;
        tvVoiceInserted = activity.findViewById(R.id.tvVoiceInserted);
        chipVoice = activity.findViewById(R.id.chipVoiceInput);
        chipUndo = activity.findViewById(R.id.chipUndoVoice);
        chipRewrite = activity.findViewById(R.id.chipAiRewrite);
        cardSuggestion = activity.findViewById(R.id.cardAiSuggestion);
        tvSuggestion = activity.findViewById(R.id.tvAiSuggestion);
        layoutChanges = activity.findViewById(R.id.layoutAiChanges);
        btnUse = activity.findViewById(R.id.btnAiUse);
        btnSkip = activity.findViewById(R.id.btnAiSkip);
        tvDisclaimer = activity.findViewById(R.id.tvAiDisclaimer);
        state = new ViewModelProvider(activity).get(State.class);

        voice = new VoiceInput(activity, VOICE_KEY, NeedTextAssistant.MAX_LENGTH, snackbarAnchor, this::insertVoice);
        chipVoice.setOnClickListener(v -> voice.start());
        chipUndo.setOnClickListener(v -> undoVoice());
        chipRewrite.setOnClickListener(v -> suggest());
        btnUse.setOnClickListener(v -> useSuggestion());
        btnSkip.setOnClickListener(v -> {
            state.suggestion = null;
            renderSuggestion();
        });
        etContent.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                // Đã sửa tay sau khi chèn: "Hoàn tác" không còn đúng nữa
                if (state.afterVoice != null && !state.afterVoice.equals(s.toString())) {
                    state.afterVoice = null;
                    state.beforeVoice = null;
                    renderVoice();
                }
            }
        });
        renderVoice();
        renderSuggestion();
    }

    // =====================================================================
    // E04 · Giọng nói
    // =====================================================================

    private void insertVoice(String spoken) {
        String current = text(etContent);
        // "Nói lại" ngay sau lần chèn trước (chưa sửa tay): thay phần vừa chèn
        String base = state.afterVoice != null && state.afterVoice.equals(current) && state.beforeVoice != null
                ? state.beforeVoice : current;
        String joined;
        if (base.trim().isEmpty()) {
            joined = spoken;
        } else {
            String b = base.replaceAll("\\s+$", "");
            char last = b.charAt(b.length() - 1);
            joined = b + (last == '.' || last == '!' || last == '?' || last == ',' ? " " : ". ") + spoken;
        }
        if (joined.length() > NeedTextAssistant.MAX_LENGTH) {
            joined = joined.substring(0, NeedTextAssistant.MAX_LENGTH);
            Snackbar.make(anchor, R.string.voice_truncated, Snackbar.LENGTH_LONG).show();
        }
        state.beforeVoice = base;
        state.afterVoice = joined;
        state.voiceInserted = true;
        etContent.setText(joined);
        state.afterVoice = etContent.getText().toString();
        etContent.setSelection(etContent.length());
        etContent.requestFocus();
        renderVoice();
    }

    private void undoVoice() {
        if (state.beforeVoice == null) return;
        String before = state.beforeVoice;
        state.beforeVoice = null;
        state.afterVoice = null;
        state.voiceInserted = false;
        etContent.setText(before);
        etContent.setSelection(etContent.length());
        renderVoice();
        Snackbar.make(anchor, R.string.voice_undone, Snackbar.LENGTH_SHORT).show();
    }

    private void renderVoice() {
        boolean canUndo = state.beforeVoice != null && state.afterVoice != null;
        tvVoiceInserted.setVisibility(state.voiceInserted ? View.VISIBLE : View.GONE);
        chipUndo.setVisibility(canUndo ? View.VISIBLE : View.GONE);
        chipVoice.setText(canUndo ? R.string.voice_action_speak_again : R.string.voice_action_speak);
    }

    // =====================================================================
    // E05 · Gợi ý viết lại
    // =====================================================================

    private void suggest() {
        String content = text(etContent);
        if (content.trim().isEmpty()) {
            Snackbar.make(anchor, R.string.ai_empty_content, Snackbar.LENGTH_LONG).show();
            return;
        }
        NeedTextAssistant.Input in = new NeedTextAssistant.Input();
        in.content = content;
        in.elderlyName = text(etElderlyName);
        in.address = text(etAddress);
        in.contact = text(etContact);
        in.desiredTime = text(etDesiredTime);
        state.suggestion = NeedTextAssistant.rewrite(in);
        renderSuggestion();
    }

    private void useSuggestion() {
        NeedTextAssistant.Suggestion s = state.suggestion;
        if (s == null) return;
        String previous = text(etContent);
        state.suggestion = null;
        state.beforeVoice = null;
        state.afterVoice = null;
        etContent.setText(s.text);
        etContent.setSelection(etContent.length());
        renderVoice();
        renderSuggestion();
        Snackbar.make(anchor, R.string.ai_used, Snackbar.LENGTH_LONG)
                .setAction(R.string.voice_action_undo, v -> {
                    etContent.setText(previous);
                    etContent.setSelection(etContent.length());
                })
                .show();
    }

    private void renderSuggestion() {
        NeedTextAssistant.Suggestion s = state.suggestion;
        boolean shown = s != null;
        cardSuggestion.setVisibility(shown ? View.VISIBLE : View.GONE);
        tvDisclaimer.setVisibility(shown ? View.VISIBLE : View.GONE);
        if (!shown) return;
        tvSuggestion.setText(s.text);
        btnUse.setVisibility(s.differs() && !s.text.isEmpty() ? View.VISIBLE : View.GONE);
        layoutChanges.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(activity);
        for (NeedTextAssistant.Change c : s.changes) {
            View row = inflater.inflate(R.layout.item_ai_change, layoutChanges, false);
            ImageView icon = row.findViewById(R.id.ivAiChange);
            TextView label = row.findViewById(R.id.tvAiChange);
            int color;
            if (c.kind == NeedTextAssistant.KIND_PRIVACY) {
                icon.setImageResource(R.drawable.ic_visibility_off);
                color = R.color.warning_text;
                label.setTextColor(ContextCompat.getColor(activity, R.color.warning_text));
            } else if (c.kind == NeedTextAssistant.KIND_HINT) {
                icon.setImageResource(R.drawable.ic_info);
                color = R.color.primary;
            } else {
                icon.setImageResource(R.drawable.ic_check);
                color = R.color.status_completed;
            }
            icon.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(activity, color)));
            label.setText(describe(c));
            layoutChanges.addView(row);
        }
    }

    private String describe(NeedTextAssistant.Change c) {
        switch (c.code) {
            case NeedTextAssistant.CHANGE_ABBREV: return activity.getString(R.string.ai_change_abbrev, c.detail);
            case NeedTextAssistant.CHANGE_STYLE: return activity.getString(R.string.ai_change_style);
            case NeedTextAssistant.CHANGE_PHONE: return activity.getString(R.string.ai_change_phone);
            case NeedTextAssistant.CHANGE_ADDRESS: return activity.getString(R.string.ai_change_address);
            case NeedTextAssistant.CHANGE_ADDRESS_PHONE: return activity.getString(R.string.ai_change_address_phone);
            case NeedTextAssistant.CHANGE_NAME: return activity.getString(R.string.ai_change_name);
            case NeedTextAssistant.HINT_TIME: return activity.getString(R.string.ai_hint_time);
            case NeedTextAssistant.HINT_DETAIL: return activity.getString(R.string.ai_hint_detail);
            default: return activity.getString(R.string.ai_no_change);
        }
    }

    private static String text(@Nullable EditText et) {
        return et != null && et.getText() != null ? et.getText().toString() : "";
    }
}
