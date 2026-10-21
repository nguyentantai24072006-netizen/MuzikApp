package com.example.muzzicapp.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.example.muzzicapp.R;
import com.example.muzzicapp.model.Track;

import java.util.ArrayList;
import java.util.List;

public class PlaylistCardAdapter extends RecyclerView.Adapter<PlaylistCardAdapter.PlaylistCardViewHolder> {

    private List<PlaylistItem> playlists = new ArrayList<>();
    private final OnPlaylistClickListener listener;

    public interface OnPlaylistClickListener {
        void onPlaylistClick(PlaylistItem playlist);
    }

    public static class PlaylistItem {
        public final String title;
        public final String subtitle;
        public final int gradientResId;
        public final String keyword;
        private List<Track> tracks;
        private String coverUrl;

        public PlaylistItem(String title, String subtitle, int gradientResId, String keyword) {
            this(title, subtitle, gradientResId, keyword, null);
        }

        public PlaylistItem(String title, String subtitle, int gradientResId, String keyword, String defaultCoverUrl) {
            this.title = title;
            this.subtitle = subtitle;
            this.gradientResId = gradientResId;
            this.keyword = keyword;
            this.coverUrl = defaultCoverUrl;
        }

        public void setTracks(List<Track> tracks) {
            this.tracks = tracks;
            if (tracks != null && !tracks.isEmpty()) {
                for (Track t : tracks) {
                    if (t.getArtworkUrl() != null && !t.getArtworkUrl().trim().isEmpty()) {
                        this.coverUrl = t.getArtworkUrl();
                        break;
                    }
                }
            }
        }

        public List<Track> getTracks() { return tracks; }
        public String getCoverUrl() { return coverUrl; }
        public void setCoverUrl(String url) { this.coverUrl = url; }
    }

    public PlaylistCardAdapter(OnPlaylistClickListener listener) {
        this.listener = listener;
    }

    public void submitList(List<PlaylistItem> newPlaylists) {
        this.playlists = newPlaylists;
        notifyDataSetChanged();
    }
    
    public void notifyPlaylistUpdated(int position) {
        if (position >= 0 && position < playlists.size()) {
            notifyItemChanged(position);
        }
    }

    @NonNull
    @Override
    public PlaylistCardViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_playlist_card, parent, false);
        return new PlaylistCardViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PlaylistCardViewHolder holder, int position) {
        PlaylistItem playlist = playlists.get(position);
        holder.bind(playlist);
    }

    @Override
    public int getItemCount() {
        return playlists.size();
    }

    class PlaylistCardViewHolder extends RecyclerView.ViewHolder {
        private final ImageView ivCover;
        private final View vGradient;
        private final TextView tvTitle;
        private final TextView tvSubtitle;

        public PlaylistCardViewHolder(@NonNull View itemView) {
            super(itemView);
            ivCover = itemView.findViewById(R.id.playlist_card_cover);
            vGradient = itemView.findViewById(R.id.playlist_card_gradient);
            tvTitle = itemView.findViewById(R.id.playlist_card_title);
            tvSubtitle = itemView.findViewById(R.id.playlist_card_subtitle);

            itemView.setOnClickListener(v -> {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                com.example.muzzicapp.UiAnimationHelper.playBounce(v);
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onPlaylistClick(playlists.get(position));
                }
            });
        }

        public void bind(PlaylistItem playlist) {
            tvTitle.setText(playlist.title);
            tvSubtitle.setText(playlist.subtitle);
            vGradient.setBackgroundResource(playlist.gradientResId);
            
            String cover = playlist.getCoverUrl();
            if (cover != null && !cover.trim().isEmpty()) {
                ivCover.setVisibility(View.VISIBLE);
                Glide.with(itemView.getContext())
                     .load(cover)
                     .override(320, 320)
                     .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                     .transform(new CenterCrop(), new RoundedCorners(24))
                     .into(ivCover);
            } else {
                Glide.with(itemView.getContext()).clear(ivCover);
                ivCover.setVisibility(View.GONE);
            }
        }
    }
}
