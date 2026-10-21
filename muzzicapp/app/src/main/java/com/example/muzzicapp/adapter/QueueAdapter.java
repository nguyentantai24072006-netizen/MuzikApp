package com.example.muzzicapp.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.muzzicapp.R;
import com.example.muzzicapp.model.Track;

import java.util.ArrayList;
import java.util.List;

public class QueueAdapter extends RecyclerView.Adapter<QueueAdapter.QueueViewHolder> {

    public interface QueueListener {
        void onTrackClick(int position);
        void onRemoveClick(int position);
    }

    private final List<Track> tracks = new ArrayList<>();
    private int currentPlayingIndex = -1;
    private final QueueListener listener;

    public QueueAdapter(List<Track> initialTracks, int currentPlayingIndex, QueueListener listener) {
        if (initialTracks != null) this.tracks.addAll(initialTracks);
        this.currentPlayingIndex = currentPlayingIndex;
        this.listener = listener;
    }

    public void updateTracks(List<Track> newTracks, int currentPlayingIndex) {
        this.tracks.clear();
        if (newTracks != null) this.tracks.addAll(newTracks);
        this.currentPlayingIndex = currentPlayingIndex;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public QueueViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_queue_track, parent, false);
        return new QueueViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull QueueViewHolder holder, int position) {
        Track track = tracks.get(position);
        boolean isPlaying = (position == currentPlayingIndex);
        holder.bind(track, position, isPlaying);
    }

    @Override
    public int getItemCount() {
        return tracks.size();
    }

    class QueueViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvIndex;
        private final ImageView ivThumb;
        private final TextView tvTitle;
        private final TextView tvArtist;
        private final TextView tvBadge;
        private final ImageView btnRemove;

        public QueueViewHolder(@NonNull View itemView) {
            super(itemView);
            tvIndex = itemView.findViewById(R.id.queue_item_index);
            ivThumb = itemView.findViewById(R.id.queue_item_thumb);
            tvTitle = itemView.findViewById(R.id.queue_item_title);
            tvArtist = itemView.findViewById(R.id.queue_item_artist);
            tvBadge = itemView.findViewById(R.id.queue_item_playing_badge);
            btnRemove = itemView.findViewById(R.id.queue_item_remove);

            com.example.muzzicapp.UiAnimationHelper.applyTouchBounce(btnRemove);

            itemView.setOnClickListener(v -> {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                com.example.muzzicapp.UiAnimationHelper.playBounce(v);
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && listener != null) {
                    listener.onTrackClick(pos);
                }
            });

            btnRemove.setOnClickListener(v -> {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && listener != null) {
                    listener.onRemoveClick(pos);
                    tracks.remove(pos);
                    notifyItemRemoved(pos);
                    notifyItemRangeChanged(pos, tracks.size());
                }
            });
        }

        public void bind(Track track, int position, boolean isPlaying) {
            tvIndex.setText(String.valueOf(position + 1));
            String title = track.getTitle();
            if (title == null || title.trim().isEmpty() || "null".equalsIgnoreCase(title)) {
                title = "Bài hát #" + (position + 1);
            }
            String artist = track.getArtist();
            if (artist == null || artist.trim().isEmpty() || "null".equalsIgnoreCase(artist)) {
                artist = "Nhiều nghệ sĩ";
            }
            tvTitle.setText(title);
            tvArtist.setText(artist);

            if (isPlaying) {
                tvBadge.setVisibility(View.VISIBLE);
                tvTitle.setTextColor(itemView.getContext().getResources().getColor(R.color.coral_primary, null));
            } else {
                tvBadge.setVisibility(View.GONE);
                tvTitle.setTextColor(itemView.getContext().getResources().getColor(R.color.text_primary_adaptive, null));
            }

            if (track.getArtworkUrl() != null && !track.getArtworkUrl().trim().isEmpty() && !"null".equalsIgnoreCase(track.getArtworkUrl())) {
                Glide.with(itemView.getContext())
                        .load(track.getArtworkUrl())
                        .placeholder(R.drawable.bg_song_thumb)
                        .error(R.drawable.bg_song_thumb)
                        .centerCrop()
                        .into(ivThumb);
            } else {
                ivThumb.setImageResource(R.drawable.bg_song_thumb);
            }
        }
    }
}
