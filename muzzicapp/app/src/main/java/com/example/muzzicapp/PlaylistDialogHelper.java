package com.example.muzzicapp;

import android.app.AlertDialog;
import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.muzzicapp.adapter.PlaylistChoiceAdapter;
import com.example.muzzicapp.adapter.QueueAdapter;
import com.example.muzzicapp.api.MusicApiService;
import com.example.muzzicapp.auth.AuthDialogHelper;
import com.example.muzzicapp.auth.AuthManager;
import com.example.muzzicapp.model.Track;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.List;
import java.util.Random;

public class PlaylistDialogHelper {

    public static final String[] STYLE_NAMES = {
            "Neon Wave", "Midnight Flow", "Acoustic Sunset", "Cyber Synth",
            "Emerald Chill", "Vintage Vinyl", "Lo-Fi Beats", "Night Drive",
            "Festival Light", "Stage Energy", "Cafe Piano", "Retro Echo"
    };

    public static final String[] AUTO_COVERS = {
            "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=500&q=80",
            "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=500&q=80",
            "https://images.unsplash.com/photo-1510915361894-db8b60106cb1?w=500&q=80",
            "https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?w=500&q=80",
            "https://images.unsplash.com/photo-1518609878373-06d740f60d8b?w=500&q=80",
            "https://images.unsplash.com/photo-1539375665275-f9de415ef9ac?w=500&q=80",
            "https://images.unsplash.com/photo-1505740420928-5e560c06d30e?w=500&q=80",
            "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=500&q=80",
            "https://images.unsplash.com/photo-1459749411175-04bf5292ceea?w=500&q=80",
            "https://images.unsplash.com/photo-1516450360452-9312f5e86fc7?w=500&q=80",
            "https://images.unsplash.com/photo-1520523839898-507121087cf8?w=500&q=80",
            "https://images.unsplash.com/photo-1465847899084-d164df4dedc6?w=500&q=80"
    };

    public static final int[] STYLE_GRADIENTS = {
            R.drawable.bg_gradient_suggest1,
            R.drawable.bg_gradient_suggest2,
            R.drawable.bg_gradient_suggest3,
            R.drawable.bg_gradient_suggest4,
            R.drawable.bg_gradient_chill,
            R.drawable.bg_gradient_hiphop,
            R.drawable.bg_gradient_acoustic,
            R.drawable.bg_gradient_vpop
    };

    public static void showCreatePlaylistDialog(Context context, Track trackToAdd, Runnable onCreatedCallback) {
        if (context == null) return;

        if (!AuthManager.getInstance().isLoggedIn()) {
            AuthDialogHelper.showGuestPrompt(context,
                    "Vui lòng đăng nhập để tạo playlist và tự động đồng bộ trên mọi thiết bị.",
                    () -> showCreatePlaylistDialog(context, trackToAdd, onCreatedCallback));
            return;
        }

        BottomSheetDialog dialog = new BottomSheetDialog(context);
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_create_playlist, null);
        dialog.setContentView(view);

        ImageView ivCoverPreview = view.findViewById(R.id.create_playlist_cover_preview);
        ImageView ivWatermark = view.findViewById(R.id.create_playlist_cover_watermark);
        TextView tvStyleName = view.findViewById(R.id.create_playlist_cover_style_name);
        TextView btnCycleCover = view.findViewById(R.id.create_playlist_btn_cycle_cover);
        EditText inputName = view.findViewById(R.id.create_playlist_input_name);

        // Auto-select a unique random cover style on dialog open
        Random random = new Random();
        final int[] selectedIndex = {random.nextInt(STYLE_NAMES.length)};
        final boolean[] userManuallyPicked = {false};

        if (trackToAdd != null && trackToAdd.getArtworkUrl() != null && !trackToAdd.getArtworkUrl().isEmpty()) {
            ivWatermark.setVisibility(View.GONE);
            Glide.with(context).load(trackToAdd.getArtworkUrl()).centerCrop().into(ivCoverPreview);
            tvStyleName.setText("Ảnh bìa: Tự động theo bài hát");
        } else {
            updateCoverUI(context, selectedIndex[0], ivCoverPreview, ivWatermark, tvStyleName);
        }

        btnCycleCover.setOnClickListener(v -> {
            userManuallyPicked[0] = true;
            selectedIndex[0] = (selectedIndex[0] + 1) % STYLE_NAMES.length;
            updateCoverUI(context, selectedIndex[0], ivCoverPreview, ivWatermark, tvStyleName);
        });

        // Dynamic auto-cover change as user types playlist name if not manually locked
        inputName.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (trackToAdd != null || userManuallyPicked[0]) return;
                String query = s.toString().toLowerCase().trim();
                if (query.isEmpty()) return;

                int targetIndex;
                if (query.contains("chill") || query.contains("thư giãn")) {
                    targetIndex = 4; // Emerald Chill
                } else if (query.contains("năng lượng") || query.contains("gym") || query.contains("edm")) {
                    targetIndex = 9; // Stage Energy
                } else if (query.contains("đêm") || query.contains("night") || query.contains("ngủ")) {
                    targetIndex = 1; // Midnight Flow
                } else if (query.contains("cà phê") || query.contains("coffee") || query.contains("acoustic") || query.contains("guitar")) {
                    targetIndex = 2; // Acoustic Sunset
                } else if (query.contains("lofi") || query.contains("beat")) {
                    targetIndex = 6; // Lo-Fi Beats
                } else if (query.contains("retro") || query.contains("vinyl") || query.contains("xưa")) {
                    targetIndex = 5; // Vintage Vinyl
                } else {
                    targetIndex = Math.abs(query.hashCode()) % STYLE_NAMES.length;
                }
                selectedIndex[0] = targetIndex;
                updateCoverUI(context, selectedIndex[0], ivCoverPreview, ivWatermark, tvStyleName);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Setup preset chips
        view.findViewById(R.id.chip_preset_chill).setOnClickListener(v -> {
            userManuallyPicked[0] = true;
            inputName.setText("Nhạc Chill Thư Giãn");
            selectedIndex[0] = 4;
            updateCoverUI(context, selectedIndex[0], ivCoverPreview, ivWatermark, tvStyleName);
        });
        view.findViewById(R.id.chip_preset_energy).setOnClickListener(v -> {
            userManuallyPicked[0] = true;
            inputName.setText("Bùng Nổ Năng Lượng");
            selectedIndex[0] = 9;
            updateCoverUI(context, selectedIndex[0], ivCoverPreview, ivWatermark, tvStyleName);
        });
        view.findViewById(R.id.chip_preset_night).setOnClickListener(v -> {
            userManuallyPicked[0] = true;
            inputName.setText("Giai Điệu Đêm Muộn");
            selectedIndex[0] = 1;
            updateCoverUI(context, selectedIndex[0], ivCoverPreview, ivWatermark, tvStyleName);
        });
        view.findViewById(R.id.chip_preset_coffee).setOnClickListener(v -> {
            userManuallyPicked[0] = true;
            inputName.setText("Góc Cà Phê Acoustic");
            selectedIndex[0] = 2;
            updateCoverUI(context, selectedIndex[0], ivCoverPreview, ivWatermark, tvStyleName);
        });
        view.findViewById(R.id.chip_preset_fav).setOnClickListener(v -> {
            userManuallyPicked[0] = true;
            inputName.setText("Bộ Sưu Tập Yêu Thích");
            selectedIndex[0] = 0;
            updateCoverUI(context, selectedIndex[0], ivCoverPreview, ivWatermark, tvStyleName);
        });

        view.findViewById(R.id.create_playlist_close).setOnClickListener(v -> dialog.dismiss());
        view.findViewById(R.id.create_playlist_btn_cancel).setOnClickListener(v -> dialog.dismiss());

        view.findViewById(R.id.create_playlist_btn_submit).setOnClickListener(v -> {
            String name = inputName.getText().toString().trim();
            if (name.isEmpty()) {
                name = "Playlist " + STYLE_NAMES[selectedIndex[0]];
            }

            String chosenCoverUrl = AUTO_COVERS[selectedIndex[0]];
            if (trackToAdd != null && trackToAdd.getArtworkUrl() != null && !trackToAdd.getArtworkUrl().isEmpty()) {
                chosenCoverUrl = trackToAdd.getArtworkUrl();
            }

            PlaylistManager pm = PlaylistManager.getInstance(context);
            PlaylistManager.UserPlaylist newPlaylist = pm.createPlaylist(name, chosenCoverUrl, selectedIndex[0]);

            if (trackToAdd != null) {
                pm.addTrackToPlaylist(newPlaylist.getId(), trackToAdd);
                Toast.makeText(context, "Đã tạo playlist và thêm bài: " + trackToAdd.getTitle(), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(context, "Đã tạo playlist: " + name, Toast.LENGTH_SHORT).show();
            }

            dialog.dismiss();
            if (onCreatedCallback != null) {
                onCreatedCallback.run();
            }
        });

        dialog.show();
    }

    private static void updateCoverUI(Context context, int index, ImageView ivPreview, ImageView ivWatermark, TextView tvStyleName) {
        tvStyleName.setText("Phong cách: " + STYLE_NAMES[index]);
        String url = AUTO_COVERS[index];
        Glide.with(context).clear(ivPreview);
        if (url != null && !url.isEmpty()) {
            ivWatermark.setVisibility(View.GONE);
            ivPreview.setBackgroundResource(0);
            Glide.with(context).load(url).centerCrop().into(ivPreview);
        } else {
            ivWatermark.setVisibility(View.VISIBLE);
            ivPreview.setImageDrawable(null);
            ivPreview.setBackgroundResource(STYLE_GRADIENTS[index % STYLE_GRADIENTS.length]);
        }
    }

    public static void showAddToPlaylistDialog(Context context, Track track, Runnable onAddedCallback) {
        if (context == null || track == null) return;

        if (!AuthManager.getInstance().isLoggedIn()) {
            AuthDialogHelper.showGuestPrompt(context,
                    "Vui lòng đăng nhập để thêm bài hát vào danh sách phát.",
                    () -> showAddToPlaylistDialog(context, track, onAddedCallback));
            return;
        }

        BottomSheetDialog dialog = new BottomSheetDialog(context);
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_add_to_playlist, null);
        dialog.setContentView(dialogView);

        dialogView.findViewById(R.id.dialog_playlist_close).setOnClickListener(v -> dialog.dismiss());
        RecyclerView recycler = dialogView.findViewById(R.id.dialog_playlist_recycler);
        recycler.setLayoutManager(new LinearLayoutManager(context));

        PlaylistManager pm = PlaylistManager.getInstance(context);
        List<PlaylistManager.UserPlaylist> playlists = pm.getPlaylists();

        PlaylistChoiceAdapter adapter = new PlaylistChoiceAdapter(playlists, true, track, p -> {
            boolean added = pm.addTrackToPlaylist(p.getId(), track);
            if (added) {
                Toast.makeText(context, "Đã thêm vào " + p.getName(), Toast.LENGTH_SHORT).show();
                dialog.dismiss();
                if (onAddedCallback != null) {
                    onAddedCallback.run();
                }
            } else {
                Toast.makeText(context, "Bài hát đã có trong " + p.getName(), Toast.LENGTH_SHORT).show();
            }
        });
        recycler.setAdapter(adapter);

        dialogView.findViewById(R.id.dialog_playlist_create_new).setOnClickListener(v -> {
            dialog.dismiss();
            showCreatePlaylistDialog(context, track, onAddedCallback);
        });

        dialog.show();
    }

    public static void showPlaylistDetailDialog(Context context, PlaylistManager.UserPlaylist playlist, Runnable onPlaylistChangedCallback) {
        if (context == null || playlist == null) return;

        BottomSheetDialog dialog = new BottomSheetDialog(context);
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_playlist_detail, null);
        dialog.setContentView(view);
        if (dialog.getWindow() != null) {
            dialog.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
        }
        dialog.setOnShowListener(d -> {
            View bottomSheetInternal = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheetInternal != null) {
                com.google.android.material.bottomsheet.BottomSheetBehavior<View> behavior =
                        com.google.android.material.bottomsheet.BottomSheetBehavior.from(bottomSheetInternal);
                behavior.setSkipCollapsed(true);
                behavior.setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
            }
        });

        ImageView ivCover = view.findViewById(R.id.playlist_detail_cover);
        TextView tvTitle = view.findViewById(R.id.playlist_detail_title);
        TextView tvCount = view.findViewById(R.id.playlist_detail_count);
        TextView btnPlayAll = view.findViewById(R.id.playlist_detail_btn_play_all);
        TextView btnChangeCover = view.findViewById(R.id.playlist_detail_btn_change_cover);
        View btnDeleteContainer = view.findViewById(R.id.playlist_detail_btn_delete_container);
        View btnClose = view.findViewById(R.id.playlist_detail_close);
        RecyclerView recyclerView = view.findViewById(R.id.playlist_detail_recycler);
        View emptyView = view.findViewById(R.id.playlist_detail_empty);

        UiAnimationHelper.applyTouchBounce(btnPlayAll);
        UiAnimationHelper.applyTouchBounce(btnChangeCover);
        UiAnimationHelper.applyTouchBounce(btnDeleteContainer);
        UiAnimationHelper.applyTouchBounce(btnClose);

        tvTitle.setText(playlist.getName());
        tvCount.setText(playlist.getTrackCount() + " bài hát");

        // Load cover art
        loadCoverArt(context, playlist, ivCover);

        if (btnClose != null) {
            btnClose.setOnClickListener(v -> {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                dialog.dismiss();
            });
        }

        // Auto change cover button: cycles cover art on tap
        if (btnChangeCover != null) {
            btnChangeCover.setOnClickListener(v -> {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                int nextIndex = (playlist.getCoverStyleIndex() + 1) % AUTO_COVERS.length;
                String nextUrl = AUTO_COVERS[nextIndex];
                playlist.setCoverArtUrl(nextUrl);
                playlist.setCoverStyleIndex(nextIndex);
                PlaylistManager.getInstance(context).savePlaylists();
                loadCoverArt(context, playlist, ivCover);
                Toast.makeText(context, "Đã đổi ảnh bìa: " + STYLE_NAMES[nextIndex], Toast.LENGTH_SHORT).show();
                if (onPlaylistChangedCallback != null) {
                    onPlaylistChangedCallback.run();
                }
            });
        }

        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        recyclerView.setHasFixedSize(true);
        recyclerView.setItemViewCacheSize(15);
        List<Track> tracks = playlist.getTracks();

        if (tracks == null || tracks.isEmpty()) {
            recyclerView.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            emptyView.setVisibility(View.GONE);
        }

        QueueAdapter adapter = new QueueAdapter(tracks, -1, new QueueAdapter.QueueListener() {
            @Override
            public void onTrackClick(int position) {
                if (playlist.getTracks() != null && position < playlist.getTracks().size()) {
                    MusicPlayerManager.getInstance().setQueue(playlist.getTracks());
                    MusicPlayerManager.getInstance().playTrack(playlist.getTracks().get(position));
                    dialog.dismiss();
                    if (context instanceof MainActivity) {
                        ((MainActivity) context).showNowPlayingTab();
                    }
                }
            }

            @Override
            public void onRemoveClick(int position) {
                if (playlist.getTracks() != null && position < playlist.getTracks().size()) {
                    Track removedTrack = playlist.getTracks().get(position);
                    PlaylistManager.getInstance(context).removeTrackFromPlaylist(playlist.getId(), removedTrack.getTrackKey());
                    tvCount.setText(playlist.getTrackCount() + " bài hát");
                    if (playlist.getTrackCount() == 0) {
                        recyclerView.setVisibility(View.GONE);
                        emptyView.setVisibility(View.VISIBLE);
                    }
                    Toast.makeText(context, "Đã xóa bài hát khỏi playlist", Toast.LENGTH_SHORT).show();
                    if (onPlaylistChangedCallback != null) {
                        onPlaylistChangedCallback.run();
                    }
                }
            }
        });
        recyclerView.setAdapter(adapter);

        btnPlayAll.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
            if (playlist.getTracks() != null && !playlist.getTracks().isEmpty()) {
                MusicPlayerManager.getInstance().setQueue(playlist.getTracks());
                MusicPlayerManager.getInstance().playTrack(playlist.getTracks().get(0));
                dialog.dismiss();
                if (context instanceof MainActivity) {
                    ((MainActivity) context).showNowPlayingTab();
                }
            } else {
                Toast.makeText(context, "Playlist này chưa có bài hát để phát", Toast.LENGTH_SHORT).show();
            }
        });

        if (btnDeleteContainer != null) {
            btnDeleteContainer.setOnClickListener(v -> {
                new AlertDialog.Builder(context)
                        .setTitle("Xóa Playlist")
                        .setMessage("Bạn có chắc chắn muốn xóa playlist \"" + playlist.getName() + "\" không?\nThao tác này không thể hoàn tác.")
                        .setPositiveButton("Xóa playlist", (d, w) -> {
                            PlaylistManager.getInstance(context).deletePlaylist(playlist.getId());
                            Toast.makeText(context, "Đã xóa playlist: " + playlist.getName(), Toast.LENGTH_SHORT).show();
                            dialog.dismiss();
                            if (onPlaylistChangedCallback != null) {
                                onPlaylistChangedCallback.run();
                            }
                        })
                        .setNegativeButton("Hủy", null)
                        .show();
            });
        }

        dialog.show();
    }

    private static void loadCoverArt(Context context, PlaylistManager.UserPlaylist playlist, ImageView ivCover) {
        Glide.with(context).clear(ivCover);
        String effectiveCover = playlist.getEffectiveCoverUrl();
        if (effectiveCover != null && !effectiveCover.trim().isEmpty()) {
            ivCover.setBackgroundResource(0);
            Glide.with(context).load(effectiveCover).centerCrop().into(ivCover);
        } else {
            ivCover.setImageDrawable(null);
            ivCover.setBackgroundResource(playlist.getCoverGradientRes());
        }
    }

    public static void showCuratedPlaylistDetailDialog(Context context, com.example.muzzicapp.adapter.PlaylistCardAdapter.PlaylistItem playlist) {
        if (context == null || playlist == null) return;

        BottomSheetDialog dialog = new BottomSheetDialog(context);
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_playlist_detail, null);
        dialog.setContentView(view);
        if (dialog.getWindow() != null) {
            dialog.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
        }
        dialog.setOnShowListener(d -> {
            View bottomSheetInternal = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheetInternal != null) {
                com.google.android.material.bottomsheet.BottomSheetBehavior<View> behavior =
                        com.google.android.material.bottomsheet.BottomSheetBehavior.from(bottomSheetInternal);
                behavior.setSkipCollapsed(true);
                behavior.setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
            }
        });

        ImageView ivCover = view.findViewById(R.id.playlist_detail_cover);
        TextView tvTitle = view.findViewById(R.id.playlist_detail_title);
        TextView tvCount = view.findViewById(R.id.playlist_detail_count);
        TextView btnPlayAll = view.findViewById(R.id.playlist_detail_btn_play_all);
        View btnChangeCover = view.findViewById(R.id.playlist_detail_btn_change_cover);
        View btnDeleteContainer = view.findViewById(R.id.playlist_detail_btn_delete_container);
        View btnClose = view.findViewById(R.id.playlist_detail_close);
        RecyclerView recyclerView = view.findViewById(R.id.playlist_detail_recycler);
        View emptyView = view.findViewById(R.id.playlist_detail_empty);

        tvTitle.setText(playlist.title);
        List<Track> tracks = playlist.getTracks();
        int count = tracks != null ? tracks.size() : 0;
        tvCount.setText(playlist.subtitle + " • " + count + " bài hát");

        Glide.with(context).clear(ivCover);
        if (playlist.getCoverUrl() != null && !playlist.getCoverUrl().trim().isEmpty()) {
            ivCover.setBackgroundResource(0);
            Glide.with(context).load(playlist.getCoverUrl()).centerCrop().into(ivCover);
        } else {
            ivCover.setImageDrawable(null);
            ivCover.setBackgroundResource(playlist.gradientResId);
        }

        UiAnimationHelper.applyTouchBounce(btnPlayAll);
        UiAnimationHelper.applyTouchBounce(btnChangeCover);
        UiAnimationHelper.applyTouchBounce(btnClose);

        if (btnChangeCover != null) {
            btnChangeCover.setVisibility(View.VISIBLE);
            if (btnChangeCover instanceof TextView) {
                ((TextView) btnChangeCover).setText("Đổi mới");
            }
            btnChangeCover.setOnClickListener(v -> {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                Toast.makeText(context, "Đang làm mới danh sách...", Toast.LENGTH_SHORT).show();
                MusicApiService.invalidateKeyPrefix("artist_v4_");
                MusicApiService.invalidateKeyPrefix("artist_v3_");
                MusicApiService.invalidateKeyPrefix("artist_v2_");
                MusicApiService.invalidateKeyPrefix("playlist_v4_");
                MusicApiService.invalidateKeyPrefix("playlist_v3_");
                MusicApiService.invalidateKeyPrefix("playlist_v2_");
                MusicApiService.invalidateKeyPrefix("playlist_");
                new MusicApiService().getPlaylistByKeyword(playlist.keyword, 25, new MusicApiService.MusicCallback() {
                    @Override
                    public void onSuccess(List<Track> freshTracks) {
                        if (freshTracks != null && !freshTracks.isEmpty() && dialog.isShowing()) {
                            playlist.setTracks(freshTracks);
                            tvCount.setText(playlist.subtitle + " • " + freshTracks.size() + " bài hát");
                            emptyView.setVisibility(View.GONE);
                            recyclerView.setVisibility(View.VISIBLE);
                            setupCuratedRecycler(context, dialog, recyclerView, freshTracks);
                            Toast.makeText(context, "Đã làm mới danh sách bài hát!", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onError(Exception e) {
                        Toast.makeText(context, "Không thể làm mới: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            });
        }
        if (btnDeleteContainer != null) btnDeleteContainer.setVisibility(View.GONE);
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                dialog.dismiss();
            });
        }

        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        recyclerView.setHasFixedSize(true);
        recyclerView.setItemViewCacheSize(15);
        if (tracks == null || tracks.isEmpty()) {
            recyclerView.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
            tvCount.setText(playlist.subtitle + " • Đang tải bài hát...");
            new MusicApiService().getPlaylistByKeyword(playlist.keyword, 25, new MusicApiService.MusicCallback() {
                @Override
                public void onSuccess(List<Track> loadedTracks) {
                    if (loadedTracks != null && !loadedTracks.isEmpty() && dialog.isShowing()) {
                        playlist.setTracks(loadedTracks);
                        tvCount.setText(playlist.subtitle + " • " + loadedTracks.size() + " bài hát");
                        emptyView.setVisibility(View.GONE);
                        recyclerView.setVisibility(View.VISIBLE);
                        setupCuratedRecycler(context, dialog, recyclerView, loadedTracks);
                    }
                }

                @Override
                public void onError(Exception e) {
                    if (dialog.isShowing()) {
                        tvCount.setText(playlist.subtitle + " • 0 bài hát");
                    }
                }
            });
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            emptyView.setVisibility(View.GONE);
            setupCuratedRecycler(context, dialog, recyclerView, tracks);
        }

        btnPlayAll.setOnClickListener(v -> {
            List<Track> currentList = playlist.getTracks();
            if (currentList != null && !currentList.isEmpty()) {
                MusicPlayerManager.getInstance().setQueue(currentList);
                MusicPlayerManager.getInstance().playTrack(currentList.get(0));
                dialog.dismiss();
                if (context instanceof MainActivity) {
                    ((MainActivity) context).showNowPlayingTab();
                }
            } else {
                Toast.makeText(context, "Đang tải bài hát cho " + playlist.title + "...", Toast.LENGTH_SHORT).show();
            }
        });

        dialog.show();
    }

    private static void setupCuratedRecycler(Context context, BottomSheetDialog dialog, RecyclerView recyclerView, List<Track> tracks) {
        QueueAdapter adapter = new QueueAdapter(tracks, -1, new QueueAdapter.QueueListener() {
            @Override
            public void onTrackClick(int position) {
                if (tracks != null && position < tracks.size()) {
                    MusicPlayerManager.getInstance().setQueue(tracks);
                    MusicPlayerManager.getInstance().playTrack(tracks.get(position));
                    dialog.dismiss();
                    if (context instanceof MainActivity) {
                        ((MainActivity) context).showNowPlayingTab();
                    }
                }
            }

            @Override
            public void onRemoveClick(int position) {}
        });
        recyclerView.setAdapter(adapter);
    }
}
