package com.seniortribe.app.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.seniortribe.app.R;
import com.seniortribe.app.activities.CreatePostActivity;
import com.seniortribe.app.activities.PostDetailActivity;
import com.seniortribe.app.adapters.PostAdapter;
import com.seniortribe.app.database.AppDatabaseHelper;
import com.seniortribe.app.models.Post;
import com.seniortribe.app.utils.Constants;
import com.seniortribe.app.utils.NetworkUtils;

import java.util.ArrayList;
import java.util.List;

public class FeedFragment extends Fragment implements PostAdapter.OnPostClickListener {

    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView rvPosts;
    private TextView tvOfflineNotice;
    private FloatingActionButton fabCreatePost;

    private PostAdapter adapter;
    private AppDatabaseHelper dbHelper;
    private FirebaseFirestore firestore;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_feed, container, false);

        swipeRefresh = view.findViewById(R.id.swipeRefresh);
        rvPosts = view.findViewById(R.id.rvPosts);
        tvOfflineNotice = view.findViewById(R.id.tvOfflineNotice);
        fabCreatePost = view.findViewById(R.id.fabCreatePost);

        dbHelper = AppDatabaseHelper.getInstance(requireContext());
        try { firestore = FirebaseFirestore.getInstance(); } catch (Exception ignored) {}

        adapter = new PostAdapter(requireContext(), this);
        rvPosts.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvPosts.setAdapter(adapter);

        swipeRefresh.setOnRefreshListener(this::loadPosts);
        fabCreatePost.setOnClickListener(v -> startActivity(new Intent(requireContext(), CreatePostActivity.class)));

        loadPosts();
        return view;
    }

    private void loadPosts() {
        swipeRefresh.setRefreshing(true);
        if (!NetworkUtils.isNetworkAvailable(requireContext()) || firestore == null) {
            // Tải từ SQLite bộ nhớ đệm ngoại tuyến (Offline Caching - F16, T05)
            loadFromOfflineCache();
            return;
        }

        firestore.collection(Constants.COL_POSTS)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<Post> posts = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        Post p = doc.toObject(Post.class);
                        p.setId(doc.getId());
                        posts.add(p);
                    }
                    if (posts.isEmpty()) {
                        posts = getSamplePosts();
                    }
                    // Tự động lưu cache bài viết vào SQLite
                    dbHelper.savePosts(posts);

                    adapter.setPosts(posts);
                    tvOfflineNotice.setVisibility(View.GONE);
                    swipeRefresh.setRefreshing(false);
                })
                .addOnFailureListener(e -> loadFromOfflineCache());
    }

    private void loadFromOfflineCache() {
        List<Post> cached = dbHelper.getAllCachedPosts();
        if (cached.isEmpty()) {
            cached = getSamplePosts();
            dbHelper.savePosts(cached);
        }
        adapter.setPosts(cached);
        tvOfflineNotice.setVisibility(View.VISIBLE);
        swipeRefresh.setRefreshing(false);
    }

    private List<Post> getSamplePosts() {
        List<Post> list = new ArrayList<>();
        Post p1 = new Post("1", "Chuyến thăm và tặng quà các cụ già neo đơn tại Quận 8",
                "Đoàn thiện nguyện đã đến thăm, tặng 30 suất quà dinh dưỡng và trò chuyện thân mật cùng các cụ có hoàn cảnh khó khăn tại địa bàn Phường 3.",
                "DNXH Vầng Trăng Khuyết", "admin_01");
        p1.setLikesCount(28);
        list.add(p1);

        Post p2 = new Post("2", "Kêu gọi tình nguyện viên hỗ trợ chăm sóc sức khỏe người cao tuổi",
                "Dự án kết hợp cùng HelpAge Việt Nam nhằm mở rộng mạng lưới chăm sóc sức khỏe, hỗ trợ việc nhà và kiểm tra y tế định kỳ cho người già neo đơn.",
                "Hội Chữ thập đỏ TP.HCM", "admin_02");
        p2.setLikesCount(45);
        list.add(p2);
        return list;
    }

    @Override
    public void onPostClick(Post post) {
        Intent intent = new Intent(requireContext(), PostDetailActivity.class);
        intent.putExtra(Constants.EXTRA_POST, post);
        startActivity(intent);
    }
}
