package com.seniortribe.app.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.seniortribe.app.R;
import com.seniortribe.app.data.PostRepository;
import com.seniortribe.app.models.Membership;
import com.seniortribe.app.models.Post;
import com.seniortribe.app.utils.DateTimeUtils;
import com.seniortribe.app.utils.OrgAvatar;

import java.util.ArrayList;
import java.util.List;

/** Danh sách thẻ bài trên Bảng tin (F04). Nút ⋮ chỉ hiện với đại diện của tổ chức đã đăng (F05). */
public class PostAdapter extends RecyclerView.Adapter<PostAdapter.PostViewHolder> {

    public interface OnPostClickListener {
        void onPostClick(Post post);
        /** Bấm nút ⋮ trên thẻ: mở bảng Sửa / Ẩn bài. */
        void onPostMenu(Post post);
    }

    private final Context context;
    private final OnPostClickListener listener;
    private List<Post> postList = new ArrayList<>();
    @Nullable private Membership membership;

    public PostAdapter(Context context, OnPostClickListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setPosts(List<Post> posts, @Nullable Membership membership) {
        this.postList = posts != null ? posts : new ArrayList<>();
        this.membership = membership;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public PostViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_post, parent, false);
        return new PostViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PostViewHolder holder, int position) {
        Post post = postList.get(position);
        holder.tvAvatar.setText(OrgAvatar.initials(post.getOrgName()));
        OrgAvatar.bindName(holder.tvAuthorOrg, post.getOrgName(), post.isOrgVerified());
        holder.tvTime.setText(DateTimeUtils.formatRelativeTime(post.getCreatedAtMillis()));
        holder.tvTitle.setText(post.getTitle());
        holder.tvContent.setText(post.getContent());

        // Bài đang xem offline thì không sửa / ẩn được nên ẩn luôn nút ⋮
        boolean canManage = !post.isCached() && PostRepository.canManage(membership, post);
        holder.btnMenu.setVisibility(canManage ? View.VISIBLE : View.GONE);
        holder.btnMenu.setOnClickListener(v -> {
            if (listener != null) listener.onPostMenu(post);
        });
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onPostClick(post);
        });
    }

    @Override
    public int getItemCount() {
        return postList.size();
    }

    static class PostViewHolder extends RecyclerView.ViewHolder {
        final TextView tvAvatar, tvAuthorOrg, tvTime, tvTitle, tvContent;
        final View btnMenu;

        PostViewHolder(@NonNull View itemView) {
            super(itemView);
            tvAvatar = itemView.findViewById(R.id.tvAvatar);
            tvAuthorOrg = itemView.findViewById(R.id.tvAuthorOrg);
            tvTime = itemView.findViewById(R.id.tvTime);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvContent = itemView.findViewById(R.id.tvContent);
            btnMenu = itemView.findViewById(R.id.btnPostMenu);
        }
    }
}
