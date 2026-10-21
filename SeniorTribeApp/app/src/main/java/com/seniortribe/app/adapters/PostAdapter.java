package com.seniortribe.app.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.seniortribe.app.R;
import com.seniortribe.app.models.Post;
import com.seniortribe.app.utils.DateTimeUtils;

import java.util.ArrayList;
import java.util.List;

public class PostAdapter extends RecyclerView.Adapter<PostAdapter.PostViewHolder> {

    public interface OnPostClickListener {
        void onPostClick(Post post);
    }

    private final Context context;
    private List<Post> postList = new ArrayList<>();
    private final OnPostClickListener listener;

    public PostAdapter(Context context, OnPostClickListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setPosts(List<Post> posts) {
        this.postList = posts != null ? posts : new ArrayList<>();
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
        holder.tvAuthorOrg.setText(post.getAuthorOrg() != null ? post.getAuthorOrg() : "Tổ chức thiện nguyện");
        holder.tvTitle.setText(post.getTitle());
        holder.tvContent.setText(post.getContent());
        holder.tvTime.setText(DateTimeUtils.formatRelativeTime(post.getCreatedAt()));
        holder.tvLikes.setText(post.getLikesCount() + " quan tâm");

        if (post.isCached()) {
            holder.tvOfflineBadge.setVisibility(View.VISIBLE);
        } else {
            holder.tvOfflineBadge.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onPostClick(post);
        });
    }

    @Override
    public int getItemCount() {
        return postList.size();
    }

    static class PostViewHolder extends RecyclerView.ViewHolder {
        TextView tvAuthorOrg, tvTime, tvTitle, tvContent, tvLikes, tvOfflineBadge;

        public PostViewHolder(@NonNull View itemView) {
            super(itemView);
            tvAuthorOrg = itemView.findViewById(R.id.tvAuthorOrg);
            tvTime = itemView.findViewById(R.id.tvTime);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvContent = itemView.findViewById(R.id.tvContent);
            tvLikes = itemView.findViewById(R.id.tvLikes);
            tvOfflineBadge = itemView.findViewById(R.id.tvOfflineBadge);
        }
    }
}
