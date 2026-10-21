package com.example.muzzicapp;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseUser;

import java.util.Calendar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSnapHelper;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.example.muzzicapp.adapter.ArtistAdapter;
import com.example.muzzicapp.adapter.PlaylistCardAdapter;
import com.example.muzzicapp.adapter.RecentTrackAdapter;
import com.example.muzzicapp.adapter.TrackAdapter;
import com.example.muzzicapp.api.MusicApiService;
import com.example.muzzicapp.auth.AuthDialogHelper;
import com.example.muzzicapp.auth.AuthManager;
import com.example.muzzicapp.model.Track;

import java.util.ArrayList;
import java.util.List;

public class HomeFragment extends Fragment {

    private String currentMood = "relax";
    private TrackAdapter trendingAdapter;
    private TrackAdapter recommendedAdapter;
    private RecentTrackAdapter recentAdapter;
    private View recentSection;
    private PlaylistCardAdapter playlistAdapter;
    private final List<PlaylistCardAdapter.PlaylistItem> playlistItems = new ArrayList<>();
    private ArtistAdapter artistAdapter;
    private PlaylistCardAdapter hotGenresAdapter;
    private final List<PlaylistCardAdapter.PlaylistItem> hotGenreItems = new ArrayList<>();
    private final MusicApiService apiService = new MusicApiService();

    private final HistoryManager.HistoryListener historyListener = recentTracks -> {
        if (getActivity() == null) return;
        getActivity().runOnUiThread(() -> updateRecentSection(recentTracks));
    };

    private TextView tvGreetingPrefix;
    private TextView tvGreetingName;
    private TextView tvActionBadge;
    private ImageView ivAvatarImg;
    private TextView tvAvatarText;
    private View vAvatarStatusDot;
    private View headerProfile;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);
        setupHeaderGreeting(view);
        setupMoodChips(view);

        setupRecentSection(view);
        setupPlaylistSection(view);
        setupRecommendedSection(view);
        fetchTrendingMusic(view);
        setupFeaturedCard(view);
        setupFeaturedArtistsSection(view);
        setupHotGenresSection(view);
        setupFooter(view);
        return view;
    }

    // ========== MOOD CHIPS — Real search & interactive playlist by mood ==========

    private void setupMoodChips(View view) {
        TextView chipRelax = view.findViewById(R.id.chip_relax);
        TextView chipEnergy = view.findViewById(R.id.chip_energy);
        TextView chipChill = view.findViewById(R.id.chip_chill);
        TextView chipFocus = view.findViewById(R.id.chip_focus);
        TextView chipHappy = view.findViewById(R.id.chip_happy);
        TextView chipSad = view.findViewById(R.id.chip_sad);
        TextView chipLove = view.findViewById(R.id.chip_love);

        TextView[] chips = {chipRelax, chipEnergy, chipChill, chipFocus, chipHappy, chipSad, chipLove};
        String[] moods = {"relax", "energy", "chill", "focus", "happy", "sad", "love"};
        String[] moodNames = {
                "Thư giãn", "Năng lượng", "Chill",
                "Tập trung", "Vui vẻ", "Tâm trạng", "Lãng mạn"
        };
        String[] moodSubtitles = {
                "Giai điệu êm dịu, xoa dịu tâm hồn",
                "Bùng nổ năng lượng, đốt cháy đam mê",
                "Thả mình theo từng nhịp điệu lofi",
                "Tăng cường tập trung học tập & làm việc",
                "Giai điệu tươi vui, lan tỏa năng lượng tích cực",
                "Những bản tình ca sâu lắng chất chứa nỗi niềm",
                "Bản tình ca ngọt ngào dành cho đôi lứa"
        };
        int[] moodGradients = {
                R.drawable.bg_gradient_chill,
                R.drawable.bg_gradient_suggest2,
                R.drawable.bg_gradient_suggest1,
                R.drawable.bg_gradient_suggest3,
                R.drawable.bg_gradient_vpop,
                R.drawable.bg_gradient_suggest4,
                R.drawable.bg_gradient_acoustic
        };
        String[] moodCovers = {
                "https://images.unsplash.com/photo-1518609878373-06d740f60d8b?w=500&q=80",
                "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=500&q=80",
                "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=500&q=80",
                "https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?w=500&q=80",
                "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=500&q=80",
                "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=500&q=80",
                "https://images.unsplash.com/photo-1518199266791-5375a83190b7?w=500&q=80"
        };

        for (int i = 0; i < chips.length; i++) {
            if (chips[i] == null) continue;
            final int index = i;
            chips[i].setOnClickListener(v -> {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                UiAnimationHelper.animateChipSelect(v);
                // Reset all chips visually
                for (TextView chip : chips) {
                    if (chip != null) {
                        chip.setBackgroundResource(R.drawable.bg_chip_light);
                        chip.setTextColor(getResources().getColor(R.color.chip_default_text_adaptive, null));
                    }
                }
                // Highlight clicked chip
                v.setBackgroundResource(R.drawable.bg_chip_selected);
                ((TextView) v).setTextColor(getResources().getColor(R.color.white, null));

                currentMood = moods[index];
                String name = moodNames[index];
                String subtitle = moodSubtitles[index];
                int gradient = moodGradients[index];
                String cover = moodCovers[index];

                // 1. Update Home section title
                TextView tvTrendingTitle = view.findViewById(R.id.home_trending_title);
                if (tvTrendingTitle != null) {
                    tvTrendingTitle.setText("Giai điệu: " + name);
                }

                // 2. Load tracks to Home feed in background
                loadMoodTracks(currentMood);

                // 3. Open rich Curated Mood Playlist Dialog smoothly without blocking the UI thread
                PlaylistCardAdapter.PlaylistItem moodPlaylist = new PlaylistCardAdapter.PlaylistItem(
                        name, subtitle, gradient, currentMood, cover
                );
                v.postDelayed(() -> {
                    if (isAdded() && getContext() != null) {
                        PlaylistDialogHelper.showCuratedPlaylistDetailDialog(requireContext(), moodPlaylist);
                    }
                }, 120);
            });
        }

        View notifContainer = view.findViewById(R.id.home_notification_container);
        if (notifContainer != null) {
            UiAnimationHelper.applyTouchBounce(notifContainer);
            notifContainer.setOnClickListener(v -> {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                Toast.makeText(getContext(), "Không có thông báo mới", Toast.LENGTH_SHORT).show();
            });
        }

        View featuredCard = view.findViewById(R.id.featured_card);
        if (featuredCard != null) {
        }
    }

    private void loadMoodTracks(String mood) {
        if (getView() == null) return;
        ProgressBar loading = getView().findViewById(R.id.trending_loading);
        if (loading != null) loading.setVisibility(View.VISIBLE);
        
        apiService.getTracksByMood(mood, new MusicApiService.MusicCallback() {
            @Override
            public void onSuccess(List<Track> tracks) {
                if (getActivity() == null) return;
                if (loading != null) loading.setVisibility(View.GONE);
                if (trendingAdapter != null && tracks != null && !tracks.isEmpty()) {
                    trendingAdapter.submitList(tracks);
                    // Set queue for playback
                    MusicPlayerManager manager = MusicPlayerManager.getInstance();
                    if (manager != null) {
                        manager.setQueue(tracks);
                    }
                }
            }

            @Override
            public void onError(Exception e) {
                if (getActivity() == null) return;
                if (loading != null) loading.setVisibility(View.GONE);
            }
        });
    }

    // ========== FEATURED CARD — Play trending playlist ==========

    private PlaylistCardAdapter.PlaylistItem trendingPlaylistItem;
    private final List<PlaylistCardAdapter.PlaylistItem> curatedPool = new ArrayList<>();

    // ========== FEATURED CARD — Trending Global Playlist ==========

    private void setupFeaturedCard(View view) {
        trendingPlaylistItem = new PlaylistCardAdapter.PlaylistItem(
                "Trending Global 2026",
                "Những bài hit đang thịnh hành nhất",
                R.drawable.bg_gradient_featured,
                "trending",
                "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=500&q=80"
        );

        // Preload trending tracks
        apiService.getTrendingTracks(new MusicApiService.MusicCallback() {
            @Override
            public void onSuccess(List<Track> tracks) {
                if (trendingPlaylistItem != null && tracks != null) {
                    trendingPlaylistItem.setTracks(tracks);
                }
            }

            @Override
            public void onError(Exception e) {}
        });

        // Click card: Open curated detail dialog with full Real-time Chart
        view.findViewById(R.id.featured_card).setOnClickListener(v -> {
            PlaylistDialogHelper.showCuratedPlaylistDetailDialog(requireContext(), trendingPlaylistItem);
        });

        // Click Play icon: Direct play & jump to player
        View playBtn = view.findViewById(R.id.featured_play_button);
        if (playBtn != null) {
            playBtn.setOnClickListener(v -> {
                List<Track> currentList = trendingPlaylistItem.getTracks();
                if (currentList != null && !currentList.isEmpty()) {
                    MusicPlayerManager manager = MusicPlayerManager.getInstance();
                    manager.setQueue(currentList);
                    manager.playTrack(currentList.get(0));
                    Toast.makeText(getContext(), "Đang phát Trending: " + currentList.get(0).getTitle(), Toast.LENGTH_SHORT).show();
                    if (getActivity() instanceof MainActivity) {
                        ((MainActivity) getActivity()).showNowPlayingTab();
                    }
                } else {
                    Toast.makeText(getContext(), "Đang tải bài hát Trending...", Toast.LENGTH_SHORT).show();
                    apiService.getTrendingTracks(new MusicApiService.MusicCallback() {
                        @Override
                        public void onSuccess(List<Track> tracks) {
                            if (getActivity() == null || tracks.isEmpty()) return;
                            trendingPlaylistItem.setTracks(tracks);
                            MusicPlayerManager manager = MusicPlayerManager.getInstance();
                            manager.setQueue(tracks);
                            manager.playTrack(tracks.get(0));
                            Toast.makeText(getContext(), "Đang phát: " + tracks.get(0).getTitle(), Toast.LENGTH_SHORT).show();
                            if (getActivity() instanceof MainActivity) {
                                ((MainActivity) getActivity()).showNowPlayingTab();
                            }
                        }

                        @Override
                        public void onError(Exception e) {
                            if (getActivity() == null) return;
                            Toast.makeText(getContext(), "Lỗi tải playlist thịnh hành", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });
        }
    }

    // ========== RECENT SECTION — Recently Played Tracks ==========

    private void setupRecentSection(View view) {
        recentSection = view.findViewById(R.id.home_recent_section);
        RecyclerView recyclerView = view.findViewById(R.id.home_recent_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        recyclerView.setHasFixedSize(true);
        recyclerView.setItemViewCacheSize(10);

        recentAdapter = new RecentTrackAdapter((track, position) -> {
            MusicPlayerManager.getInstance().setQueue(recentAdapter.getTracks());
            MusicPlayerManager.getInstance().playTrack(track);
            Toast.makeText(getContext(), "Đang phát: " + track.getTitle(), Toast.LENGTH_SHORT).show();
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).showNowPlayingTab();
            }
        });
        recyclerView.setAdapter(recentAdapter);

        View btnClear = view.findViewById(R.id.home_recent_clear);
        if (btnClear != null) {
            btnClear.setOnClickListener(v -> {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Xóa lịch sử nghe")
                        .setMessage("Bạn có chắc chắn muốn xóa toàn bộ danh sách bài hát đã nghe gần đây không?")
                        .setPositiveButton("Xóa", (dialog, which) -> {
                            HistoryManager.getInstance(requireContext()).clearHistory();
                            Toast.makeText(getContext(), "Đã xóa lịch sử nghe nhạc", Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("Hủy", null)
                        .show();
            });
        }

        HistoryManager.getInstance(requireContext()).addListener(historyListener);
        updateRecentSection(HistoryManager.getInstance(requireContext()).getRecentTracks());
    }

    private void updateRecentSection(List<Track> tracks) {
        if (recentSection == null || recentAdapter == null) return;
        if (tracks == null || tracks.isEmpty()) {
            recentSection.setVisibility(View.GONE);
        } else {
            recentSection.setVisibility(View.VISIBLE);
            recentAdapter.submitList(tracks);
        }
    }

    // ========== PLAYLIST SECTION — Curated playlists ==========

    private void setupPlaylistSection(View view) {
        RecyclerView recyclerView = view.findViewById(R.id.playlist_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        recyclerView.setHasFixedSize(true);
        recyclerView.setItemViewCacheSize(10);
        if (recyclerView.getOnFlingListener() == null) {
            new LinearSnapHelper().attachToRecyclerView(recyclerView);
        }

        playlistAdapter = new PlaylistCardAdapter(playlist -> {
            PlaylistDialogHelper.showCuratedPlaylistDetailDialog(requireContext(), playlist);
        });
        recyclerView.setAdapter(playlistAdapter);

        initCuratedPool();
        refreshCuratedPlaylists();

        View btnRefresh = view.findViewById(R.id.home_suggestions_see_all);
        if (btnRefresh != null) {
            btnRefresh.setOnClickListener(v -> {
                MusicApiService.invalidateKeyPrefix("playlist_");
                Toast.makeText(getContext(), "Đang làm mới playlist đề xuất...", Toast.LENGTH_SHORT).show();
                refreshCuratedPlaylists();
            });
        }
    }

    private void initCuratedPool() {
        if (!curatedPool.isEmpty()) return;
        curatedPool.add(new PlaylistCardAdapter.PlaylistItem(
                "Acoustic Morning", "Khởi đầu ngày mới", R.drawable.bg_gradient_acoustic, "acoustic morning guitar",
                "https://images.unsplash.com/photo-1510915361894-db8b60106cb1?w=500&q=80"));
        curatedPool.add(new PlaylistCardAdapter.PlaylistItem(
                "Hip-Hop Vibes", "Flow cuốn hút", R.drawable.bg_gradient_hiphop, "hiphop rap vibes",
                "https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?w=500&q=80"));
        curatedPool.add(new PlaylistCardAdapter.PlaylistItem(
                "Lofi Chill Night", "Nhẹ nhàng thư giãn", R.drawable.bg_gradient_chill, "lofi chill night",
                "https://images.unsplash.com/photo-1518609878373-06d740f60d8b?w=500&q=80"));
        curatedPool.add(new PlaylistCardAdapter.PlaylistItem(
                "EDM Party Mix", "Bùng nổ năng lượng", R.drawable.bg_gradient_suggest2, "edm party mix",
                "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=500&q=80"));
        curatedPool.add(new PlaylistCardAdapter.PlaylistItem(
                "V-Pop Giai Điệu", "Những bản hit cảm xúc", R.drawable.bg_gradient_vpop, "vpop nhac tre viet nam",
                "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=500&q=80"));
        curatedPool.add(new PlaylistCardAdapter.PlaylistItem(
                "Indie Mộc Mạc", "Giai điệu tự do", R.drawable.bg_gradient_indie, "indie viet chill",
                "https://images.unsplash.com/photo-1459749411175-04bf5292ceea?w=500&q=80"));
        curatedPool.add(new PlaylistCardAdapter.PlaylistItem(
                "Cafe Sáng Chill", "Thư thái bên tách cafe", R.drawable.bg_gradient_suggest3, "jazz cafe acoustic",
                "https://images.unsplash.com/photo-1511192336575-5a79af67a629?w=500&q=80"));
        curatedPool.add(new PlaylistCardAdapter.PlaylistItem(
                "Workout Energy", "Bứt phá giới hạn", R.drawable.bg_gradient_suggest4, "workout electronic",
                "https://images.unsplash.com/photo-1571266028243-3716f02d2d2e?w=500&q=80"));
    }

    private void refreshCuratedPlaylists() {
        initCuratedPool();
        java.util.Collections.shuffle(curatedPool);
        playlistItems.clear();
        for (int i = 0; i < Math.min(4, curatedPool.size()); i++) {
            PlaylistCardAdapter.PlaylistItem orig = curatedPool.get(i);
            // Create fresh instance so tracks can reload cleanly
            PlaylistCardAdapter.PlaylistItem fresh = new PlaylistCardAdapter.PlaylistItem(
                    orig.title, orig.subtitle, orig.gradientResId, orig.keyword, orig.getCoverUrl());
            playlistItems.add(fresh);
        }
        playlistAdapter.submitList(new ArrayList<>(playlistItems));

        // Load tracks asynchronously for each item
        for (int i = 0; i < playlistItems.size(); i++) {
            final int index = i;
            PlaylistCardAdapter.PlaylistItem item = playlistItems.get(i);
            apiService.getPlaylistByKeyword(item.keyword, 15, new MusicApiService.MusicCallback() {
                @Override
                public void onSuccess(List<Track> tracks) {
                    if (getActivity() == null) return;
                    item.setTracks(tracks);
                    playlistAdapter.notifyPlaylistUpdated(index);
                }

                @Override
                public void onError(Exception e) {}
            });
        }
    }

    // ========== RECOMMENDED SECTION ==========

    private void setupRecommendedSection(View view) {
        RecyclerView recyclerView = view.findViewById(R.id.recommended_recycler_view);
        ProgressBar loading = view.findViewById(R.id.recommended_loading);
        
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        recyclerView.setHasFixedSize(true);
        recyclerView.setItemViewCacheSize(10);
        if (recyclerView.getOnFlingListener() == null) {
            new PagerSnapHelper().attachToRecyclerView(recyclerView);
        }
        recommendedAdapter = new TrackAdapter((track, position) -> {
            MusicPlayerManager.getInstance().setQueue(recommendedAdapter.getTracks());
            MusicPlayerManager.getInstance().playTrack(track);
            Toast.makeText(getContext(), "Đang phát: " + track.getTitle(), Toast.LENGTH_SHORT).show();
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).showNowPlayingTab();
            }
        }, false);
        recyclerView.setAdapter(recommendedAdapter);

        loadRecommended(loading);

        view.findViewById(R.id.recommended_refresh).setOnClickListener(v -> {
            MusicApiService.invalidateKeyPrefix("recommended_tracks");
            loadRecommended(loading);
        });
    }

    private void loadRecommended(ProgressBar loading) {
        loading.setVisibility(View.VISIBLE);
        apiService.getRecommendedTracks(new MusicApiService.MusicCallback() {
            @Override
            public void onSuccess(List<Track> tracks) {
                if (getActivity() == null) return;
                loading.setVisibility(View.GONE);
                recommendedAdapter.submitList(tracks);
            }

            @Override
            public void onError(Exception e) {
                if (getActivity() == null) return;
                loading.setVisibility(View.GONE);
            }
        });
    }

    // ========== THEME TOGGLE ==========



    // ========== TRENDING MUSIC ==========

    private void fetchTrendingMusic(View root) {
        ProgressBar loading = root.findViewById(R.id.trending_loading);
        RecyclerView recyclerView = root.findViewById(R.id.trending_recycler_view);
        
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setHasFixedSize(true);
        recyclerView.setItemViewCacheSize(20);

        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView rv, int newState) {
                if (getContext() == null) return;
                if (newState == RecyclerView.SCROLL_STATE_SETTLING) {
                    com.bumptech.glide.Glide.with(getContext()).pauseRequests();
                } else if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    com.bumptech.glide.Glide.with(getContext()).resumeRequests();
                }
            }
        });

        trendingAdapter = new TrackAdapter((track, position) -> {
            MusicPlayerManager.getInstance().setQueue(trendingAdapter.getTracks());
            MusicPlayerManager.getInstance().playTrack(track);
            Toast.makeText(getContext(), "Đang phát: " + track.getTitle(), Toast.LENGTH_SHORT).show();
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).showNowPlayingTab();
            }
        });
        recyclerView.setAdapter(trendingAdapter);
        
        apiService.getTrendingTracks(new MusicApiService.MusicCallback() {
            @Override
            public void onSuccess(List<Track> tracks) {
                if (getActivity() == null || recyclerView == null) return;
                loading.setVisibility(View.GONE);
                trendingAdapter.submitList(tracks);
            }

            @Override
            public void onError(Exception e) {
                if (getActivity() == null || loading == null) return;
                loading.setVisibility(View.GONE);
                Toast.makeText(getContext(), "Lỗi tải nhạc: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupHeaderGreeting(View view) {
        headerProfile = view.findViewById(R.id.home_header_profile);
        ivAvatarImg = view.findViewById(R.id.home_avatar_img);
        tvAvatarText = view.findViewById(R.id.home_avatar_text);
        vAvatarStatusDot = view.findViewById(R.id.home_avatar_status_dot);
        tvGreetingPrefix = view.findViewById(R.id.home_tv_greeting_prefix);
        tvGreetingName = view.findViewById(R.id.home_tv_greeting_name);
        tvActionBadge = view.findViewById(R.id.home_tv_action_badge);

        updateGreetingUI();

        View.OnClickListener openAuthListener = v -> {
            if (AuthManager.getInstance().isLoggedIn()) {
                AuthDialogHelper.showUserProfileDialog(requireContext(), this::updateGreetingUI);
            } else {
                AuthDialogHelper.showAuthDialog(requireContext(), this::updateGreetingUI);
            }
        };

        if (headerProfile != null) {
            UiAnimationHelper.applyTouchBounce(headerProfile);
            headerProfile.setOnClickListener(openAuthListener);
        }

        AuthManager.getInstance().addAuthStateListener(user -> {
            if (isAdded()) {
                updateGreetingUI();
            }
        });
    }

    private String getTimeOfDayGreeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour >= 5 && hour < 12) {
            return "Chào buổi sáng ☀️";
        } else if (hour >= 12 && hour < 18) {
            return "Chào buổi chiều 🌤️";
        } else if (hour >= 18 && hour < 23) {
            return "Chào buổi tối 🌙";
        } else {
            return "Đêm muộn rồi 🎧";
        }
    }

    private void updateGreetingUI() {
        if (!isAdded() || tvGreetingName == null) return;
        boolean loggedIn = AuthManager.getInstance().isLoggedIn();
        if (loggedIn) {
            String name = AuthManager.getInstance().getDisplayName();
            if (tvGreetingPrefix != null) {
                tvGreetingPrefix.setText(getTimeOfDayGreeting());
            }
            tvGreetingName.setText(name);

            if (tvActionBadge != null) {
                tvActionBadge.setVisibility(View.VISIBLE);
                tvActionBadge.setText("Đã đồng bộ ✓");
                tvActionBadge.setBackgroundResource(R.drawable.bg_badge_synced);
                tvActionBadge.setTextColor(0xFF34D399);
            }

            FirebaseUser user = AuthManager.getInstance().getCurrentUser();
            Uri photoUrl = (user != null) ? user.getPhotoUrl() : null;

            if (photoUrl != null && ivAvatarImg != null) {
                ivAvatarImg.setVisibility(View.VISIBLE);
                if (tvAvatarText != null) tvAvatarText.setVisibility(View.GONE);
                Glide.with(this)
                        .load(photoUrl)
                        .circleCrop()
                        .into(ivAvatarImg);
            } else if (tvAvatarText != null) {
                if (ivAvatarImg != null) ivAvatarImg.setVisibility(View.GONE);
                tvAvatarText.setVisibility(View.VISIBLE);
                String initial = (name != null && !name.trim().isEmpty())
                        ? name.trim().substring(0, 1).toUpperCase()
                        : "U";
                tvAvatarText.setText(initial);
            }

            if (vAvatarStatusDot != null) {
                vAvatarStatusDot.setVisibility(View.VISIBLE);
            }
        } else {
            if (tvGreetingPrefix != null) {
                tvGreetingPrefix.setText(getTimeOfDayGreeting());
            }
            tvGreetingName.setText("Khách");

            if (tvActionBadge != null) {
                tvActionBadge.setVisibility(View.VISIBLE);
                tvActionBadge.setText("Đăng nhập ›");
                tvActionBadge.setBackgroundResource(R.drawable.bg_badge_login);
                tvActionBadge.setTextColor(0xFF38BDF8);
            }

            if (ivAvatarImg != null) {
                ivAvatarImg.setVisibility(View.VISIBLE);
                ivAvatarImg.setImageResource(R.drawable.ic_user_avatar);
                ivAvatarImg.setBackgroundResource(R.drawable.bg_avatar_guest);
                ivAvatarImg.setImageTintList(android.content.res.ColorStateList.valueOf(0xFFA0AEC0));
            }
            if (tvAvatarText != null) {
                tvAvatarText.setVisibility(View.GONE);
            }
            if (vAvatarStatusDot != null) {
                vAvatarStatusDot.setVisibility(View.GONE);
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        updateGreetingUI();
        if (getContext() != null) {
            updateRecentSection(HistoryManager.getInstance(requireContext()).getRecentTracks());
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (getContext() != null) {
            HistoryManager.getInstance(getContext()).removeListener(historyListener);
        }
    }

    // ========== FEATURED / TRENDING ARTISTS ==========

    private void setupFeaturedArtistsSection(View view) {
        RecyclerView recyclerView = view.findViewById(R.id.home_artists_recycler_view);
        if (recyclerView == null) return;

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        recyclerView.setHasFixedSize(true);
        recyclerView.setItemViewCacheSize(12);

        artistAdapter = new ArtistAdapter(track -> {
            String artistName = track.getArtist();
            PlaylistCardAdapter.PlaylistItem artistPlaylist = new PlaylistCardAdapter.PlaylistItem(
                    artistName, "Tuyển tập ca khúc hay nhất của " + artistName,
                    R.drawable.bg_gradient_vpop, "artist:" + artistName, track.getArtworkUrl());
            if (getContext() != null) {
                PlaylistDialogHelper.showCuratedPlaylistDetailDialog(requireContext(), artistPlaylist);
            }
        });
        recyclerView.setAdapter(artistAdapter);

        // Preload curated top Vietnamese stars with official Zing MP3 avatar images
        List<Track> artistList = new ArrayList<>();
        for (String[] a : MusicApiService.TOP_VIETNAMESE_ARTISTS) {
            Track t = new Track();
            t.setArtist(a[0]);
            t.setTitle("Nghệ sĩ " + a[0]);
            t.setArtworkUrl(a[1]);
            artistList.add(t);
        }
        artistAdapter.submitList(artistList);

        View btnRefresh = view.findViewById(R.id.home_artists_refresh);
        if (btnRefresh != null) {
            UiAnimationHelper.applyTouchBounce(btnRefresh);
            btnRefresh.setOnClickListener(v -> {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).showExploreTab();
                }
            });
        }
    }

    // ========== CHỦ ĐỀ & THỂ LOẠI HOT ==========

    private void setupHotGenresSection(View view) {
        RecyclerView recyclerView = view.findViewById(R.id.home_genres_recycler_view);
        if (recyclerView == null) return;

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        recyclerView.setHasFixedSize(true);
        recyclerView.setItemViewCacheSize(10);

        hotGenresAdapter = new PlaylistCardAdapter(playlist -> {
            if (getContext() != null) {
                PlaylistDialogHelper.showCuratedPlaylistDetailDialog(requireContext(), playlist);
            }
        });
        recyclerView.setAdapter(hotGenresAdapter);

        hotGenreItems.clear();
        hotGenreItems.add(new PlaylistCardAdapter.PlaylistItem(
                "V-Pop Thịnh Hành", "Bản hit quốc dân", R.drawable.bg_gradient_vpop, "vpop hot hit",
                "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=500&q=80"));
        hotGenreItems.add(new PlaylistCardAdapter.PlaylistItem(
                "Indie & Acoustic", "Mộc mạc sâu lắng", R.drawable.bg_gradient_acoustic, "indie acoustic viet",
                "https://images.unsplash.com/photo-1510915361894-db8b60106cb1?w=500&q=80"));
        hotGenreItems.add(new PlaylistCardAdapter.PlaylistItem(
                "Rap / Hip-Hop Việt", "Beat cháy năng lượng", R.drawable.bg_gradient_hiphop, "rap viet hiphop",
                "https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?w=500&q=80"));
        hotGenreItems.add(new PlaylistCardAdapter.PlaylistItem(
                "Chill Beats & Lofi", "Học tập & Thư giãn", R.drawable.bg_gradient_chill, "chill lofi viet",
                "https://images.unsplash.com/photo-1518609878373-06d740f60d8b?w=500&q=80"));
        hotGenreItems.add(new PlaylistCardAdapter.PlaylistItem(
                "Ballad Tâm Trạng", "Nỗi niềm sâu lắng", R.drawable.bg_gradient_suggest4, "ballad viet tam trang",
                "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=500&q=80"));
        hotGenreItems.add(new PlaylistCardAdapter.PlaylistItem(
                "Pop US-UK Hits", "Giai điệu toàn cầu", R.drawable.bg_gradient_pop, "us uk pop billboard",
                "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=500&q=80"));

        hotGenresAdapter.submitList(new ArrayList<>(hotGenreItems));

        // Asynchronously pre-fetch tracks for each playlist
        for (int i = 0; i < hotGenreItems.size(); i++) {
            final int index = i;
            PlaylistCardAdapter.PlaylistItem item = hotGenreItems.get(i);
            apiService.getPlaylistByKeyword(item.keyword, 15, new MusicApiService.MusicCallback() {
                @Override
                public void onSuccess(List<Track> tracks) {
                    if (getActivity() == null) return;
                    item.setTracks(tracks);
                    hotGenresAdapter.notifyPlaylistUpdated(index);
                }

                @Override
                public void onError(Exception e) {}
            });
        }
    }

    // ========== APP BRANDING FOOTER ==========

    private void setupFooter(View view) {
        View btnExploreMore = view.findViewById(R.id.home_btn_explore_more);
        View footerCard = view.findViewById(R.id.home_footer_card);
        View.OnClickListener exploreAction = v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).showExploreTab();
            }
        };
        if (btnExploreMore != null) {
            UiAnimationHelper.applyTouchBounce(btnExploreMore);
            btnExploreMore.setOnClickListener(exploreAction);
        }
        if (footerCard != null) {
            UiAnimationHelper.applyTouchBounce(footerCard);
            footerCard.setOnClickListener(exploreAction);
        }
    }
}
