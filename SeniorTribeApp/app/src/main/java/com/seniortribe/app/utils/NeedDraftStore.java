package com.seniortribe.app.utils;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import com.seniortribe.app.models.NeedForm;

/**
 * Lưu nháp nhu cầu đang nhập (F06, BFD 2.2.7 "Lưu nháp nhu cầu").
 *
 * CHỈ lưu phần tóm tắt. Họ tên, liên hệ, địa chỉ của người cao tuổi không được lưu
 * bền vững trên thiết bị (hạng mục kỹ thuật T05), nên không có trong bản nháp.
 * Nháp gắn với uid để tài khoản khác dùng chung máy không thấy.
 */
public final class NeedDraftStore {

    private static final String PREFS = "need_draft";

    private final SharedPreferences prefs;
    private final String uid;

    public NeedDraftStore(Context context, String uid) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.uid = uid != null ? uid : "";
    }

    public void save(NeedForm form) {
        prefs.edit()
                .putString("uid", uid)
                .putString("title", form.title)
                .putString("topic", form.topic)
                .putString("content", form.content)
                .putString("area", form.area)
                .putString("desiredTime", form.desiredTime)
                .putString("source", form.source)
                .putBoolean("consentRecorded", form.consentRecorded)
                .putString("consentMethod", form.consentMethod)
                .putLong("savedAt", System.currentTimeMillis())
                .apply();
    }

    /** @return bản nháp của tài khoản hiện tại, hoặc null nếu không có. */
    @Nullable
    public NeedForm load() {
        if (!uid.equals(prefs.getString("uid", null))) return null;
        NeedForm f = new NeedForm();
        f.title = prefs.getString("title", "");
        f.topic = prefs.getString("topic", "");
        f.content = prefs.getString("content", "");
        f.area = prefs.getString("area", "");
        f.desiredTime = prefs.getString("desiredTime", "");
        f.source = prefs.getString("source", "");
        f.consentRecorded = prefs.getBoolean("consentRecorded", false);
        f.consentMethod = prefs.getString("consentMethod", "");
        return f;
    }

    public void clear() {
        prefs.edit().clear().apply();
    }
}
