package com.seniortribe.app.ui;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** E08 · Cỡ chữ trong app nhân với cỡ chữ hệ thống, có giới hạn để giao diện không vỡ. */
public class TextScaleApplierTest {

    @Test
    public void nhanVoiCoChuHeThong() {
        assertEquals(1.15f, TextScaleApplier.targetScale(1.0f, 1.15f), 0.0001f);
        assertEquals(1.3f * 1.15f, TextScaleApplier.targetScale(1.15f, 1.3f), 0.0001f);
    }

    @Test
    public void gioiHanTrongKhoangHopLy() {
        assertEquals(2.0f, TextScaleApplier.targetScale(2.0f, 1.3f), 0.0001f);  // máy đã để chữ rất lớn
        assertEquals(0.8f, TextScaleApplier.targetScale(0.85f, 0.9f), 0.0001f); // không nhỏ quá
    }

    @Test
    public void mucMacDinhLaVua() {
        assertEquals(1.0f, DisplaySettings.TEXT_FACTORS[DisplaySettings.TEXT_DEFAULT], 0.0001f);
    }
}
