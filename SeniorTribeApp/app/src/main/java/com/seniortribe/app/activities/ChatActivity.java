package com.seniortribe.app.activities;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.seniortribe.app.R;
import com.seniortribe.app.adapters.ChatAdapter;
import com.seniortribe.app.models.ChatMessage;
import com.seniortribe.app.models.NeedSummary;
import com.seniortribe.app.utils.Constants;

import java.util.ArrayList;
import java.util.List;

public class ChatActivity extends AppCompatActivity {

    private Toolbar chatToolbar;
    private RecyclerView rvMessages;
    private EditText etMessage;
    private ImageView btnSend;

    private ChatAdapter adapter;
    private List<ChatMessage> messageList = new ArrayList<>();
    private String currentNeedId = "default";
    private String myUid = "user_me";
    private String myName = "Tôi";
    private FirebaseFirestore firestore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        chatToolbar = findViewById(R.id.chatToolbar);
        rvMessages = findViewById(R.id.rvMessages);
        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);

        try {
            firestore = FirebaseFirestore.getInstance();
            if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                myUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
                myName = FirebaseAuth.getInstance().getCurrentUser().getDisplayName();
            }
        } catch (Exception ignored) {}

        currentNeedId = getIntent().getStringExtra(Constants.EXTRA_NEED_ID);
        if (currentNeedId == null) currentNeedId = "need_01";

        NeedSummary need = (NeedSummary) getIntent().getSerializableExtra(Constants.EXTRA_NEED);
        if (need != null && need.getCaseCode() != null) {
            chatToolbar.setTitle("Chat (" + need.getCaseCode() + ")");
        } else {
            chatToolbar.setTitle("Trao đổi ca hỗ trợ");
        }
        setSupportActionBar(chatToolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            chatToolbar.setNavigationOnClickListener(v -> finish());
        }

        adapter = new ChatAdapter(this, myUid);
        LinearLayoutManager lm = new LinearLayoutManager(this);
        lm.setStackFromEnd(true);
        rvMessages.setLayoutManager(lm);
        rvMessages.setAdapter(adapter);

        btnSend.setOnClickListener(v -> sendMessage());

        listenRealtimeMessages();
    }

    private void listenRealtimeMessages() {
        if (firestore == null) {
            loadSampleMessages();
            return;
        }

        firestore.collection(Constants.COL_CHATS)
                .document(currentNeedId)
                .collection(Constants.COL_MESSAGES)
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null || snapshots == null) {
                        if (messageList.isEmpty()) loadSampleMessages();
                        return;
                    }

                    messageList.clear();
                    snapshots.forEach(doc -> {
                        ChatMessage msg = doc.toObject(ChatMessage.class);
                        msg.setMessageId(doc.getId());
                        messageList.add(msg);
                    });
                    if (messageList.isEmpty()) {
                        loadSampleMessages();
                    } else {
                        adapter.setMessages(messageList);
                        rvMessages.scrollToPosition(messageList.size() - 1);
                    }
                });
    }

    private void loadSampleMessages() {
        messageList.clear();
        messageList.add(new ChatMessage(currentNeedId, "other_uid", "Quyên (Đầu mối)", "Chào anh/chị, em đã lập phiếu hỗ trợ cho cụ bà tại Quận 8."));
        messageList.add(new ChatMessage(currentNeedId, myUid, myName, "Chào bạn, bên mình đã tiếp nhận và sẵn sàng qua thăm cụ vào cuối tuần này."));
        adapter.setMessages(messageList);
        rvMessages.scrollToPosition(messageList.size() - 1);
    }

    private void sendMessage() {
        String text = etMessage.getText().toString().trim();
        if (TextUtils.isEmpty(text)) return;

        ChatMessage msg = new ChatMessage(currentNeedId, myUid, myName, text);
        etMessage.setText("");

        if (firestore != null) {
            firestore.collection(Constants.COL_CHATS)
                    .document(currentNeedId)
                    .collection(Constants.COL_MESSAGES)
                    .add(msg);
        } else {
            messageList.add(msg);
            adapter.setMessages(messageList);
            rvMessages.scrollToPosition(messageList.size() - 1);
        }
    }
}
