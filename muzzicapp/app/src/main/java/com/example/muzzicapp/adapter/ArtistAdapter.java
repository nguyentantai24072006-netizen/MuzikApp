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

public class ArtistAdapter extends RecyclerView.Adapter<ArtistAdapter.ArtistViewHolder> {

    private List<Track> artists = new ArrayList<>();
    private final OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(Track artistTrack);
    }

    public ArtistAdapter(OnItemClickListener listener) {
        this.listener = listener;
        setHasStableIds(true);
    }

    @Override
    public long getItemId(int position) {
        if (position >= 0 && position < artists.size()) {
            Track t = artists.get(position);
            return (t.getArtist() != null ? t.getArtist().hashCode() : position);
        }
        return position;
    }

    public void submitList(List<Track> newArtists) {
        if (newArtists == null) newArtists = new ArrayList<>();
        final List<Track> oldList = this.artists;
        final List<Track> newList = newArtists;

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
                return Objects.equals(oldList.get(oldItemPosition).getArtist(), newList.get(newItemPosition).getArtist());
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                Track oldT = oldList.get(oldItemPosition);
                Track newT = newList.get(newItemPosition);
                return Objects.equals(oldT.getArtist(), newT.getArtist()) &&
                       Objects.equals(oldT.getArtworkUrl(), newT.getArtworkUrl());
            }
        });

        this.artists = new ArrayList<>(newArtists);
        diffResult.dispatchUpdatesTo(this);
    }

    @NonNull
    @Override
    public ArtistViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_explore_artist, parent, false);
        return new ArtistViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ArtistViewHolder holder, int position) {
        Track track = artists.get(position);
        holder.bind(track);
    }

    @Override
    public int getItemCount() {
        return artists.size();
    }

    class ArtistViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvName;
        private final ImageView ivAvatar;

        public ArtistViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.explore_artist_name);
            ivAvatar = itemView.findViewById(R.id.explore_artist_avatar);

            itemView.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                com.example.muzzicapp.UiAnimationHelper.playBounce(v);
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onItemClick(artists.get(position));
                }
            });
        }

        public void bind(Track track) {
            tvName.setText(track.getArtist());
            
            if (track.getArtworkUrl() != null && !track.getArtworkUrl().trim().isEmpty()) {
                Glide.with(itemView.getContext())
                     .load(track.getArtworkUrl())
                     .override(160, 160)
                     .diskCacheStrategy(DiskCacheStrategy.ALL)
                     .placeholder(R.drawable.bg_avatar_artist_blue)
                     .error(R.drawable.bg_avatar_artist_blue)
                     .circleCrop()
                     .into(ivAvatar);
            } else {
                ivAvatar.setImageResource(R.drawable.bg_avatar_artist_blue);
            }
        }
    }
}
