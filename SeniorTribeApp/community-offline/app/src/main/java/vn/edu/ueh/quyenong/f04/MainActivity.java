
package vn.edu.ueh.quyenong.f04;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private final String organization1 =
            "Đoàn Thanh niên Phường 7";

    private final String title1 =
            "Ngày hội thăm hỏi người cao tuổi tháng 10";

    private final String content1 =
            "Sáng Chủ nhật 20/10, đoàn viên Phường 7 đến thăm " +
                    "và trò chuyện cùng 15 cụ trong khu phố. " +
                    "Mỗi nhóm 2–3 bạn, có đầu mối của phường đi cùng.\n\n" +
                    "Đơn vị muốn phối hợp vui lòng nhắn đại diện qua " +
                    "ứng dụng trước thứ Sáu.";

    private final String organization2 =
            "Hội Chữ thập đỏ Quận 3";

    private final String title2 =
            "Kết nối tình nguyện viên cuối tuần";

    private final String content2 =
            "Mời các đơn vị cùng tham gia hoạt động cộng đồng " +
                    "và kết nối nguồn lực hỗ trợ.";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        View cardPost1 = findViewById(R.id.cardPost1);
        View cardPost2 = findViewById(R.id.cardPost2);

        ImageButton btnPostMenu =
                findViewById(R.id.btnPostMenu);

        TextView btnCreatePost =
                findViewById(R.id.btnCreatePost);

        TextView btnAll =
                findViewById(R.id.btnAll);

        TextView btnMyUnits =
                findViewById(R.id.btnMyUnits);

        if (cardPost1 != null) {
            cardPost1.setOnClickListener(v ->
                    openDetail(
                            organization1,
                            title1,
                            content1
                    ));
        }

        if (cardPost2 != null) {
            cardPost2.setOnClickListener(v ->
                    openDetail(
                            organization2,
                            title2,
                            content2
                    ));
        }

        if (btnPostMenu != null) {
            btnPostMenu.setOnClickListener(v ->
                    showPostOptions());
        }

        if (btnCreatePost != null) {
            btnCreatePost.setOnClickListener(v -> {
                Intent intent = new Intent(
                        MainActivity.this,
                        CreatePostActivity.class
                );
                startActivity(intent);
            });
        }

        if (btnAll != null) {
            btnAll.setOnClickListener(v ->
                    Toast.makeText(
                            MainActivity.this,
                            "Đang hiển thị tất cả bài đăng",
                            Toast.LENGTH_SHORT
                    ).show());
        }

        if (btnMyUnits != null) {
            btnMyUnits.setOnClickListener(v ->
                    Toast.makeText(
                            MainActivity.this,
                            "Bộ lọc đơn vị của tôi sẽ được hoàn thiện sau",
                            Toast.LENGTH_SHORT
                    ).show());
        }
    }

    /**
     * Hiển thị bảng tùy chọn bài đăng theo giao diện Figma.
     */
    private void showPostOptions() {

        Dialog dialog = new Dialog(this);

        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_post_options);
        dialog.setCancelable(true);

        Window window = dialog.getWindow();

        if (window != null) {
            window.setBackgroundDrawable(
                    new ColorDrawable(Color.TRANSPARENT)
            );

            window.addFlags(
                    WindowManager.LayoutParams.FLAG_DIM_BEHIND
            );

            WindowManager.LayoutParams params =
                    window.getAttributes();

            params.width =
                    WindowManager.LayoutParams.MATCH_PARENT;

            params.height =
                    WindowManager.LayoutParams.WRAP_CONTENT;

            params.gravity = Gravity.BOTTOM;
            params.dimAmount = 0.42f;

            window.setAttributes(params);
        }

        TextView tvOptionTitle =
                dialog.findViewById(R.id.tvOptionTitle);

        TextView tvOptionDescription =
                dialog.findViewById(R.id.tvOptionDescription);

        View btnEditPost =
                dialog.findViewById(R.id.btnEditPost);

        View btnHidePost =
                dialog.findViewById(R.id.btnHidePost);

        View btnCloseOptions =
                dialog.findViewById(R.id.btnCloseOptions);

        if (tvOptionTitle != null) {
            tvOptionTitle.setText(title1);
        }

        if (tvOptionDescription != null) {
            tvOptionDescription.setText(
                    "Chỉ đại diện của Đoàn Thanh niên Phường 7 " +
                            "thấy các tùy chọn này."
            );
        }

        // Sửa bài: mở màn hình đăng bài và điền dữ liệu hiện có.
        if (btnEditPost != null) {
            btnEditPost.setOnClickListener(v -> {
                dialog.dismiss();

                Intent intent = new Intent(
                        MainActivity.this,
                        CreatePostActivity.class
                );

                intent.putExtra("edit_title", title1);
                intent.putExtra("edit_content", content1);

                startActivity(intent);
            });
        }

        // Ẩn bài: hiện thông báo lựa chọn.
        if (btnHidePost != null) {
            btnHidePost.setOnClickListener(v -> {
                dialog.dismiss();

                Toast.makeText(
                        MainActivity.this,
                        "Đã chọn ẩn bài khỏi bảng tin",
                        Toast.LENGTH_SHORT
                ).show();
            });
        }

        // Đóng bảng tùy chọn.
        if (btnCloseOptions != null) {
            btnCloseOptions.setOnClickListener(v ->
                    dialog.dismiss());
        }

        dialog.show();

        // Đặt chiều rộng sau khi Dialog được hiển thị.
        Window shownWindow = dialog.getWindow();

        if (shownWindow != null) {
            shownWindow.setBackgroundDrawable(
                    new ColorDrawable(Color.TRANSPARENT)
            );

            shownWindow.setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT
            );

            shownWindow.setGravity(Gravity.BOTTOM);
        }
    }

    /**
     * Mở màn hình chi tiết bài đăng.
     */
    private void openDetail(
            String organization,
            String title,
            String content) {

        Intent intent = new Intent(
                MainActivity.this,
                DetailActivity.class
        );

        intent.putExtra("organization", organization);
        intent.putExtra("title", title);
        intent.putExtra("content", content);

        startActivity(intent);
    }
}
