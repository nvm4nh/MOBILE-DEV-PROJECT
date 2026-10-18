package vn.edu.ueh.quyenong.f04;

import android.app.Activity;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class NeedsActivity extends Activity {

    private TextView tvStateIcon;
    private TextView tvStateTitle;
    private TextView tvStateMessage;
    private TextView btnStateAction;
    private EditText etSearchNeed;

    private boolean showingError = false;
    private boolean loading = false;

    private final Handler handler =
            new Handler(Looper.getMainLooper());

    private final Runnable loadingTimeout = () -> {
        loading = false;

        if (!isNetworkAvailable()) {
            showOfflineState();
        } else {
            showErrorState();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_needs);

        tvStateIcon = findViewById(R.id.tvStateIcon);
        tvStateTitle = findViewById(R.id.tvStateTitle);
        tvStateMessage = findViewById(R.id.tvStateMessage);
        btnStateAction = findViewById(R.id.btnStateAction);
        etSearchNeed = findViewById(R.id.etSearchNeed);

        TextView btnReceived = findViewById(R.id.btnReceived);
        TextView btnAvailable = findViewById(R.id.btnAvailable);
        TextView btnDistrict = findViewById(R.id.btnDistrict);
        TextView btnTopic = findViewById(R.id.btnTopic);
        TextView btnTime = findViewById(R.id.btnTime);

        btnReceived.setOnClickListener(v ->
                Toast.makeText(
                        this,
                        "Mục Đơn vị ghi nhận",
                        Toast.LENGTH_SHORT
                ).show()
        );

        btnAvailable.setOnClickListener(v -> {
            if (isNetworkAvailable()) {
                showEmptyState();
            } else {
                showOfflineState();
            }
        });

        btnDistrict.setOnClickListener(v ->
                Toast.makeText(
                        this,
                        "Bộ lọc khu vực",
                        Toast.LENGTH_SHORT
                ).show()
        );

        btnTopic.setOnClickListener(v ->
                Toast.makeText(
                        this,
                        "Bộ lọc chủ đề",
                        Toast.LENGTH_SHORT
                ).show()
        );

        btnTime.setOnClickListener(v ->
                Toast.makeText(
                        this,
                        "Bộ lọc thời gian",
                        Toast.LENGTH_SHORT
                ).show()
        );

        btnStateAction.setOnClickListener(v -> {
            if (loading) {
                return;
            }

            if (!isNetworkAvailable()) {
                showOfflineState();
                return;
            }

            if (showingError) {
                showLoadingState();
            } else {
                etSearchNeed.setText("");
                showEmptyState();
            }
        });

        checkNetworkState();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (tvStateTitle != null) {
            checkNetworkState();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(loadingTimeout);
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm =
                (ConnectivityManager) getSystemService(
                        Context.CONNECTIVITY_SERVICE
                );

        if (cm == null) {
            return false;
        }

        Network network = cm.getActiveNetwork();

        if (network == null) {
            return false;
        }

        NetworkCapabilities capabilities =
                cm.getNetworkCapabilities(network);

        return capabilities != null
                && capabilities.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_INTERNET
        )
                && capabilities.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_VALIDATED
        );
    }

    private void checkNetworkState() {
        if (loading) {
            return;
        }

        if (!isNetworkAvailable()) {
            showOfflineState();
        } else {
            showEmptyState();
        }
    }

    private void showOfflineState() {
        handler.removeCallbacks(loadingTimeout);
        loading = false;
        showingError = true;

        tvStateIcon.setText("!");
        tvStateTitle.setText("Bạn đang ngoại tuyến");
        tvStateMessage.setText(
                "Không có kết nối Internet. " +
                        "Hãy kiểm tra mạng rồi thử lại."
        );
        btnStateAction.setText("Thử lại");
        btnStateAction.setEnabled(true);
    }

    private void showEmptyState() {
        handler.removeCallbacks(loadingTimeout);
        loading = false;
        showingError = false;

        tvStateIcon.setText("⌕");
        tvStateTitle.setText("Chưa có nhu cầu phù hợp");
        tvStateMessage.setText(
                "Thử bỏ bớt bộ lọc khu vực hoặc chủ đề " +
                        "để xem thêm nhu cầu đang cần."
        );
        btnStateAction.setText("Xóa bộ lọc");
        btnStateAction.setEnabled(true);
    }

    private void showLoadingState() {
        if (!isNetworkAvailable()) {
            showOfflineState();
            return;
        }

        handler.removeCallbacks(loadingTimeout);
        loading = true;

        tvStateIcon.setText("…");
        tvStateTitle.setText("Đang tải dữ liệu");
        tvStateMessage.setText(
                "Vui lòng chờ trong giây lát."
        );
        btnStateAction.setText("Đang tải...");
        btnStateAction.setEnabled(false);

        handler.postDelayed(loadingTimeout, 1200);
    }

    private void showErrorState() {
        loading = false;
        showingError = true;

        tvStateIcon.setText("!");
        tvStateTitle.setText("Không tải được dữ liệu");
        tvStateMessage.setText(
                "Đã xảy ra lỗi khi tải dữ liệu. " +
                        "Vui lòng thử lại."
        );
        btnStateAction.setText("Thử lại");
        btnStateAction.setEnabled(true);
    }
}
