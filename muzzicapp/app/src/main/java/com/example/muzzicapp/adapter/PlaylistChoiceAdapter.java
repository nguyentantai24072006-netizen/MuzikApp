package com.example.muzzicapp.adapter;

import android.graphics.Color;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.muzzicapp.PlaylistManager;
import com.example.muzzicapp.R;
import com.example.muzzicapp.model.Track;

import java.util.ArrayList;
import java.util.List;

public class PlaylistChoiceAdapter extends RecyclerView.Adapter<PlaylistChoiceAdapter.ChoiceViewHolder> {

    public interface OnPlaylistSelectedListener {
        void onSelected(PlaylistManager.UserPlaylist playlist);
    }

    private final List<PlaylistManager.UserPlaylist> playlists = new ArrayList<>();
    private final OnPlaylistSelectedListener listener;
    private boolean isSelectionMode = false;
    private Track candidateTrack;

    public PlaylistChoiceAdapter(List<PlaylistManager.UserPlaylist> playlists, OnPlaylistSelectedListener listener) {
        this(playlists, false, null, listener);
    }

    public PlaylistChoiceAdapter(List<PlaylistManager.UserPlaylist> playlists, boolean isSelectionMode, OnPlaylistSelectedListener listener) {
        this(playlists, isSelectionMode, null, listener);
    }

    public PlaylistChoiceAdapter(List<PlaylistManager.UserPlaylist> playlists, boolean isSelectionMode, Track candidateTrack, OnPlaylistSelectedListener listener) {
        if (playlists != null) this.playlists.addAll(playlists);
        this.isSelectionMode = isSelectionMode;
        this.candidateTrack = candidateTrack;
        this.listener = listener;
    }

    public void setCandidateTrack(Track candidateTrack) {
        this.candidateTrack = candidateTrack;
        notifyDataSetChanged();
    }

    public void updatePlaylists(List<PlaylistManager.UserPlaylist> newPlaylists) {
        this.playlists.clear();
        if (newPlaylists != null) this.playlists.addAll(newPlaylists);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ChoiceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_playlist_choice, parent, false);
        return new ChoiceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChoiceViewHolder holder, int position) {
        PlaylistManager.UserPlaylist playlist = playlists.get(position);
        holder.bind(playlist);
    }

    @Override
    public int getItemCount() {
        return playlists.size();
    }

    class ChoiceViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvName;
        private final TextView tvCount;
        private final ImageView ivCover;
        private final ImageView ivAction;

        public ChoiceViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.playlist_choice_name);
            tvCount = itemView.findViewById(R.id.playlist_choice_count);
            ivCover = itemView.findViewById(R.id.playlist_choice_icon);
            ivAction = itemView.findViewById(R.id.playlist_choice_action_icon);
        }

        public void bind(PlaylistManager.UserPlaylist playlist) {
            tvName.setText(playlist.getName());

            boolean isAlreadyInPlaylist = isSelectionMode && candidateTrack != null && playlist.hasTrack(candidateTrack);

            if (isAlreadyInPlaylist) {
                String countText = playlist.getTrackCount() + " bài hát • Đã có trong playlist";
                SpannableString ss = new SpannableString(countText);
                int start = countText.indexOf("Đã có trong playlist");
                if (start != -1) {
                    ss.setSpan(new ForegroundColorSpan(Color.parseColor("#34C759")), start, countText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                tvCount.setText(ss);

                if (ivAction != null) {
                    ivAction.setImageResource(R.drawable.ic_check);
                    ivAction.setColorFilter(Color.parseColor("#34C759"));
                }

                itemView.setAlpha(0.85f);
                itemView.setOnClickListener(v -> {
                    Toast.makeText(itemView.getContext(), "Bài hát này đã có trong \"" + playlist.getName() + "\"", Toast.LENGTH_SHORT).show();
                });
            } else {
                tvCount.setText(playlist.getTrackCount() + " bài hát");
                tvCount.setTextColor(itemView.getContext().getResources().getColor(R.color.text_secondary_adaptive, null));
                itemView.setAlpha(1.0f);

                if (ivAction != null) {
                    if (isSelectionMode) {
                        ivAction.setImageResource(R.drawable.ic_add);
                        ivAction.setColorFilter(itemView.getContext().getResources().getColor(R.color.coral_primary, null));
                    } else {
                        ivAction.setImageResource(R.drawable.ic_chevron_right);
                        ivAction.setColorFilter(itemView.getContext().getResources().getColor(R.color.text_tertiary_adaptive, null));
                    }
                }

                itemView.setOnClickListener(v -> {
                    int pos = getAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION && listener != null) {
                        listener.onSelected(playlist);
                    }
                });
            }

            Glide.with(itemView.getContext()).clear(ivCover);
            String effectiveCover = playlist.getEffectiveCoverUrl();
            if (effectiveCover != null && !effectiveCover.trim().isEmpty()) {
                ivCover.clearColorFilter();
                ivCover.setPadding(0, 0, 0, 0);
                ivCover.setBackgroundResource(0);
                Glide.with(itemView.getContext())
                        .load(effectiveCover)
                        .centerCrop()
                        .into(ivCover);
            } else {
                ivCover.setBackgroundResource(playlist.getCoverGradientRes());
                ivCover.setImageResource(R.drawable.ic_bookmark);
                ivCover.setPadding(10, 10, 10, 10);
                ivCover.setColorFilter(Color.WHITE);
            }
        }
    }
}
