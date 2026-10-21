package com.example.muzzicapp;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.muzzicapp.adapter.ArtistAdapter;
import com.example.muzzicapp.adapter.PlaylistCardAdapter;
import com.example.muzzicapp.adapter.TrackAdapter;
import com.example.muzzicapp.api.MusicApiService;
import com.example.muzzicapp.model.Track;

import java.util.ArrayList;
import java.util.List;

public class ExploreFragment extends Fragment {

    private final MusicApiService apiService = new MusicApiService();
    private TrackAdapter searchResultsAdapter;
    private RecyclerView searchResultsRecycler;
    private View searchResultsContainer;
    private View mainContent;
    private androidx.core.widget.NestedScrollView nestedScrollView;
    private EditText searchInput;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_explore, container, false);
        nestedScrollView = view.findViewById(R.id.explore_scroll_view);

        // Click on the top "Khám phá" title resets to the main explore screen
        View titleHeader = view.findViewById(R.id.explore_title_header);
        if (titleHeader != null) {
            UiAnimationHelper.applyTouchBounce(titleHeader);
            titleHeader.setOnClickListener(v -> {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                resetToMainScreen();
            });
        }

        setupSearch(view);
        setupGenreCards(view);
        fetchFeaturedArtists(view);
        setupExplorePlaylistSection(view);
        return view;
    }

    // ========== SEARCH — Real Audius search ==========

    private void setupSearch(View view) {
        this.searchInput = view.findViewById(R.id.explore_search_input);
        EditText searchInput = this.searchInput;
        searchResultsContainer = view.findViewById(R.id.search_results_container);
        mainContent = view.findViewById(R.id.explore_main_content);
        searchResultsRecycler = view.findViewById(R.id.search_results_recycler);
        ProgressBar searchLoading = view.findViewById(R.id.search_loading);

        searchResultsRecycler.setLayoutManager(new LinearLayoutManager(getContext()));
        searchResultsRecycler.setHasFixedSize(true);
        searchResultsRecycler.setItemViewCacheSize(20);

        searchResultsRecycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
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

        searchResultsAdapter = new TrackAdapter((track, position) -> {
            // Dismiss soft keyboard
            if (getActivity() != null) {
                View focus = getActivity().getCurrentFocus();
                if (focus != null) {
                    android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager)
                            requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                    if (imm != null) imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
                }
            }
            MusicPlayerManager.getInstance().setQueue(searchResultsAdapter.getTracks());
            MusicPlayerManager.getInstance().playTrack(track);
            Toast.makeText(getContext(), "Đang phát: " + track.getTitle(), Toast.LENGTH_SHORT).show();
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).showNowPlayingTab();
            }
        });
        searchResultsRecycler.setAdapter(searchResultsAdapter);

        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || 
               (event != null && event.getAction() == android.view.KeyEvent.ACTION_DOWN && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER)) {
                String query = searchInput.getText().toString().trim();
                if (!query.isEmpty()) {
                    performSearch(query, searchLoading);
                }
                // Hide keyboard
                android.view.inputmethod.InputMethodManager imm = 
                    (android.view.inputmethod.InputMethodManager) requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
                return true;
            }
            return false;
        });

        final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
        final Runnable[] workRunnable = {null};

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (workRunnable[0] != null) {
                    handler.removeCallbacks(workRunnable[0]);
                }
                
                String query = s.toString().trim();
                if (query.isEmpty()) {
                    showMainContent();
                } else {
                    workRunnable[0] = () -> performSearch(query, searchLoading);
                    handler.postDelayed(workRunnable[0], 450); // Fast 450ms debounce
                }
            }
        });
    }

    private void performSearch(String query, ProgressBar loading) {
        mainContent.setVisibility(View.GONE);
        searchResultsContainer.setVisibility(View.VISIBLE);
        loading.setVisibility(View.VISIBLE);

        apiService.searchTracks(query, new MusicApiService.MusicCallback() {
            @Override
            public void onSuccess(List<Track> tracks) {
                if (getActivity() == null) return;
                loading.setVisibility(View.GONE);
                searchResultsAdapter.submitList(tracks);
            }

            @Override
            public void onError(Exception e) {
                if (getActivity() == null) return;
                loading.setVisibility(View.GONE);
                Toast.makeText(getContext(), "Không tìm thấy: " + query, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showMainContent() {
        if (searchResultsContainer != null) searchResultsContainer.setVisibility(View.GONE);
        if (mainContent != null) mainContent.setVisibility(View.VISIBLE);
    }

    /**
     * Resets ExploreFragment to its default root screen:
     * - Clears search query and hides the soft keyboard
     * - Restores main explore content (genres, artists, playlists)
     * - Smoothly scrolls to the top of the explore screen
     */
    public void resetToMainScreen() {
        if (!isAdded() || getView() == null) return;

        // 1. Clear search input if present
        if (searchInput != null) {
            searchInput.setText("");
            searchInput.clearFocus();
        }

        // 2. Hide virtual keyboard
        if (getActivity() != null) {
            View focus = getActivity().getCurrentFocus();
            if (focus != null) {
                android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager)
                        requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
            }
        }

        // 3. Hide search results and reveal main content
        showMainContent();

        // 4. Smooth scroll back to top of page
        if (nestedScrollView != null) {
            nestedScrollView.smoothScrollTo(0, 0);
        }
    }

    // ========== GENRE CARDS — Load music by genre and show detailed playlist ==========

    // ========== GENRE CARDS — Load music by genre and show detailed playlist ==========

    private void setupGenreCards(View view) {
        String[][] genres = {
                {"V-Pop Hits", "vpop nhac tre viet nam"},
                {"Indie Việt", "indie viet chill"},
                {"Hip-Hop Việt", "rap viet hiphop"},
                {"Pop Global", "us uk pop billboard"},
                {"Chill Beats", "lofi chill beats thu gian"},
                {"Acoustic", "acoustic guitar moc"}
        };
        int[] genreCardIds = {
                R.id.genre_vpop, R.id.genre_indie,
                R.id.genre_hiphop, R.id.genre_pop,
                R.id.genre_chill, R.id.genre_acoustic
        };
        int[] genreGradients = {
                R.drawable.bg_gradient_vpop, R.drawable.bg_gradient_indie,
                R.drawable.bg_gradient_hiphop, R.drawable.bg_gradient_pop,
                R.drawable.bg_gradient_chill, R.drawable.bg_gradient_acoustic
        };
        String[] genreCovers = {
                "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=500&q=80",
                "https://images.unsplash.com/photo-1459749411175-04bf5292ceea?w=500&q=80",
                "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=500&q=80",
                "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=500&q=80",
                "https://images.unsplash.com/photo-1518609878373-06d740f60d8b?w=500&q=80",
                "https://images.unsplash.com/photo-1510915361894-db8b60106cb1?w=500&q=80"
        };

        // Wire "Ngẫu nhiên" button
        View btnGenreRandom = view.findViewById(R.id.explore_genres_refresh);
        if (btnGenreRandom != null) {
            UiAnimationHelper.applyTouchBounce(btnGenreRandom);
            btnGenreRandom.setOnClickListener(v -> {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                int randomIndex = new java.util.Random().nextInt(genres.length);
                String genreName = genres[randomIndex][0];
                String genreKeyword = genres[randomIndex][1];
                int gradientRes = genreGradients[randomIndex];
                String coverUrl = genreCovers[randomIndex];

                Toast.makeText(getContext(), "Đã chọn ngẫu nhiên: " + genreName, Toast.LENGTH_SHORT).show();
                PlaylistCardAdapter.PlaylistItem randomPlaylist = new PlaylistCardAdapter.PlaylistItem(
                        genreName, "Tuyển tập " + genreName + " ngẫu nhiên", gradientRes, genreKeyword, coverUrl);
                v.postDelayed(() -> {
                    if (isAdded() && getContext() != null) {
                        PlaylistDialogHelper.showCuratedPlaylistDetailDialog(requireContext(), randomPlaylist);
                    }
                }, 100);
            });
        }

        for (int i = 0; i < genreCardIds.length; i++) {
            View card = view.findViewById(genreCardIds[i]);
            if (card != null) {
                final String genreName = genres[i][0];
                final String genreKeyword = genres[i][1];
                final int gradientRes = genreGradients[i];
                final String coverUrl = genreCovers[i];

                UiAnimationHelper.applyTouchBounce(card);
                card.setOnClickListener(v -> {
                    v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                    PlaylistCardAdapter.PlaylistItem genrePlaylist = new PlaylistCardAdapter.PlaylistItem(
                            genreName, "Tuyển tập " + genreName + " chọn lọc", gradientRes, genreKeyword, coverUrl);
                    v.postDelayed(() -> {
                        if (isAdded() && getContext() != null) {
                            PlaylistDialogHelper.showCuratedPlaylistDetailDialog(requireContext(), genrePlaylist);
                        }
                    }, 100);
                });
            }
        }
    }

    // ========== FEATURED ARTISTS ==========

    private void fetchFeaturedArtists(View root) {
        ProgressBar loading = root.findViewById(R.id.explore_loading);
        RecyclerView recyclerView = root.findViewById(R.id.explore_artists_recycler_view);
        
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        recyclerView.setHasFixedSize(true);
        recyclerView.setItemViewCacheSize(10);
        ArtistAdapter adapter = new ArtistAdapter(track -> {
            String artistName = track.getArtist();
            com.example.muzzicapp.adapter.PlaylistCardAdapter.PlaylistItem artistPlaylist =
                    new com.example.muzzicapp.adapter.PlaylistCardAdapter.PlaylistItem(
                            artistName, "Tuyển tập ca khúc hay nhất của " + artistName,
                            R.drawable.bg_gradient_vpop, "artist:" + artistName, track.getArtworkUrl());
            if (getContext() != null) {
                PlaylistDialogHelper.showCuratedPlaylistDetailDialog(requireContext(), artistPlaylist);
            }
        });
        recyclerView.setAdapter(adapter);
        
        apiService.getFeaturedArtists(new MusicApiService.MusicCallback() {
            @Override
            public void onSuccess(List<Track> tracks) {
                if (getActivity() == null || recyclerView == null) return;
                loading.setVisibility(View.GONE);
                adapter.submitList(tracks);
            }

            @Override
            public void onError(Exception e) {
                if (getActivity() == null || loading == null) return;
                loading.setVisibility(View.GONE);
                Toast.makeText(getContext(), "Lỗi tải nghệ sĩ: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ========== EXPLORE PLAYLISTS — Popular Playlists with Refresh ==========

    private PlaylistCardAdapter popularAdapter;
    private final List<PlaylistCardAdapter.PlaylistItem> popularPool = new ArrayList<>();
    private final List<PlaylistCardAdapter.PlaylistItem> currentPopularItems = new ArrayList<>();

    private void setupExplorePlaylistSection(View view) {
        RecyclerView recyclerView = view.findViewById(R.id.explore_playlists_recycler);
        if (recyclerView == null) return;
        
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        recyclerView.setHasFixedSize(true);
        recyclerView.setItemViewCacheSize(10);
        
        popularAdapter = new PlaylistCardAdapter(playlist -> {
            PlaylistDialogHelper.showCuratedPlaylistDetailDialog(requireContext(), playlist);
        });
        recyclerView.setAdapter(popularAdapter);

        initPopularPool();
        refreshPopularPlaylists();

        View btnRefresh = view.findViewById(R.id.explore_playlists_refresh);
        if (btnRefresh != null) {
            btnRefresh.setOnClickListener(v -> {
                MusicApiService.invalidateKeyPrefix("playlist_");
                Toast.makeText(getContext(), "Đang làm mới Playlist phổ biến...", Toast.LENGTH_SHORT).show();
                refreshPopularPlaylists();
            });
        }
    }

    private void initPopularPool() {
        if (!popularPool.isEmpty()) return;
        popularPool.add(new PlaylistCardAdapter.PlaylistItem(
                "Deep House", "Bass sâu lắng", R.drawable.bg_gradient_suggest2, "deep house electronic",
                "https://images.unsplash.com/photo-1571266028243-3716f02d2d2e?w=500&q=80"));
        popularPool.add(new PlaylistCardAdapter.PlaylistItem(
                "R&B Soul", "Tâm hồn bay bổng", R.drawable.bg_gradient_suggest1, "rnb soul smooth",
                "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=500&q=80"));
        popularPool.add(new PlaylistCardAdapter.PlaylistItem(
                "Jazz Cafe", "Thư thái tao nhã", R.drawable.bg_gradient_suggest3, "jazz cafe relaxing",
                "https://images.unsplash.com/photo-1511192336575-5a79af67a629?w=500&q=80"));
        popularPool.add(new PlaylistCardAdapter.PlaylistItem(
                "Rock Classic", "Năng lượng bất diệt", R.drawable.bg_gradient_suggest4, "rock classic guitar",
                "https://images.unsplash.com/photo-1498038432885-c6f3f1b912ee?w=500&q=80"));
        popularPool.add(new PlaylistCardAdapter.PlaylistItem(
                "Night Synth", "Ánh đèn đô thị", R.drawable.bg_gradient_hiphop, "electronic synthwave",
                "https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?w=500&q=80"));
        popularPool.add(new PlaylistCardAdapter.PlaylistItem(
                "Chillhop Study", "Tập trung làm việc", R.drawable.bg_gradient_chill, "lofi study chill",
                "https://images.unsplash.com/photo-1518609878373-06d740f60d8b?w=500&q=80"));
        popularPool.add(new PlaylistCardAdapter.PlaylistItem(
                "Acoustic Sunset", "Hoàng hôn nhẹ trôi", R.drawable.bg_gradient_acoustic, "acoustic sunset guitar",
                "https://images.unsplash.com/photo-1510915361894-db8b60106cb1?w=500&q=80"));
    }

    private void refreshPopularPlaylists() {
        initPopularPool();
        java.util.Collections.shuffle(popularPool);
        currentPopularItems.clear();
        for (int i = 0; i < Math.min(4, popularPool.size()); i++) {
            PlaylistCardAdapter.PlaylistItem orig = popularPool.get(i);
            PlaylistCardAdapter.PlaylistItem fresh = new PlaylistCardAdapter.PlaylistItem(
                    orig.title, orig.subtitle, orig.gradientResId, orig.keyword, orig.getCoverUrl());
            currentPopularItems.add(fresh);
        }
        popularAdapter.submitList(new ArrayList<>(currentPopularItems));

        // Load tracks asynchronously for each playlist
        for (int i = 0; i < currentPopularItems.size(); i++) {
            final int index = i;
            PlaylistCardAdapter.PlaylistItem item = currentPopularItems.get(i);
            apiService.getPlaylistByKeyword(item.keyword, 15, new MusicApiService.MusicCallback() {
                @Override
                public void onSuccess(List<Track> tracks) {
                    if (getActivity() == null) return;
                    item.setTracks(tracks);
                    popularAdapter.notifyPlaylistUpdated(index);
                }

                @Override
                public void onError(Exception e) {}
            });
        }
    }

}
