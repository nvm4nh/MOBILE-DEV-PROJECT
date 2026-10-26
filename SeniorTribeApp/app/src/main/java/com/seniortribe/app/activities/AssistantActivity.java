package com.seniortribe.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.seniortribe.app.R;
import com.seniortribe.app.adapters.AssistantAdapter;
import com.seniortribe.app.models.AssistantMessage;
import com.seniortribe.app.models.NeedSummary;
import com.seniortribe.app.utils.Constants;
import com.seniortribe.app.utils.VoiceInput;
import com.seniortribe.app.viewmodels.AssistantViewModel;

import java.util.ArrayList;
import java.util.List;

/**
 * E05 · Trợ lý SeniorTribe: hướng dẫn thao tác (trả lại, đối soát, chốt lịch…) và gợi ý nhu cầu đang chờ
 * tiếp nhận phù hợp với câu hỏi. Chạy ngay trên máy theo quy tắc soạn sẵn; chỉ dùng bản tóm tắt nhu cầu,
 * không xem thông tin riêng của cụ và không thay người dùng quyết định. Có thể hỏi bằng giọng nói (E04).
 */
public class AssistantActivity extends AppCompatActivity implements AssistantAdapter.OnNeedClickListener {

    private static final String VOICE_KEY = "assistant_voice";

    private AssistantViewModel viewModel;
    private AssistantAdapter adapter;
    private RecyclerView rv;
    private EditText etQuestion;
    private View btnSend;
    private ChipGroup chipGroup;
    private VoiceInput voice;
    private int lastCount = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_assistant);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        rv = findViewById(R.id.rvAssistant);
        etQuestion = findViewById(R.id.etAssistant);
        btnSend = findViewById(R.id.btnAssistantSend);
        chipGroup = findViewById(R.id.chipGroupAssistant);

        LinearLayoutManager lm = new LinearLayoutManager(this);
        lm.setStackFromEnd(true);
        rv.setLayoutManager(lm);
        adapter = new AssistantAdapter(this, this);
        rv.setAdapter(adapter);

        voice = new VoiceInput(this, VOICE_KEY, 300, rv, text -> {
            etQuestion.setText(text);
            etQuestion.setSelection(etQuestion.length());
        });
        findViewById(R.id.btnAssistantMic).setOnClickListener(v -> voice.start());
        btnSend.setOnClickListener(v -> send());
        etQuestion.setOnEditorActionListener((v, actionId, event) -> {
            boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN;
            if (actionId == EditorInfo.IME_ACTION_SEND || enter) {
                send();
                return true;
            }
            return false;
        });
        etQuestion.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { btnSend.setEnabled(s.toString().trim().length() > 0); }
        });
        btnSend.setEnabled(false);

        viewModel = new ViewModelProvider(this).get(AssistantViewModel.class);
        viewModel.getUi().observe(this, this::render);
        viewModel.start();
    }

    private void send() {
        String q = etQuestion.getText().toString().trim();
        if (q.isEmpty()) return;
        etQuestion.setText("");
        viewModel.ask(q);
    }

    private void render(AssistantViewModel.Ui s) {
        List<AssistantMessage> items = new ArrayList<>(s.messages);
        if (s.searching) items.add(AssistantMessage.caption(getString(R.string.assistant_typing)));
        adapter.setItems(items);
        if (items.size() != lastCount && !items.isEmpty()) rv.scrollToPosition(items.size() - 1);
        lastCount = items.size();
        renderChips(s.chips);
    }

    private void renderChips(List<String> chips) {
        chipGroup.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (String text : chips) {
            Chip chip = (Chip) inflater.inflate(R.layout.item_assistant_chip, chipGroup, false);
            chip.setText(text);
            chip.setOnClickListener(v -> viewModel.ask(text));
            chipGroup.addView(chip);
        }
        findViewById(R.id.scrollAssistantChips).setVisibility(chips.isEmpty() ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onNeedClick(NeedSummary need) {
        startActivity(new Intent(this, NeedDetailActivity.class).putExtra(Constants.EXTRA_NEED_ID, need.getNeedId()));
    }
}
