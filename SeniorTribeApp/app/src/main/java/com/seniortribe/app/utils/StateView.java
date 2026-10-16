package com.seniortribe.app.utils;

import android.content.res.ColorStateList;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.seniortribe.app.R;

/**
 * Điều khiển layout view_list_state.xml: đang tải / rỗng / lỗi (F16 · trạng thái dữ liệu).
 * Mỗi trạng thái đều có một nút xử lý tiếp theo để người dùng không bị kẹt.
 */
public class StateView {

    private final View root;
    private final View progress;
    private final View iconFrame;
    private final ImageView icon;
    private final TextView title;
    private final TextView message;
    private final MaterialButton action;

    /** @param container view chứa layout view_list_state (thường là root của màn hình) */
    public StateView(View container) {
        root = container.findViewById(R.id.layoutState);
        progress = root.findViewById(R.id.progressState);
        iconFrame = root.findViewById(R.id.frameStateIcon);
        icon = root.findViewById(R.id.ivStateIcon);
        title = root.findViewById(R.id.tvStateTitle);
        message = root.findViewById(R.id.tvStateMessage);
        action = root.findViewById(R.id.btnStateAction);
    }

    public void hide() {
        root.setVisibility(View.GONE);
    }

    public boolean isShowing() {
        return root.getVisibility() == View.VISIBLE;
    }

    public void showLoading() {
        root.setVisibility(View.VISIBLE);
        progress.setVisibility(View.VISIBLE);
        iconFrame.setVisibility(View.GONE);
        title.setVisibility(View.GONE);
        message.setVisibility(View.GONE);
        action.setVisibility(View.GONE);
    }

    /** Trạng thái thông thường (danh sách rỗng, chưa được cấp quyền...): biểu tượng màu xanh. */
    public void showMessage(@DrawableRes int iconRes, String titleText, String messageText,
                            @Nullable String actionText, @Nullable View.OnClickListener onAction) {
        show(iconRes, titleText, messageText, actionText, onAction, R.color.primary_light, R.color.primary);
    }

    /** Trạng thái lỗi: biểu tượng màu đỏ, nút thường là "Thử lại". */
    public void showError(String titleText, String messageText,
                          @Nullable String actionText, @Nullable View.OnClickListener onAction) {
        show(R.drawable.ic_error_outline, titleText, messageText, actionText, onAction,
                R.color.error_container, R.color.error);
    }

    private void show(@DrawableRes int iconRes, String titleText, String messageText,
                      @Nullable String actionText, @Nullable View.OnClickListener onAction,
                      int circleColor, int iconColor) {
        root.setVisibility(View.VISIBLE);
        progress.setVisibility(View.GONE);
        iconFrame.setVisibility(View.VISIBLE);
        iconFrame.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(root.getContext(), circleColor)));
        icon.setImageResource(iconRes);
        icon.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(root.getContext(), iconColor)));
        title.setVisibility(View.VISIBLE);
        title.setText(titleText);
        message.setVisibility(messageText != null ? View.VISIBLE : View.GONE);
        message.setText(messageText);
        if (actionText != null && onAction != null) {
            action.setVisibility(View.VISIBLE);
            action.setText(actionText);
            action.setOnClickListener(onAction);
        } else {
            action.setVisibility(View.GONE);
        }
    }
}
