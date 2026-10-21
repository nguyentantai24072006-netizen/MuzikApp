package com.example.muzzicapp.adapter;

import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.example.muzzicapp.R;
import com.example.muzzicapp.model.Track;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class TrackAdapter extends RecyclerView.Adapter<TrackAdapter.TrackViewHolder> {

    private List<Track> tracks = new ArrayList<>();
    private final OnItemClickListener listener;
    private final boolean showRank;

    public interface OnItemClickListener {
        void onItemClick(Track track, int position);
    }

    public TrackAdapter(OnItemClickListener listener) {
        this(listener, true);
    }

    public TrackAdapter(OnItemClickListener listener, boolean showRank) {
        this.listener = listener;
        this.showRank = showRank;
        setHasStableIds(true);
    }

    @Override
    public long getItemId(int position) {
        if (position >= 0 && position < tracks.size()) {
            Track t = tracks.get(position);
            return (t.getTrackKey() != null ? t.getTrackKey().hashCode() : position);
        }
        return position;
    }

    public void submitList(List<Track> newTracks) {
        if (newTracks == null) newTracks = new ArrayList<>();
        final List<Track> oldList = this.tracks;
        final List<Track> newList = newTracks;

        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return oldList.size();
            }

            @Override
            public int getNewListSize() {
                return newList.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                return Objects.equals(oldList.get(oldItemPosition).getTrackKey(), newList.get(newItemPosition).getTrackKey());
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                Track oldT = oldList.get(oldItemPosition);
                Track newT = newList.get(newItemPosition);
                return Objects.equals(oldT.getTitle(), newT.getTitle()) &&
                       Objects.equals(oldT.getArtist(), newT.getArtist()) &&
                       Objects.equals(oldT.getArtworkUrl(), newT.getArtworkUrl());
            }
        });

        this.tracks = new ArrayList<>(newTracks);
        diffResult.dispatchUpdatesTo(this);
    }

    public List<Track> getTracks() {
        return tracks != null ? tracks : new ArrayList<>();
    }

    @NonNull
    @Override
    public TrackViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_trending_song, parent, false);
        return new TrackViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TrackViewHolder holder, int position) {
        Track track = tracks.get(position);
        holder.bind(track, position);
    }

    @Override
    public int getItemCount() {
        return tracks.size();
    }

    class TrackViewHolder extends RecyclerView.ViewHolder {
        private final View container;
        private final TextView tvRank;
        private final TextView tvTitle;
        private final TextView tvArtist;
        private final ImageView ivThumb;
        private final ImageView ivPlayingIndicator;
        private final View playBtn;
        private final ImageView playIcon;
        private final ImageView btnMore;

        public TrackViewHolder(@NonNull View itemView) {
            super(itemView);
            container = itemView.findViewById(R.id.trending_container);
            tvRank = itemView.findViewById(R.id.trending_rank);
            tvTitle = itemView.findViewById(R.id.trending_title);
            tvArtist = itemView.findViewById(R.id.trending_artist);
            ivThumb = itemView.findViewById(R.id.trending_thumb);
            ivPlayingIndicator = itemView.findViewById(R.id.trending_playing_indicator);
            playBtn = itemView.findViewById(R.id.trending_play_btn);
            playIcon = itemView.findViewById(R.id.trending_play_icon);
            btnMore = itemView.findViewById(R.id.trending_btn_more);

            itemView.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                com.example.muzzicapp.UiAnimationHelper.playBounce(v);
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onItemClick(tracks.get(position), position);
                }
            });

            if (playBtn != null) {
                playBtn.setOnClickListener(v -> {
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    com.example.muzzicapp.UiAnimationHelper.playBounce(v);
                    int position = getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION && listener != null) {
                        listener.onItemClick(tracks.get(position), position);
                    }
                });
            }

            if (btnMore != null) {
                btnMore.setOnClickListener(v -> {
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    com.example.muzzicapp.UiAnimationHelper.playBounce(v);
                    int position = getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION && position < tracks.size()) {
                        com.example.muzzicapp.TrackOptionsHelper.showTrackOptionsDialog(v.getContext(), tracks.get(position));
                    }
                });
            }
        }

        public void bind(Track track, int position) {
            // Rank badge styling
            if (tvRank != null) {
                if (showRank) {
                    tvRank.setVisibility(View.VISIBLE);
                    tvRank.setText(String.valueOf(position + 1));
                    if (position == 0) {
                        tvRank.setTextColor(0xFFF59E0B); // Top 1 Gold
                        tvRank.setTextSize(16f);
                    } else if (position == 1) {
                        tvRank.setTextColor(0xFF38BDF8); // Top 2 Sky Blue
                        tvRank.setTextSize(15f);
                    } else if (position == 2) {
                        tvRank.setTextColor(0xFF2DD4BF); // Top 3 Teal
                        tvRank.setTextSize(15f);
                    } else {
                        tvRank.setTextColor(0xFF64748B); // Slate Grey
                        tvRank.setTextSize(13f);
                    }
                } else {
                    tvRank.setVisibility(View.GONE);
                }
            }

            tvTitle.setText(track.getTitle());
            tvArtist.setText(track.getArtist());

            // Active playback state detection
            com.example.muzzicapp.MusicPlayerManager manager = com.example.muzzicapp.MusicPlayerManager.getInstance();
            boolean isCurrent = manager != null && manager.getCurrentTrack() != null &&
                    Objects.equals(manager.getCurrentTrack().getTrackKey(), track.getTrackKey());
            boolean isPlaying = isCurrent && manager.isPlaying();

            if (isCurrent) {
                if (container != null) container.setBackgroundResource(R.drawable.bg_track_row_playing);
                tvTitle.setTextColor(0xFF2979FF);
                if (ivPlayingIndicator != null) ivPlayingIndicator.setVisibility(View.VISIBLE);
                if (playIcon != null) {
                    playIcon.setImageResource(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play);
                    playIcon.setColorFilter(0xFFFFFFFF);
                }
            } else {
                if (container != null) container.setBackgroundResource(R.drawable.bg_track_row_idle);
                tvTitle.setTextColor(0xFFFFFFFF);
                if (ivPlayingIndicator != null) ivPlayingIndicator.setVisibility(View.GONE);
                if (playIcon != null) {
                    playIcon.setImageResource(R.drawable.ic_play);
                    playIcon.setColorFilter(0xFFFFFFFF);
                }
            }

            if (track.getArtworkUrl() != null && !track.getArtworkUrl().trim().isEmpty()) {
                Glide.with(itemView.getContext())
                     .load(track.getArtworkUrl())
                     .override(160, 160)
                     .diskCacheStrategy(DiskCacheStrategy.ALL)
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
