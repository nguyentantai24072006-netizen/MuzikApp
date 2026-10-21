package com.example.muzzicapp;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.muzzicapp.adapter.PlaylistChoiceAdapter;
import com.example.muzzicapp.adapter.RecordingAdapter;
import com.example.muzzicapp.adapter.TrackAdapter;
import com.example.muzzicapp.api.MusicApiService;
import com.example.muzzicapp.auth.AuthDialogHelper;
import com.example.muzzicapp.auth.AuthManager;
import com.example.muzzicapp.model.RecordingItem;
import com.example.muzzicapp.model.Track;

import java.util.ArrayList;
import java.util.List;

public class LibraryFragment extends Fragment implements PlaylistManager.PlaylistListener, RecordingManager.RecordingListener {

    private TrackAdapter trackAdapter;
    private RecordingAdapter recordingAdapter;
    private RecyclerView recyclerView;
    private String currentFilter = "all";
    private final MusicApiService apiService = new MusicApiService();
    private TextView[] filterChips;
    private TextView btnUser;
    private View bannerGuest;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_library, container, false);
        PlaylistManager.getInstance(requireContext()).addListener(this);
        RecordingManager.getInstance(requireContext()).addListener(this);
        setupUserUI(view);
        setupFilterChips(view);
        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (getContext() != null) {
            PlaylistManager.getInstance(getContext()).removeListener(this);
            RecordingManager.getInstance(getContext()).removeListener(this);
        }
        if (recordingAdapter != null) {
            recordingAdapter.release();
            recordingAdapter = null;
        }
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) {
            if ("playlist".equals(currentFilter)) {
                loadPlaylists();
            } else if ("recording".equals(currentFilter)) {
                loadRecordings();
            } else if ("recent".equals(currentFilter)) {
                loadRecentHistory();
            }
        }
    }

    @Override
    public void onPlaylistsChanged() {
        if ("playlist".equals(currentFilter) && getActivity() != null) {
            getActivity().runOnUiThread(this::loadPlaylists);
        }
    }

    @Override
    public void onRecordingsChanged() {
        if ("recording".equals(currentFilter) && getActivity() != null) {
            getActivity().runOnUiThread(this::loadRecordings);
        }
    }

    private void setupFilterChips(View view) {
        TextView filterAll = view.findViewById(R.id.filter_all);
        TextView filterPlaylist = view.findViewById(R.id.filter_playlist);
        TextView filterRecent = view.findViewById(R.id.filter_recent);
        TextView filterArtist = view.findViewById(R.id.filter_artist);
        TextView filterAlbum = view.findViewById(R.id.filter_album);
        TextView filterRecording = view.findViewById(R.id.filter_recording);

        filterChips = new TextView[]{filterAll, filterPlaylist, filterRecent, filterArtist, filterAlbum, filterRecording};

        recyclerView = view.findViewById(R.id.library_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setHasFixedSize(true);
        recyclerView.setItemViewCacheSize(20);

        trackAdapter = new TrackAdapter((track, position) -> {
            MusicPlayerManager.getInstance().setQueue(trackAdapter.getTracks());
            MusicPlayerManager.getInstance().playTrack(track);
            Toast.makeText(getContext(), "Đang phát: " + track.getTitle(), Toast.LENGTH_SHORT).show();
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).showNowPlayingTab();
            }
        });

        View.OnClickListener chipClickListener = v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
            UiAnimationHelper.animateChipSelect(v);
            if (recordingAdapter != null) {
                recordingAdapter.release();
            }
            for (TextView chip : filterChips) {
                if (chip != null) {
                    chip.setBackgroundResource(R.drawable.bg_chip_light);
                    chip.setTextColor(getResources().getColor(R.color.text_primary_adaptive, null));
                }
            }
            ((TextView) v).setBackgroundResource(R.drawable.bg_chip_light_selected);
            ((TextView) v).setTextColor(getResources().getColor(R.color.white, null));

            if (v == filterAll) {
                currentFilter = "all";
                recyclerView.setAdapter(trackAdapter);
                loadLibrary();
            } else if (v == filterPlaylist) {
                currentFilter = "playlist";
                if (!AuthManager.getInstance().isLoggedIn()) {
                    AuthDialogHelper.showGuestPrompt(requireContext(),
                            "Vui lòng đăng nhập để xem và quản lý danh sách phát của bạn.",
                            this::loadPlaylists);
                }
                loadPlaylists();
            } else if (v == filterRecent) {
                currentFilter = "recent";
                recyclerView.setAdapter(trackAdapter);
                loadRecentHistory();
            } else if (v == filterArtist) {
                currentFilter = "artist";
                recyclerView.setAdapter(trackAdapter);
                loadArtists();
            } else if (v == filterAlbum) {
                currentFilter = "album";
                recyclerView.setAdapter(trackAdapter);
                loadGenre("vpop");
            } else if (v == filterRecording) {
                currentFilter = "recording";
                loadRecordings();
            }
        };

        for (TextView chip : filterChips) {
            if (chip != null) {
                chip.setOnClickListener(chipClickListener);
            }
        }

        View libSearch = view.findViewById(R.id.library_search);
        if (libSearch != null) {
            UiAnimationHelper.applyTouchBounce(libSearch);
            libSearch.setOnClickListener(v -> {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).showHomeTab();
                }
            });
        }

        View libAdd = view.findViewById(R.id.library_add);
        if (libAdd != null) {
            UiAnimationHelper.applyTouchBounce(libAdd);
            libAdd.setOnClickListener(v -> {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                if (!AuthManager.getInstance().isLoggedIn()) {
                    AuthDialogHelper.showGuestPrompt(requireContext(),
                            "Vui lòng đăng nhập để tạo playlist và tự động đồng bộ trên mọi thiết bị.",
                            this::showCreatePlaylistDialog);
                    return;
                }
                showCreatePlaylistDialog();
            });
        }

        // Default display
        recyclerView.setAdapter(trackAdapter);
        loadLibrary();
    }

    private void setupUserUI(View view) {
        btnUser = view.findViewById(R.id.library_btn_user);
        bannerGuest = view.findViewById(R.id.library_banner_guest);

        updateUserUI();

        if (btnUser != null) {
            btnUser.setOnClickListener(v -> {
                if (AuthManager.getInstance().isLoggedIn()) {
                    AuthDialogHelper.showUserProfileDialog(requireContext(), this::updateUserUI);
                } else {
                    AuthDialogHelper.showAuthDialog(requireContext(), this::updateUserUI);
                }
            });
        }

        if (bannerGuest != null) {
            bannerGuest.setOnClickListener(v -> {
                AuthDialogHelper.showAuthDialog(requireContext(), this::updateUserUI);
            });
        }

        AuthManager.getInstance().addAuthStateListener(user -> {
            if (isAdded()) {
                updateUserUI();
                if ("playlist".equals(currentFilter)) {
                    loadPlaylists();
                }
            }
        });
    }

    private void updateUserUI() {
        if (!isAdded()) return;
        boolean loggedIn = AuthManager.getInstance().isLoggedIn();
        if (bannerGuest != null) {
            bannerGuest.setVisibility(loggedIn ? View.GONE : View.VISIBLE);
        }
        if (btnUser != null) {
            if (loggedIn) {
                String name = AuthManager.getInstance().getDisplayName();
                btnUser.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);
                if (name != null && !name.isEmpty()) {
                    btnUser.setText(name.substring(0, 1).toUpperCase());
                } else {
                    btnUser.setText("✓");
                }
                btnUser.setBackgroundResource(R.drawable.bg_avatar_user);
                btnUser.setBackgroundTintList(null);
            } else {
                btnUser.setText("");
                btnUser.setBackgroundResource(R.drawable.bg_avatar_guest);
                btnUser.setBackgroundTintList(null);
                android.graphics.drawable.Drawable d = androidx.core.content.ContextCompat.getDrawable(requireContext(), R.drawable.ic_user_avatar);
                if (d != null) {
                    d = d.mutate();
                    d.setTint(0xFFA0AEC0);
                    btnUser.setCompoundDrawablesWithIntrinsicBounds(d, null, null, null);
                }
            }
        }
    }

    private void loadPlaylists() {
        if (getContext() == null) return;
        if (!AuthManager.getInstance().isLoggedIn()) {
            recyclerView.setAdapter(new PlaylistChoiceAdapter(new ArrayList<>(), false, null));
            return;
        }
        PlaylistManager pm = PlaylistManager.getInstance(requireContext());
        List<PlaylistManager.UserPlaylist> playlists = pm.getPlaylists();

        PlaylistChoiceAdapter choiceAdapter = new PlaylistChoiceAdapter(playlists, false, playlist -> {
            PlaylistDialogHelper.showPlaylistDetailDialog(requireContext(), playlist, this::loadPlaylists);
        });
        recyclerView.setAdapter(choiceAdapter);
    }

    private void loadRecentHistory() {
        if (getContext() == null) return;
        List<Track> recent = HistoryManager.getInstance(requireContext()).getRecentTracks();
        if (recent.isEmpty()) {
            Toast.makeText(getContext(), "Chưa có bài hát nào trong lịch sử nghe nhạc", Toast.LENGTH_SHORT).show();
        }
        trackAdapter.submitList(recent);
    }

    private void showCreatePlaylistDialog() {
        if (getContext() == null) return;
        PlaylistDialogHelper.showCreatePlaylistDialog(requireContext(), null, () -> {
            if ("playlist".equals(currentFilter)) {
                loadPlaylists();
            }
        });
    }

    private void loadLibrary() {
        if (getContext() == null) return;
        apiService.getRecommendedTracks(new MusicApiService.MusicCallback() {
            @Override
            public void onSuccess(List<Track> tracks) {
                if (getActivity() == null) return;
                trackAdapter.submitList(tracks);
            }

            @Override
            public void onError(Exception e) {
                if (getActivity() == null) return;
                Toast.makeText(getContext(), "Lỗi tải thư viện", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadArtists() {
        if (getContext() == null) return;
        apiService.getFeaturedArtists(new MusicApiService.MusicCallback() {
            @Override
            public void onSuccess(List<Track> tracks) {
                if (getActivity() == null) return;
                trackAdapter.submitList(tracks);
            }

            @Override
            public void onError(Exception e) {
                if (getActivity() == null) return;
                Toast.makeText(getContext(), "Lỗi tải nghệ sĩ", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadGenre(String genre) {
        if (getContext() == null) return;
        apiService.getTrendingByGenre(genre, new MusicApiService.MusicCallback() {
            @Override
            public void onSuccess(List<Track> tracks) {
                if (getActivity() == null) return;
                trackAdapter.submitList(tracks);
            }

            @Override
            public void onError(Exception e) {
                if (getActivity() == null) return;
                Toast.makeText(getContext(), "Lỗi tải album/thể loại", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadRecordings() {
        if (getContext() == null) return;
        List<RecordingItem> list = RecordingManager.getInstance(requireContext()).getRecordings();
        if (recordingAdapter == null) {
            recordingAdapter = new RecordingAdapter(requireContext(), list);
        } else {
            recordingAdapter.submitList(list);
        }
        recyclerView.setAdapter(recordingAdapter);
        if (list.isEmpty()) {
            Toast.makeText(getContext(), "Chưa có bản thu âm nào. Hãy vào phát nhạc và bấm icon Micro để thu âm nhé!", Toast.LENGTH_SHORT).show();
        }
    }

    public void selectRecordingsFilter() {
        if (filterChips != null && filterChips.length > 4 && filterChips[4] != null) {
            filterChips[4].performClick();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if ("playlist".equals(currentFilter)) {
            loadPlaylists();
        } else if ("recording".equals(currentFilter)) {
            loadRecordings();
        }
    }
}
