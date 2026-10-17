package com.seniortribe.app.utils;

import androidx.annotation.Nullable;

/**
 * Bọc một sự kiện chỉ xử lý một lần (thông báo Toast, đóng màn hình...).
 * Tránh việc xoay màn hình làm LiveData phát lại thông báo cũ.
 */
public class Event<T> {

    private final T content;
    private boolean handled = false;

    public Event(T content) {
        this.content = content;
    }

    @Nullable
    public T getIfNotHandled() {
        if (handled) return null;
        handled = true;
        return content;
    }
}
