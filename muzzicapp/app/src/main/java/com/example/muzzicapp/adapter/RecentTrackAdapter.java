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
import com.example.muzzicapp.MusicPlayerManager;
import com.example.muzzicapp.R;
import com.example.muzzicapp.TrackOptionsHelper;
import com.example.muzzicapp.UiAnimationHelper;
import com.example.muzzicapp.model.Track;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class RecentTrackAdapter extends RecyclerView.Adapter<RecentTrackAdapter.RecentViewHolder> {

    private List<Track> tracks = new ArrayList<>();
    private final OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(Track track, int position);
    }

    public RecentTrackAdapter(OnItemClickListener listener) {
        this.listener = listener;
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
        final List<Track> newList = new ArrayList<>(newTracks);

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
                Track oldT = oldList.get(oldItemPosition);
                Track newT = newList.get(newItemPosition);
                if (oldT == null || newT == null) return false;
                return Objects.equals(oldT.getTrackKey(), newT.getTrackKey());
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                Track oldT = oldList.get(oldItemPosition);
                Track newT = newList.get(newItemPosition);
                if (oldT == null || newT == null) return false;
                return Objects.equals(oldT.getTitle(), newT.getTitle()) &&
                        Objects.equals(oldT.getArtist(), newT.getArtist()) &&
                        Objects.equals(oldT.getArtworkUrl(), newT.getArtworkUrl());
            }
        });

        this.tracks = newList;
        diffResult.dispatchUpdatesTo(this);
    }

    public List<Track> getTracks() {
        return tracks;
    }

    @NonNull
    @Override
    public RecentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_recent_track, parent, false);
        return new RecentViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecentViewHolder holder, int position) {
        holder.bind(tracks.get(position));
    }

    @Override
    public int getItemCount() {
        return tracks.size();
    }

    class RecentViewHolder extends RecyclerView.ViewHolder {
        private final ImageView ivThumb;
        private final TextView tvTitle;
        private final TextView tvArtist;
        private final ImageView ivPlayingIndicator;

        public RecentViewHolder(@NonNull View itemView) {
            super(itemView);
            ivThumb = itemView.findViewById(R.id.recent_item_thumb);
            tvTitle = itemView.findViewById(R.id.recent_item_title);
            tvArtist = itemView.findViewById(R.id.recent_item_artist);
            ivPlayingIndicator = itemView.findViewById(R.id.recent_item_playing_indicator);

            UiAnimationHelper.applyTouchBounce(itemView);

            itemView.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onItemClick(tracks.get(position), position);
                }
            });

            itemView.setOnLongClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && position < tracks.size()) {
                    TrackOptionsHelper.showTrackOptionsDialog(v.getContext(), tracks.get(position));
                    return true;
                }
                return false;
            });
        }

        public void bind(Track track) {
            tvTitle.setText(track.getTitle());
            tvArtist.setText(track.getArtist());

            MusicPlayerManager manager = MusicPlayerManager.getInstance();
            boolean isCurrent = manager != null && manager.getCurrentTrack() != null &&
                    Objects.equals(manager.getCurrentTrack().getTrackKey(), track.getTrackKey());

            if (isCurrent) {
                tvTitle.setTextColor(0xFF2979FF);
                if (ivPlayingIndicator != null) ivPlayingIndicator.setVisibility(View.VISIBLE);
            } else {
                tvTitle.setTextColor(0xFFFFFFFF);
                if (ivPlayingIndicator != null) ivPlayingIndicator.setVisibility(View.GONE);
            }

            if (track.getArtworkUrl() != null && !track.getArtworkUrl().trim().isEmpty()) {
                Glide.with(itemView.getContext())
                        .load(track.getArtworkUrl())
                        .override(260, 260)
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
