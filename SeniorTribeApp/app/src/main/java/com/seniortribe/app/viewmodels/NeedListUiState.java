package com.seniortribe.app.viewmodels;

import com.seniortribe.app.models.NeedSummary;

import java.util.ArrayList;
import java.util.List;

/** Toàn bộ dữ liệu màn hình Nhu cầu (F08, F09) cần để vẽ lại, kể cả sau khi xoay màn hình. */
public class NeedListUiState {

    public int mode = NeedListViewModel.MODE_UNIT;

    public boolean loading = true;
    public boolean noMembership = false;
    public String error;           // khác null → hiện trạng thái lỗi kèm nút Thử lại
    public boolean fromCache;      // dữ liệu đang lấy từ bộ nhớ đệm (mất mạng)

    public List<NeedSummary> items = new ArrayList<>();

    // F08 · số lượng cho hàng chip trạng thái
    public String statusGroup = NeedListViewModel.GROUP_ALL;
    public int countAll, countReview, countOpen, countActive, countDone;
    public boolean canCreate;      // tài khoản có quyền đầu mối → hiện nút Ghi nhận

    // F09 · bộ lọc tìm kiếm
    public String keyword = "";
    public String area;            // null = mọi khu vực
    public String topic;           // null = mọi chủ đề
    public int days;               // 0 = mọi thời gian
    public List<String> areaOptions = new ArrayList<>();
    public List<String> topicOptions = new ArrayList<>();

    public boolean hasSearchFilter() {
        return !keyword.isEmpty() || area != null || topic != null || days > 0;
    }
}
