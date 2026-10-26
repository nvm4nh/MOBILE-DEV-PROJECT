package com.seniortribe.app.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.seniortribe.app.R;

/**
 * E04 · Sóng âm đơn giản cho bảng "Đang nghe": mỗi lần micro báo độ lớn (dB) thì đẩy thêm một cột.
 * Cột mới ở bên phải màu chính, cột cũ nhạt dần sang màu viền.
 */
public class VoiceWaveView extends View {

    private static final int BARS = 32;
    private final float[] levels = new float[BARS];
    private final Paint active = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint idle = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private int head;
    private boolean listening;

    public VoiceWaveView(Context context) {
        this(context, null);
    }

    public VoiceWaveView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        active.setColor(ContextCompat.getColor(context, R.color.primary));
        idle.setColor(ContextCompat.getColor(context, R.color.divider));
        for (int i = 0; i < BARS; i++) levels[i] = 0.12f;
    }

    /** @param rmsDb giá trị từ RecognitionListener.onRmsChanged (khoảng -2..10) */
    public void push(float rmsDb) {
        float level = Math.max(0.12f, Math.min(1f, (rmsDb + 2f) / 12f));
        levels[head] = level;
        head = (head + 1) % BARS;
        invalidate();
    }

    public void setListening(boolean listening) {
        this.listening = listening;
        if (!listening) for (int i = 0; i < BARS; i++) levels[i] = Math.min(levels[i], 0.3f);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;
        float gap = w / BARS;
        float barW = Math.max(3f, gap * 0.5f);
        for (int i = 0; i < BARS; i++) {
            float level = levels[(head + i) % BARS];
            float barH = Math.max(barW, h * level);
            float x = i * gap + (gap - barW) / 2f;
            rect.set(x, (h - barH) / 2f, x + barW, (h + barH) / 2f);
            boolean recent = listening && i >= BARS - 22;
            canvas.drawRoundRect(rect, barW / 2f, barW / 2f, recent ? active : idle);
        }
    }
}
