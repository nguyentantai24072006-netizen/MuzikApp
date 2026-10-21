package com.example.muzzicapp;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.example.muzzicapp.PlaylistManager;
import com.example.muzzicapp.auth.AuthDialogHelper;
import com.example.muzzicapp.auth.AuthManager;
import com.example.muzzicapp.model.Track;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

/**
 * Helper class providing modern quick-actions for any music track:
 * Play now, Add to playlist, Favorite, and Share.
 */
public final class TrackOptionsHelper {

    private TrackOptionsHelper() {
        // Utility class
    }

    public static void showTrackOptionsDialog(Context context, Track track) {
        if (context == null || track == null) return;

        BottomSheetDialog dialog = new BottomSheetDialog(context);
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_track_options, null);
        dialog.setContentView(view);

        if (dialog.getWindow() != null) {
            dialog.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
        }

        dialog.setOnShowListener(d -> {
            View bottomSheetInternal = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheetInternal != null) {
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheetInternal);
                behavior.setSkipCollapsed(true);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        });

        ImageView ivThumb = view.findViewById(R.id.track_options_thumb);
        TextView tvTitle = view.findViewById(R.id.track_options_title);
        TextView tvArtist = view.findViewById(R.id.track_options_artist);

        tvTitle.setText(track.getTitle());
        tvArtist.setText(track.getArtist());

        if (track.getArtworkUrl() != null && !track.getArtworkUrl().trim().isEmpty()) {
            Glide.with(context)
                    .load(track.getArtworkUrl())
                    .centerCrop()
                    .placeholder(R.drawable.bg_song_thumb)
                    .into(ivThumb);
        } else {
            ivThumb.setImageResource(R.drawable.bg_song_thumb);
        }

        // Action 1: Play Now
        View optPlayNow = view.findViewById(R.id.track_opt_play_now);
        optPlayNow.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
            MusicPlayerManager manager = MusicPlayerManager.getInstance();
            if (manager != null) {
                manager.playTrack(track);
            }
            dialog.dismiss();
            if (context instanceof MainActivity) {
                ((MainActivity) context).showNowPlayingTab();
            }
        });

        // Action 2: Add to Playlist
        View optAddPlaylist = view.findViewById(R.id.track_opt_add_playlist);
        optAddPlaylist.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
            dialog.dismiss();
            PlaylistDialogHelper.showAddToPlaylistDialog(context, track, null);
        });

        // Action 3: Favorite
        View optFavorite = view.findViewById(R.id.track_opt_favorite);
        ImageView ivFavIcon = view.findViewById(R.id.track_opt_favorite_icon);
        TextView tvFavText = view.findViewById(R.id.track_opt_favorite_text);

        PlaylistManager playlistManager = PlaylistManager.getInstance(context);
        boolean isFav = playlistManager.isTrackFavorite(track);
        updateFavoriteUI(isFav, ivFavIcon, tvFavText);

        optFavorite.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
            if (!AuthManager.getInstance().isLoggedIn()) {
                dialog.dismiss();
                AuthDialogHelper.showGuestPrompt(context,
                        "Vui lòng đăng nhập để lưu bài hát yêu thích và tự động đồng bộ trên mọi thiết bị.",
                        () -> showTrackOptionsDialog(context, track));
                return;
            }

            boolean nowFav = playlistManager.toggleFavorite(track);
            updateFavoriteUI(nowFav, ivFavIcon, tvFavText);
            Toast.makeText(context, nowFav ? "Đã thêm vào bài hát yêu thích" : "Đã xóa khỏi bài hát yêu thích", Toast.LENGTH_SHORT).show();
        });

        // Action 4: Share
        View optShare = view.findViewById(R.id.track_opt_share);
        optShare.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
            dialog.dismiss();
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            String shareUrl = track.getTrackViewUrl() != null && !track.getTrackViewUrl().isEmpty()
                    ? track.getTrackViewUrl()
                    : "https://zingmp3.vn";
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, track.getTitle());
            shareIntent.putExtra(Intent.EXTRA_TEXT, "Đang nghe bài hát \"" + track.getTitle() + "\" của " + track.getArtist() + " trên Muzzic App:\n" + shareUrl);
            context.startActivity(Intent.createChooser(shareIntent, "Chia sẻ bài hát qua"));
        });

        dialog.show();
    }

    private static void updateFavoriteUI(boolean isFavorite, ImageView ivIcon, TextView tvText) {
        if (ivIcon != null) {
            ivIcon.setImageResource(isFavorite ? R.drawable.ic_heart_filled : R.drawable.ic_heart);
        }
        if (tvText != null) {
            tvText.setText(isFavorite ? "Xóa khỏi bài hát yêu thích" : "Thêm vào bài hát yêu thích");
        }
    }
}
