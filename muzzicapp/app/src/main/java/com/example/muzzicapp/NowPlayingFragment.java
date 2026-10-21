package com.example.muzzicapp;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.HapticFeedbackConstants;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import android.media.MediaPlayer;
import com.example.muzzicapp.KaraokeRecorder;
import com.example.muzzicapp.RecordingManager;
import com.example.muzzicapp.model.RecordingItem;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import android.content.pm.PackageManager;
import android.Manifest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.muzzicapp.adapter.QueueAdapter;
import com.example.muzzicapp.adapter.PlaylistChoiceAdapter;
import com.example.muzzicapp.PlaylistManager;
import com.example.muzzicapp.auth.AuthDialogHelper;
import com.example.muzzicapp.auth.AuthManager;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.example.muzzicapp.R;
import com.example.muzzicapp.api.LyricsApiService;
import com.example.muzzicapp.model.Track;

import android.graphics.Bitmap;
import androidx.palette.graphics.Palette;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;

public class NowPlayingFragment extends Fragment implements MusicPlayerManager.OnPlaybackChangeListener, PlaylistManager.PlaylistListener {

    private boolean isPlaying = false;
    private boolean isShuffle = false;
    private int repeatState = 0; // 0 = off, 1 = all, 2 = one
    private boolean isLyricsVisible = false;
    
    private final List<LyricLine> lyricLines = new ArrayList<>();
    private int currentLyricIndex = -1;
    private int previousActiveLyricIndex = -1;
    private String lyricsRequestKey = "";

    private boolean isUserScrolling = false;
    private long userSongOffsetMs = 0;
    private final Handler lyricsHandler = new Handler(Looper.getMainLooper());
    private final Runnable resumeAutoScrollRunnable = () -> isUserScrolling = false;
    private ObjectAnimator currentScrollAnimator = null;
    private ActivityResultLauncher<String> recordAudioPermissionLauncher;
    private ObjectAnimator recordingDotAnimator = null;

    // Cached views to eliminate heavy 100ms findViewById traversals
    private SeekBar cachedProgressBar = null;
    private TextView cachedTvCurrent = null;
    private TextView cachedTvTotal = null;
    private LinearLayout cachedLyricsContainer = null;
    private ScrollView cachedLyricsScroll = null;
    private int lastRenderedSecond = -1;
    private int[] currentGradientColors = null;
    private android.animation.ValueAnimator dynamicBgAnimator = null;

    // Lyrics mode & swipe-to-collapse UI elements
    private boolean isControlsHiddenInLyrics = false;
    private boolean isUserSeeking = false;
    private final Handler autoHideHandler = new Handler(Looper.getMainLooper());
    private final Runnable autoHideControlsRunnable = this::hideBottomControlsInLyrics;
    private static final long CONTROLS_AUTO_HIDE_DELAY_MS = 1500L;
    private View cachedMainContent = null;
    private View cachedContentFrame = null;
    private View cachedSongInfo = null;
    private View cachedTopBar = null;
    private View cachedLyricsHeader = null;
    private View cachedBottomControls = null;
    private View cachedBottomTapTarget = null;
    private View cachedHintIndicator = null;
    private final Handler bounceHintHandler = new Handler(Looper.getMainLooper());
    private boolean isHintBounceRunning = false;
    private static final long HINT_BOUNCE_INTERVAL_MS = 2800L;
    private final Runnable hintBounceRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isHintBounceRunning || cachedHintIndicator == null) return;
            playGentleArrowBounce();
            bounceHintHandler.postDelayed(this, HINT_BOUNCE_INTERVAL_MS + 1140L);
        }
    };
    private ImageView cachedCompactArt = null;
    private TextView cachedCompactTitle = null;
    private TextView cachedCompactArtist = null;
    private View cachedCompactTrackInfo = null;
    private View cachedLyricsBottomBackdrop = null;
    private android.animation.ValueAnimator lyricsBlurAnimator = null;
    private ImageView cachedBtnLyrics = null;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        recordAudioPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        startKaraokeRecording();
                    } else {
                        Toast.makeText(getContext(), "Cần cấp quyền micro để ghi âm Karaoke", Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_now_playing, container, false);
        setupInteractions(view);
        MusicPlayerManager.getInstance().addListener(this);
        PlaylistManager.getInstance(requireContext()).addListener(this);
        return view;
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        MusicPlayerManager.getInstance().removeListener(this);
        if (getContext() != null) {
            PlaylistManager.getInstance(getContext()).removeListener(this);
        }
        lyricsHandler.removeCallbacks(resumeAutoScrollRunnable);
        cancelControlsAutoHide();
        if (currentScrollAnimator != null) {
            currentScrollAnimator.cancel();
        }
        if (KaraokeRecorder.getInstance().isRecording()) {
            KaraokeRecorder.getInstance().cancelRecording();
        }
        if (recordingDotAnimator != null) {
            recordingDotAnimator.cancel();
            recordingDotAnimator = null;
        }
        cachedProgressBar = null;
        cachedTvCurrent = null;
        cachedTvTotal = null;
        cachedLyricsContainer = null;
        cachedLyricsScroll = null;
        cachedMainContent = null;
        cachedContentFrame = null;
        cachedSongInfo = null;
        cachedTopBar = null;
        cachedLyricsHeader = null;
        cachedBottomControls = null;
        cachedBottomTapTarget = null;
        stopArrowHintBounce();
        cachedHintIndicator = null;
        if (lyricsBlurAnimator != null) {
            lyricsBlurAnimator.cancel();
            lyricsBlurAnimator = null;
        }
        if (cachedLyricsScroll != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            cachedLyricsScroll.setRenderEffect(null);
        }
        if (cachedLyricsContainer != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            for (int i = 0; i < cachedLyricsContainer.getChildCount(); i++) {
                View child = cachedLyricsContainer.getChildAt(i);
                if (child != null) child.setRenderEffect(null);
            }
        }
        cachedLyricsBottomBackdrop = null;
        cachedCompactArt = null;
        cachedCompactTitle = null;
        cachedCompactArtist = null;
        cachedCompactTrackInfo = null;
        cachedBtnLyrics = null;
        lastRenderedSecond = -1;
        if (dynamicBgAnimator != null) {
            dynamicBgAnimator.cancel();
            dynamicBgAnimator = null;
        }
        currentGradientColors = null;
    }

    private void setupInteractions(View view) {
        cachedProgressBar = view.findViewById(R.id.now_playing_progress_bar);
        cachedTvCurrent = view.findViewById(R.id.now_playing_time_current);
        cachedTvTotal = view.findViewById(R.id.now_playing_time_total);
        cachedLyricsContainer = view.findViewById(R.id.now_playing_lyrics_container);
        cachedLyricsScroll = view.findViewById(R.id.now_playing_lyrics_scroll);
        cachedMainContent = view.findViewById(R.id.now_playing_main_content);
        cachedContentFrame = view.findViewById(R.id.now_playing_content_frame);
        cachedSongInfo = view.findViewById(R.id.now_playing_song_info);
        cachedTopBar = view.findViewById(R.id.now_playing_top_bar);
        cachedLyricsHeader = view.findViewById(R.id.now_playing_lyrics_header);
        cachedBottomControls = view.findViewById(R.id.now_playing_bottom_controls_container);
        cachedBottomTapTarget = view.findViewById(R.id.now_playing_bottom_tap_target);
        cachedHintIndicator = view.findViewById(R.id.now_playing_hint_indicator);
        cachedLyricsBottomBackdrop = view.findViewById(R.id.now_playing_lyrics_bottom_blur_backdrop);
        cachedCompactArt = view.findViewById(R.id.now_playing_lyrics_compact_art);
        cachedCompactTitle = view.findViewById(R.id.now_playing_lyrics_compact_title);
        cachedCompactArtist = view.findViewById(R.id.now_playing_lyrics_compact_artist);
        cachedCompactTrackInfo = view.findViewById(R.id.now_playing_lyrics_compact_track_info);
        cachedBtnLyrics = view.findViewById(R.id.now_playing_btn_lyrics);
        lastRenderedSecond = -1;

        // Apply Apple Music dynamic background immediately for current playing track
        Track initialTrack = MusicPlayerManager.getInstance().getCurrentTrack();
        if (initialTrack != null) {
            extractDynamicBackgroundColor(initialTrack);
        } else {
            applyDynamicBackground(getDefaultAppleMusicGradient());
        }

        // Setup mini drag handle to pull player down
        setupDragToCollapse(view);

        // Play/Pause
        FrameLayout btnPlayPause = view.findViewById(R.id.now_playing_btn_play_pause);
        UiAnimationHelper.applyTouchBounce(btnPlayPause);
        btnPlayPause.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
            MusicPlayerManager.getInstance().togglePlayPause();
            if (isLyricsVisible && !isControlsHiddenInLyrics) {
                scheduleControlsAutoHide();
            }
        });

        // Shuffle
        ImageView btnShuffle = view.findViewById(R.id.now_playing_btn_shuffle);
        UiAnimationHelper.applyTouchBounce(btnShuffle);
        updateShuffleButtonUI(btnShuffle);
        btnShuffle.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
            boolean enabled = MusicPlayerManager.getInstance().toggleShuffle();
            updateShuffleButtonUI(btnShuffle);
            Toast.makeText(getContext(), enabled ? "Đã bật phát ngẫu nhiên" : "Đã tắt phát ngẫu nhiên", Toast.LENGTH_SHORT).show();
        });

        // Repeat
        ImageView btnRepeat = view.findViewById(R.id.now_playing_btn_repeat);
        UiAnimationHelper.applyTouchBounce(btnRepeat);
        updateRepeatButtonUI(btnRepeat);
        btnRepeat.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
            int mode = MusicPlayerManager.getInstance().cycleRepeatMode();
            updateRepeatButtonUI(btnRepeat);
            if (mode == 0) {
                Toast.makeText(getContext(), "Tắt lặp lại", Toast.LENGTH_SHORT).show();
            } else if (mode == 1) {
                Toast.makeText(getContext(), "Lặp lại toàn bộ danh sách", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getContext(), "Lặp lại bài hát hiện tại", Toast.LENGTH_SHORT).show();
            }
        });

        // Favorite
        ImageView btnFavorite = view.findViewById(R.id.now_playing_btn_favorite);
        UiAnimationHelper.applyTouchBounce(btnFavorite);
        View burstRing = view.findViewById(R.id.now_playing_heart_burst_ring);
        View burstDots = view.findViewById(R.id.now_playing_heart_burst_dots);
        updateFavoriteButtonUI(MusicPlayerManager.getInstance().getCurrentTrack());

        btnFavorite.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
            if (!AuthManager.getInstance().isLoggedIn()) {
                AuthDialogHelper.showGuestPrompt(requireContext(),
                        "Vui lòng đăng nhập để lưu bài hát yêu thích và tự động đồng bộ trên mọi thiết bị.",
                        () -> toggleFavoriteWithAnimation(btnFavorite, burstRing, burstDots));
                return;
            }
            toggleFavoriteWithAnimation(btnFavorite, burstRing, burstDots);
        });

        // Lyrics View Toggle
        if (cachedBtnLyrics != null) {
            UiAnimationHelper.applyTouchBounce(cachedBtnLyrics);
            cachedBtnLyrics.setOnClickListener(v -> {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                toggleLyricsMode();
            });
        }

        // Click listener to exit lyrics mode and reverse-morph expand back to normal view
        View.OnClickListener exitLyricsClickListener = v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
            exitLyricsMode();
        };

        // Tap on compact cover art or song info in top header to exit lyrics mode and expand back to full player
        if (cachedCompactTrackInfo != null) {
            UiAnimationHelper.applyTouchBounce(cachedCompactTrackInfo);
            cachedCompactTrackInfo.setOnClickListener(exitLyricsClickListener);
        }
        if (cachedCompactArt != null) {
            cachedCompactArt.setOnClickListener(exitLyricsClickListener);
        }
        if (cachedCompactTitle != null) {
            cachedCompactTitle.setOnClickListener(exitLyricsClickListener);
        }
        if (cachedCompactArtist != null) {
            cachedCompactArtist.setOnClickListener(exitLyricsClickListener);
        }

        // Close Lyrics button inside top compact header
        View btnCloseLyrics = view.findViewById(R.id.now_playing_lyrics_btn_close);
        if (btnCloseLyrics != null) {
            UiAnimationHelper.applyTouchBounce(btnCloseLyrics);
            btnCloseLyrics.setOnClickListener(exitLyricsClickListener);
        }

        // Tap on bottom tap target to bring playback controls back
        if (cachedBottomTapTarget != null) {
            cachedBottomTapTarget.setOnClickListener(v -> {
                if (syncPanel != null && syncPanel.getVisibility() == View.VISIBLE) {
                    return;
                }
                v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                showBottomControlsInLyrics();
            });
        }

        // GestureDetector to detect single taps on lyrics to toggle controls without interfering with vertical scrolling
        android.view.GestureDetector lyricsTapDetector = new android.view.GestureDetector(
                getContext(),
                new android.view.GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onSingleTapConfirmed(MotionEvent e) {
                        if (isLyricsVisible) {
                            if (syncPanel != null && syncPanel.getVisibility() == View.VISIBLE) {
                                return false;
                            }
                            if (isControlsHiddenInLyrics) {
                                showBottomControlsInLyrics();
                            } else {
                                hideBottomControlsInLyrics();
                            }
                            return true;
                        }
                        return false;
                    }
                }
        );

        ScrollView lyricsScroll = view.findViewById(R.id.now_playing_lyrics_scroll);
        if (lyricsScroll != null) {
            lyricsScroll.setOnTouchListener((v, event) -> {
                lyricsTapDetector.onTouchEvent(event);
                int action = event.getAction();
                if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                    isUserScrolling = true;
                    lyricsHandler.removeCallbacks(resumeAutoScrollRunnable);
                    if (currentScrollAnimator != null && currentScrollAnimator.isRunning()) {
                        currentScrollAnimator.cancel();
                    }
                } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    lyricsHandler.removeCallbacks(resumeAutoScrollRunnable);
                    lyricsHandler.postDelayed(resumeAutoScrollRunnable, 3500);
                }
                return false;
            });
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                lyricsScroll.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                    if (isLyricsVisible && !isControlsHiddenInLyrics) {
                        updateBottomLinesBlur(true);
                    }
                });
            }
        }

        SeekBar progressBar = view.findViewById(R.id.now_playing_progress_bar);
        progressBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    ((TextView) view.findViewById(R.id.now_playing_time_current)).setText(formatTime(progress));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isUserSeeking = true;
                cancelControlsAutoHide();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                isUserSeeking = false;
                MusicPlayerManager.getInstance().seekTo(seekBar.getProgress());
                if (isLyricsVisible && !isControlsHiddenInLyrics) {
                    scheduleControlsAutoHide();
                }
            }
        });

        if (cachedBottomControls != null) {
            cachedBottomControls.setOnTouchListener((v, event) -> {
                int action = event.getAction();
                if (action == MotionEvent.ACTION_DOWN) {
                    cancelControlsAutoHide();
                } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    if (isLyricsVisible && !isControlsHiddenInLyrics && !isUserSeeking) {
                        scheduleControlsAutoHide();
                    }
                }
                return false;
            });
        }

        View btnPrevious = view.findViewById(R.id.now_playing_btn_previous);
        UiAnimationHelper.applyTouchBounce(btnPrevious);
        btnPrevious.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
            MusicPlayerManager.getInstance().previousTrack();
            if (isLyricsVisible && !isControlsHiddenInLyrics) {
                scheduleControlsAutoHide();
            }
        });
            
        View btnNext = view.findViewById(R.id.now_playing_btn_next);
        UiAnimationHelper.applyTouchBounce(btnNext);
        btnNext.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
            MusicPlayerManager.getInstance().nextTrack();
            if (isLyricsVisible && !isControlsHiddenInLyrics) {
                scheduleControlsAutoHide();
            }
        });
            
        View btnCollapse = view.findViewById(R.id.now_playing_collapse);
        UiAnimationHelper.applyTouchBounce(btnCollapse);
        btnCollapse.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
            animateAndCollapse();
        });
            
        view.findViewById(R.id.now_playing_more).setOnClickListener(v -> {
            Track track = MusicPlayerManager.getInstance().getCurrentTrack();
            if (track != null && track.getTrackViewUrl() != null) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(track.getTrackViewUrl())));
            } else {
                android.widget.Toast.makeText(getContext(), "Chưa có liên kết bài hát", android.widget.Toast.LENGTH_SHORT).show();
            }
        });

        setupRecordingHUD(view);

        ImageView btnSparkle = view.findViewById(R.id.now_playing_btn_sparkle);
        if (btnSparkle != null) {
            btnSparkle.setOnClickListener(v -> toggleSyncPanel());
        }
        View karaokeTools = view.findViewById(R.id.now_playing_karaoke_tools);
        if (karaokeTools != null) {
            karaokeTools.setOnClickListener(v -> toggleSyncPanel());
        }
        setupSyncPanel(view);
            
        View btnVolume = view.findViewById(R.id.now_playing_btn_volume);
        if (btnVolume != null) {
            UiAnimationHelper.applyTouchBounce(btnVolume);
            btnVolume.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                AudioOutputDialogHelper.showAudioOutputDialog(requireContext());
            });
        }
        updateVolumeButtonIcon();

        view.findViewById(R.id.now_playing_btn_bookmark).setOnClickListener(v -> showAddToPlaylistDialog());
        view.findViewById(R.id.now_playing_btn_queue).setOnClickListener(v -> showQueueDialog());
    }

    private float dpToPx(float dp) {
        return UiAnimationHelper.dpToPx(getContext(), dp);
    }

    private void setupDragToCollapse(View view) {
        View dragHandleArea = view.findViewById(R.id.now_playing_drag_handle_area);
        View.OnTouchListener dragListener = new View.OnTouchListener() {
            private float startY = 0f;
            private float initialTransY = 0f;
            private boolean isDragging = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                View fragmentView = getView();
                if (fragmentView == null) return false;

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startY = event.getRawY();
                        initialTransY = fragmentView.getTranslationY();
                        isDragging = false;
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float deltaY = event.getRawY() - startY;
                        if (deltaY > 8 || isDragging) {
                            isDragging = true;
                            float newY = Math.max(0, initialTransY + deltaY);
                            fragmentView.setTranslationY(newY);
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        float totalDelta = event.getRawY() - startY;
                        if (isDragging && totalDelta > dpToPx(90)) {
                            animateAndCollapse();
                        } else if (isDragging) {
                            fragmentView.animate()
                                    .translationY(0f)
                                    .setDuration(220)
                                    .setInterpolator(new DecelerateInterpolator(1.8f))
                                    .start();
                        } else {
                            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                            animateAndCollapse();
                        }
                        isDragging = false;
                        return true;
                }
                return false;
            }
        };

        if (dragHandleArea != null) {
            dragHandleArea.setOnTouchListener(dragListener);
        }
    }

    private void animateAndCollapse() {
        View fragmentView = getView();
        if (fragmentView != null && isAdded()) {
            int height = fragmentView.getHeight() > 0 ? fragmentView.getHeight() : (int) dpToPx(600);
            fragmentView.animate()
                    .translationY(height)
                    .alpha(0.3f)
                    .setDuration(220)
                    .setInterpolator(new AccelerateInterpolator(1.6f))
                    .withEndAction(() -> {
                        fragmentView.setTranslationY(0f);
                        fragmentView.setAlpha(1f);
                        if (getActivity() instanceof MainActivity) {
                            ((MainActivity) getActivity()).collapseNowPlaying();
                        }
                    })
                    .start();
        } else if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).collapseNowPlaying();
        }
    }

    private void enterLyricsMode() {
        if (isLyricsVisible) return;
        isLyricsVisible = true;
        isControlsHiddenInLyrics = false;

        // 1. Update compact header info
        Track current = MusicPlayerManager.getInstance().getCurrentTrack();
        if (current != null) {
            if (cachedCompactTitle != null) cachedCompactTitle.setText(current.getTitle());
            if (cachedCompactArtist != null) cachedCompactArtist.setText(current.getArtist());
            if (cachedCompactArt != null && current.getArtworkUrl() != null && !current.getArtworkUrl().trim().isEmpty()) {
                Glide.with(this)
                     .load(current.getArtworkUrl())
                     .override(160, 160)
                     .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                     .placeholder(R.drawable.bg_album_art)
                     .error(R.drawable.bg_album_art)
                     .into(cachedCompactArt);
            }
        }

        // 2. Hide normal top bar with fade
        if (cachedTopBar != null) {
            cachedTopBar.animate().cancel();
            cachedTopBar.animate().alpha(0f).setDuration(180).withEndAction(() -> cachedTopBar.setVisibility(View.GONE)).start();
        }

        // 3. Apple Music-style Morphing Animation for Album Art & Song Title:
        View favContainer = getView() != null ? getView().findViewById(R.id.now_playing_favorite_container) : null;
        View lyricsView = getView() != null ? getView().findViewById(R.id.now_playing_lyrics_view) : null;

        if (cachedContentFrame != null && cachedMainContent != null && cachedLyricsHeader != null) {
            cachedContentFrame.animate().cancel();
            if (cachedSongInfo != null) cachedSongInfo.animate().cancel();
            if (favContainer != null) favContainer.animate().cancel();
            cachedLyricsHeader.animate().cancel();

            // Destination target dimensions for compact art (56dp at top-left)
            float targetCompactX = dpToPx(16);
            float targetCompactY = dpToPx(28);
            float compactSize = dpToPx(56);
            float compactCenterX = targetCompactX + compactSize / 2f;
            float compactCenterY = targetCompactY + compactSize / 2f;

            // Source dimensions for big album art
            float bigWidth = cachedContentFrame.getWidth() > 0 ? cachedContentFrame.getWidth() : dpToPx(300);
            float bigCenterX = cachedMainContent.getX() + cachedContentFrame.getX() + bigWidth / 2f;
            float bigCenterY = cachedMainContent.getY() + cachedContentFrame.getY() + bigWidth / 2f;

            float targetScale = compactSize / bigWidth;
            float deltaX = compactCenterX - bigCenterX;
            float deltaY = compactCenterY - bigCenterY;

            // Destination for song title/artist beside compact art
            float targetTitleX = targetCompactX + compactSize + dpToPx(14);
            float targetTitleY = targetCompactY + dpToPx(6);
            float bigTitleX = cachedSongInfo != null ? (cachedMainContent.getX() + cachedSongInfo.getX() + dpToPx(20)) : 0;
            float bigTitleY = cachedSongInfo != null ? (cachedMainContent.getY() + cachedSongInfo.getY()) : 0;
            float titleDeltaX = targetTitleX - bigTitleX;
            float titleDeltaY = targetTitleY - bigTitleY;

            // Animate big album art: shrinks and glides directly into top-left compact slot
            cachedContentFrame.setPivotX(bigWidth / 2f);
            cachedContentFrame.setPivotY(bigWidth / 2f);
            cachedContentFrame.animate()
                    .translationX(deltaX)
                    .translationY(deltaY)
                    .scaleX(targetScale)
                    .scaleY(targetScale)
                    .alpha(0.08f)
                    .setDuration(360)
                    .setInterpolator(new DecelerateInterpolator(2.0f))
                    .start();

            // Animate song info: shrinks and glides directly to title slot beside compact art
            if (cachedSongInfo != null) {
                cachedSongInfo.setPivotX(0f);
                cachedSongInfo.setPivotY(0f);
                cachedSongInfo.animate()
                        .translationX(titleDeltaX)
                        .translationY(titleDeltaY)
                        .scaleX(0.72f)
                        .scaleY(0.72f)
                        .alpha(0.08f)
                        .setDuration(340)
                        .setInterpolator(new DecelerateInterpolator(2.0f))
                        .start();
            }

            // Fade out favorite heart during morph
            if (favContainer != null) {
                favContainer.animate().alpha(0f).setDuration(160).start();
            }

            // Compact header fades in smoothly as they land
            cachedLyricsHeader.setVisibility(View.VISIBLE);
            cachedLyricsHeader.setAlpha(0f);
            cachedLyricsHeader.animate()
                    .alpha(1f)
                    .setDuration(160)
                    .setStartDelay(200)
                    .withEndAction(() -> {
                        if (cachedMainContent != null && isLyricsVisible) {
                            cachedMainContent.setVisibility(View.GONE);
                            if (cachedContentFrame != null) {
                                cachedContentFrame.setTranslationX(0f);
                                cachedContentFrame.setTranslationY(0f);
                                cachedContentFrame.setScaleX(1f);
                                cachedContentFrame.setScaleY(1f);
                                cachedContentFrame.setAlpha(1f);
                            }
                            if (cachedSongInfo != null) {
                                cachedSongInfo.setTranslationX(0f);
                                cachedSongInfo.setTranslationY(0f);
                                cachedSongInfo.setScaleX(1f);
                                cachedSongInfo.setScaleY(1f);
                                cachedSongInfo.setAlpha(1f);
                            }
                            if (favContainer != null) favContainer.setAlpha(1f);
                        }
                    })
                    .start();
        } else if (cachedLyricsHeader != null) {
            cachedLyricsHeader.setVisibility(View.VISIBLE);
            cachedLyricsHeader.setAlpha(1f);
            if (cachedMainContent != null) cachedMainContent.setVisibility(View.GONE);
        }

        // 4. Show lyrics view with fluid slide-up & fade
        if (lyricsView != null) {
            lyricsView.animate().cancel();
            lyricsView.setVisibility(View.VISIBLE);
            lyricsView.setAlpha(0f);
            lyricsView.setTranslationY(dpToPx(40));
            lyricsView.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(360)
                    .setInterpolator(new DecelerateInterpolator(1.8f))
                    .start();
            if (currentLyricIndex >= 0) {
                lyricsView.post(() -> updateLyricsUI(currentLyricIndex, false));
            }
        }

        // 5. Bottom controls: keep visible with frosted glass backdrop, then sink down & hide after 1.5s
        if (cachedBottomControls != null) {
            cachedBottomControls.setBackgroundResource(R.drawable.bg_lyrics_controls_frosted);
            cachedBottomControls.animate().cancel();
            cachedBottomControls.setVisibility(View.VISIBLE);
            cachedBottomControls.setTranslationY(0f);
            cachedBottomControls.setAlpha(1f);
        }
        if (cachedBottomTapTarget != null) {
            cachedBottomTapTarget.setVisibility(View.GONE);
        }
        updateBottomLyricsBlur(true, true);
        scheduleControlsAutoHide();

        // 6. Highlight lyrics button
        if (cachedBtnLyrics != null) {
            cachedBtnLyrics.setColorFilter(getResources().getColor(R.color.coral_primary, null));
        }
    }

    private void updateBottomLyricsBlur(boolean enable, boolean animate) {
        if (lyricsBlurAnimator != null) {
            lyricsBlurAnimator.cancel();
            lyricsBlurAnimator = null;
        }

        // 1. Frosted gradient backdrop docked in bottom 250dp
        if (cachedLyricsBottomBackdrop != null) {
            if (enable) {
                cachedLyricsBottomBackdrop.setVisibility(View.VISIBLE);
                if (animate) {
                    cachedLyricsBottomBackdrop.animate().cancel();
                    cachedLyricsBottomBackdrop.animate().alpha(1f).setDuration(260).start();
                } else {
                    cachedLyricsBottomBackdrop.setAlpha(1f);
                }
            } else {
                if (animate) {
                    cachedLyricsBottomBackdrop.animate().cancel();
                    cachedLyricsBottomBackdrop.animate().alpha(0f).setDuration(240)
                            .withEndAction(() -> {
                                if (isControlsHiddenInLyrics && cachedLyricsBottomBackdrop != null) {
                                    cachedLyricsBottomBackdrop.setVisibility(View.GONE);
                                }
                            })
                            .start();
                } else {
                    cachedLyricsBottomBackdrop.setAlpha(0f);
                    cachedLyricsBottomBackdrop.setVisibility(View.GONE);
                }
            }
        }

        // 2. Clear whole-screen blur immediately - NEVER blur the entire ScrollView
        if (cachedLyricsScroll != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            cachedLyricsScroll.setRenderEffect(null);
        }

        // 3. Blur only individual lyric lines in the bottom 220dp region behind controls
        updateBottomLinesBlur(enable);
    }

    private void updateBottomLinesBlur(boolean enable) {
        if (cachedLyricsContainer == null || cachedLyricsScroll == null) return;
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) return;

        // Ensure scroll view itself is NEVER blurred
        cachedLyricsScroll.setRenderEffect(null);

        int childCount = cachedLyricsContainer.getChildCount();
        if (childCount == 0) return;

        if (!enable) {
            for (int i = 0; i < childCount; i++) {
                View child = cachedLyricsContainer.getChildAt(i);
                if (child != null) {
                    child.setRenderEffect(null);
                }
            }
            return;
        }

        int scrollY = cachedLyricsScroll.getScrollY();
        int scrollHeight = cachedLyricsScroll.getHeight();
        if (scrollHeight <= 0) return;

        // Bottom threshold: lyrics with top Y >= bottomThresholdY are under the controls (~220dp from bottom)
        int bottomThresholdY = scrollY + scrollHeight - (int) dpToPx(220);

        for (int i = 0; i < childCount; i++) {
            View child = cachedLyricsContainer.getChildAt(i);
            if (child == null) continue;
            int childTop = child.getTop();
            if (childTop >= bottomThresholdY) {
                // Frosted blur for text under the bottom controls
                child.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(
                        10f, 10f, android.graphics.Shader.TileMode.CLAMP));
            } else {
                // 100% crisp and readable text in active viewing area
                child.setRenderEffect(null);
            }
        }
    }

    private void scheduleControlsAutoHide() {
        autoHideHandler.removeCallbacks(autoHideControlsRunnable);
        if (syncPanel != null && syncPanel.getVisibility() == View.VISIBLE) {
            return;
        }
        if (isLyricsVisible && !isControlsHiddenInLyrics && !isUserSeeking) {
            autoHideHandler.postDelayed(autoHideControlsRunnable, CONTROLS_AUTO_HIDE_DELAY_MS);
        }
    }

    private void cancelControlsAutoHide() {
        autoHideHandler.removeCallbacks(autoHideControlsRunnable);
    }

    private void exitLyricsMode() {
        cancelControlsAutoHide();
        isLyricsVisible = false;
        isControlsHiddenInLyrics = false;
        hideSyncPanel();
        updateBottomLyricsBlur(false, false);

        View favContainer = getView() != null ? getView().findViewById(R.id.now_playing_favorite_container) : null;
        View lyricsView = getView() != null ? getView().findViewById(R.id.now_playing_lyrics_view) : null;

        // 1. Hide compact lyrics header
        if (cachedLyricsHeader != null) {
            cachedLyricsHeader.animate().cancel();
            cachedLyricsHeader.animate()
                    .alpha(0f)
                    .setDuration(120)
                    .withEndAction(() -> {
                        if (cachedLyricsHeader != null) {
                            cachedLyricsHeader.setVisibility(View.GONE);
                        }
                    })
                    .start();
        }

        // 2. Hide lyrics view smoothly downward
        if (lyricsView != null) {
            lyricsView.animate().cancel();
            lyricsView.animate()
                    .alpha(0f)
                    .translationY(dpToPx(40))
                    .setDuration(220)
                    .withEndAction(() -> {
                        if (lyricsView != null) {
                            lyricsView.setVisibility(View.GONE);
                            lyricsView.setTranslationY(0f);
                        }
                    })
                    .start();
        }
        if (cachedBottomTapTarget != null) {
            cachedBottomTapTarget.setVisibility(View.GONE);
        }
        stopArrowHintBounce();

        // 3. Restore normal top bar
        if (cachedTopBar != null) {
            cachedTopBar.setVisibility(View.VISIBLE);
            cachedTopBar.setAlpha(0f);
            cachedTopBar.animate().alpha(1f).setDuration(240).start();
        }

        // 4. Apple Music-style Reverse Morph: expand album art & title back from compact top-left position to center
        if (cachedContentFrame != null && cachedMainContent != null) {
            cachedContentFrame.animate().cancel();
            if (cachedSongInfo != null) cachedSongInfo.animate().cancel();
            if (favContainer != null) favContainer.animate().cancel();

            float targetCompactX = dpToPx(16);
            float targetCompactY = dpToPx(28);
            float compactSize = dpToPx(56);
            float compactCenterX = targetCompactX + compactSize / 2f;
            float compactCenterY = targetCompactY + compactSize / 2f;

            float bigWidth = cachedContentFrame.getWidth() > 0 ? cachedContentFrame.getWidth() : dpToPx(300);
            float bigCenterX = cachedMainContent.getX() + cachedContentFrame.getX() + bigWidth / 2f;
            float bigCenterY = cachedMainContent.getY() + cachedContentFrame.getY() + bigWidth / 2f;

            float targetScale = compactSize / bigWidth;
            float deltaX = compactCenterX - bigCenterX;
            float deltaY = compactCenterY - bigCenterY;

            float targetTitleX = targetCompactX + compactSize + dpToPx(14);
            float targetTitleY = targetCompactY + dpToPx(6);
            float bigTitleX = cachedSongInfo != null ? (cachedMainContent.getX() + cachedSongInfo.getX() + dpToPx(20)) : 0;
            float bigTitleY = cachedSongInfo != null ? (cachedMainContent.getY() + cachedSongInfo.getY()) : 0;
            float titleDeltaX = targetTitleX - bigTitleX;
            float titleDeltaY = targetTitleY - bigTitleY;

            cachedMainContent.setVisibility(View.VISIBLE);
            cachedMainContent.setAlpha(1f);

            // Start contentFrame at the compact art position & scale
            cachedContentFrame.setPivotX(bigWidth / 2f);
            cachedContentFrame.setPivotY(bigWidth / 2f);
            cachedContentFrame.setTranslationX(deltaX);
            cachedContentFrame.setTranslationY(deltaY);
            cachedContentFrame.setScaleX(targetScale);
            cachedContentFrame.setScaleY(targetScale);
            cachedContentFrame.setAlpha(1f);

            // Animate contentFrame expanding back to center
            cachedContentFrame.animate()
                    .translationX(0f)
                    .translationY(0f)
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .alpha(1f)
                    .setDuration(360)
                    .setInterpolator(new DecelerateInterpolator(2.0f))
                    .start();

            // Start songInfo at the compact title position
            if (cachedSongInfo != null) {
                cachedSongInfo.setPivotX(0f);
                cachedSongInfo.setPivotY(0f);
                cachedSongInfo.setTranslationX(titleDeltaX);
                cachedSongInfo.setTranslationY(titleDeltaY);
                cachedSongInfo.setScaleX(0.72f);
                cachedSongInfo.setScaleY(0.72f);
                cachedSongInfo.setAlpha(1f);

                cachedSongInfo.animate()
                        .translationX(0f)
                        .translationY(0f)
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .alpha(1f)
                        .setDuration(360)
                        .setInterpolator(new DecelerateInterpolator(2.0f))
                        .start();
            }

            if (favContainer != null) {
                favContainer.setAlpha(0f);
                favContainer.animate().alpha(1f).setDuration(240).setStartDelay(160).start();
            }
        } else if (cachedMainContent != null) {
            cachedMainContent.setVisibility(View.VISIBLE);
            cachedMainContent.setAlpha(1f);
        }

        // 5. Restore bottom controls permanently and remove frosted background
        if (cachedBottomControls != null) {
            cachedBottomControls.setBackground(null);
            cachedBottomControls.animate().cancel();
            cachedBottomControls.setVisibility(View.VISIBLE);
            cachedBottomControls.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(260)
                    .setInterpolator(new DecelerateInterpolator(1.8f))
                    .start();
        }

        if (cachedBtnLyrics != null) {
            cachedBtnLyrics.setColorFilter(getResources().getColor(R.color.text_tertiary_adaptive, null));
        }
    }

    private void toggleLyricsMode() {
        if (isLyricsVisible) {
            exitLyricsMode();
        } else {
            enterLyricsMode();
        }
    }

    private void showBottomControlsInLyrics() {
        if (cachedBottomControls == null || !isLyricsVisible) return;
        if (syncPanel != null && syncPanel.getVisibility() == View.VISIBLE) {
            return;
        }
        stopArrowHintBounce();
        isControlsHiddenInLyrics = false;
        cachedBottomControls.setBackgroundResource(R.drawable.bg_lyrics_controls_frosted);
        cachedBottomControls.animate().cancel();
        cachedBottomControls.setVisibility(View.VISIBLE);

        int controlsHeight = cachedBottomControls.getHeight() > 0 ? cachedBottomControls.getHeight() : (int) dpToPx(220);
        if (cachedBottomControls.getAlpha() == 0f || cachedBottomControls.getTranslationY() == 0f) {
            cachedBottomControls.setTranslationY(controlsHeight);
        }

        cachedBottomControls.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(280)
                .setInterpolator(new DecelerateInterpolator(1.8f))
                .start();

        if (cachedBottomTapTarget != null) {
            cachedBottomTapTarget.setVisibility(View.GONE);
        }

        updateBottomLyricsBlur(true, true);
        scheduleControlsAutoHide();
    }

    private void hideBottomControlsInLyrics() {
        cancelControlsAutoHide();
        if (cachedBottomControls == null || !isLyricsVisible) return;
        isControlsHiddenInLyrics = true;
        int controlsHeight = cachedBottomControls.getHeight() > 0 ? cachedBottomControls.getHeight() : (int) dpToPx(220);
        cachedBottomControls.animate().cancel();
        cachedBottomControls.animate()
                .translationY(controlsHeight)
                .alpha(0f)
                .setDuration(280)
                .setInterpolator(new AccelerateInterpolator(1.6f))
                .withEndAction(() -> {
                    if (isControlsHiddenInLyrics && cachedBottomControls != null) {
                        cachedBottomControls.setVisibility(View.GONE);
                    }
                    if (isControlsHiddenInLyrics && cachedBottomTapTarget != null) {
                        if (syncPanel == null || syncPanel.getVisibility() != View.VISIBLE) {
                            cachedBottomTapTarget.setVisibility(View.VISIBLE);
                            cachedBottomTapTarget.setAlpha(0f);
                            cachedBottomTapTarget.animate().alpha(1f).setDuration(220).start();
                            startArrowHintBounce();
                        } else {
                            cachedBottomTapTarget.setVisibility(View.GONE);
                            stopArrowHintBounce();
                        }
                    }
                })
                .start();

        updateBottomLyricsBlur(false, true);
    }

    private void playGentleArrowBounce() {
        if (cachedHintIndicator == null) return;
        // Float upwards very gently (only ~3.2dp) with smooth easing so it doesn't distract from lyrics
        float bounceDist = -dpToPx(3.2f);
        cachedHintIndicator.animate().cancel();
        cachedHintIndicator.animate()
                .translationY(bounceDist)
                .alpha(0.55f)
                .setDuration(520)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .withEndAction(() -> {
                    if (cachedHintIndicator != null) {
                        cachedHintIndicator.animate()
                                .translationY(0f)
                                .alpha(0.35f)
                                .setDuration(620)
                                .setInterpolator(new AccelerateDecelerateInterpolator())
                                .start();
                    }
                })
                .start();
    }

    private void startArrowHintBounce() {
        if (isHintBounceRunning) return;
        isHintBounceRunning = true;
        if (cachedHintIndicator != null) {
            cachedHintIndicator.setTranslationY(0f);
            cachedHintIndicator.setAlpha(0.35f);
        }
        bounceHintHandler.removeCallbacks(hintBounceRunnable);
        bounceHintHandler.postDelayed(hintBounceRunnable, 1000L);
    }

    private void stopArrowHintBounce() {
        isHintBounceRunning = false;
        bounceHintHandler.removeCallbacks(hintBounceRunnable);
        if (cachedHintIndicator != null) {
            cachedHintIndicator.animate().cancel();
            cachedHintIndicator.setTranslationY(0f);
            cachedHintIndicator.setAlpha(0.35f);
        }
    }

    private void setupRecordingHUD(View view) {
        View recordingBar = view.findViewById(R.id.now_playing_recording_bar);
        View btnRecStop = view.findViewById(R.id.now_playing_rec_btn_stop);
        View btnRecCancel = view.findViewById(R.id.now_playing_rec_btn_cancel);
        ImageView btnMic = view.findViewById(R.id.now_playing_btn_mic);

        btnMic.setOnClickListener(v -> {
            if (KaraokeRecorder.getInstance().isRecording()) {
                stopKaraokeRecording(view);
            } else {
                if (ContextCompat.checkSelfPermission(
                        requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    startKaraokeRecording();
                } else {
                    recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
                }
            }
        });

        if (btnRecStop != null) {
            btnRecStop.setOnClickListener(v -> stopKaraokeRecording(view));
        }

        if (btnRecCancel != null) {
            btnRecCancel.setOnClickListener(v -> cancelKaraokeRecording(view));
        }
    }

    private void startKaraokeRecording() {
        if (getView() == null || getContext() == null) return;
        View recordingBar = getView().findViewById(R.id.now_playing_recording_bar);
        View recDot = getView().findViewById(R.id.now_playing_rec_dot);
        TextView tvRecStatus = getView().findViewById(R.id.now_playing_rec_status);
        ImageView btnMic = getView().findViewById(R.id.now_playing_btn_mic);

        Track currentTrack = MusicPlayerManager.getInstance().getCurrentTrack();
        String songTitle = currentTrack != null && currentTrack.getTitle() != null ? currentTrack.getTitle() : "Bài hát";
        String artist = currentTrack != null && currentTrack.getArtist() != null ? currentTrack.getArtist() : "Nghệ sĩ";

        try {
            KaraokeRecorder.getInstance().startRecording(requireContext(), songTitle, artist);
            if (recordingBar != null) recordingBar.setVisibility(View.VISIBLE);
            if (tvRecStatus != null) tvRecStatus.setText("Đang ghi âm: 00:00");
            if (btnMic != null) btnMic.setColorFilter(Color.parseColor("#FF3B30"));

            if (recordingDotAnimator != null) recordingDotAnimator.cancel();
            if (recDot != null) {
                recordingDotAnimator = ObjectAnimator.ofFloat(recDot, "alpha", 1.0f, 0.2f, 1.0f);
                recordingDotAnimator.setDuration(800);
                recordingDotAnimator.setRepeatCount(ObjectAnimator.INFINITE);
                recordingDotAnimator.start();
            }

            KaraokeRecorder.getInstance().setTimerListener((ms, formattedTime) -> {
                if (getActivity() != null && tvRecStatus != null) {
                    getActivity().runOnUiThread(() -> tvRecStatus.setText("Đang ghi âm: " + formattedTime));
                }
            });

            Toast.makeText(getContext(), "Bắt đầu ghi âm Karaoke! Hãy hát theo lời bài hát nhé.", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(getContext(), "Lỗi khởi động micro: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            if (recordingBar != null) recordingBar.setVisibility(View.GONE);
            if (btnMic != null) btnMic.clearColorFilter();
        }
    }

    private void stopKaraokeRecording(View view) {
        if (getContext() == null || view == null) return;
        View recordingBar = view.findViewById(R.id.now_playing_recording_bar);
        ImageView btnMic = view.findViewById(R.id.now_playing_btn_mic);

        if (recordingDotAnimator != null) {
            recordingDotAnimator.cancel();
            recordingDotAnimator = null;
        }
        if (recordingBar != null) recordingBar.setVisibility(View.GONE);
        if (btnMic != null) btnMic.clearColorFilter();

        RecordingItem item = KaraokeRecorder.getInstance().stopRecording();
        if (item != null) {
            RecordingManager.getInstance(requireContext()).addRecording(item);
            showRecordingCompleteDialog(item);
        } else {
            Toast.makeText(getContext(), "Bản thu âm quá ngắn hoặc chưa có dữ liệu.", Toast.LENGTH_SHORT).show();
        }
    }

    private void cancelKaraokeRecording(View view) {
        if (view == null) return;
        View recordingBar = view.findViewById(R.id.now_playing_recording_bar);
        ImageView btnMic = view.findViewById(R.id.now_playing_btn_mic);

        if (recordingDotAnimator != null) {
            recordingDotAnimator.cancel();
            recordingDotAnimator = null;
        }
        if (recordingBar != null) recordingBar.setVisibility(View.GONE);
        if (btnMic != null) btnMic.clearColorFilter();

        KaraokeRecorder.getInstance().cancelRecording();
        Toast.makeText(getContext(), "Đã hủy ghi âm", Toast.LENGTH_SHORT).show();
    }

    private void showRecordingCompleteDialog(RecordingItem item) {
        if (getContext() == null || item == null) return;
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_recording_complete, null);
        dialog.setContentView(dialogView);
        if (dialog.getWindow() != null) {
            View bottomSheet = dialog.getWindow().findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) bottomSheet.setBackgroundResource(android.R.color.transparent);
        }

        TextView tvTitle = dialogView.findViewById(R.id.dialog_rec_title);
        TextView tvInfo = dialogView.findViewById(R.id.dialog_rec_info);
        ImageView btnPlay = dialogView.findViewById(R.id.dialog_rec_btn_play);
        SeekBar seekBar = dialogView.findViewById(R.id.dialog_rec_seekbar);
        TextView tvCurrent = dialogView.findViewById(R.id.dialog_rec_time_current);
        TextView tvTotal = dialogView.findViewById(R.id.dialog_rec_time_total);
        View btnShare = dialogView.findViewById(R.id.dialog_rec_btn_share);
        View btnLibrary = dialogView.findViewById(R.id.dialog_rec_btn_library);
        View btnClose = dialogView.findViewById(R.id.dialog_rec_btn_close);

        tvTitle.setText(item.getTitle());
        tvInfo.setText(item.getFormattedDuration() + " • " + item.getFormattedDate());
        tvTotal.setText(item.getFormattedDuration());

        final MediaPlayer[] previewPlayer = {null};
        final Handler previewHandler = new Handler(Looper.getMainLooper());
        final Runnable[] previewRunnable = {null};

        btnPlay.setOnClickListener(v -> {
            if (previewPlayer[0] == null) {
                try {
                    previewPlayer[0] = new MediaPlayer();
                    previewPlayer[0].setDataSource(item.getFilePath());
                    previewPlayer[0].prepare();
                    previewPlayer[0].start();
                    btnPlay.setImageResource(R.drawable.ic_pause);
                    seekBar.setMax(previewPlayer[0].getDuration());

                    previewRunnable[0] = new Runnable() {
                        @Override
                        public void run() {
                            if (previewPlayer[0] != null && previewPlayer[0].isPlaying()) {
                                int pos = previewPlayer[0].getCurrentPosition();
                                seekBar.setProgress(pos);
                                tvCurrent.setText(formatTime(pos));
                                previewHandler.postDelayed(this, 200);
                            }
                        }
                    };
                    previewHandler.post(previewRunnable[0]);

                    previewPlayer[0].setOnCompletionListener(mp -> {
                        btnPlay.setImageResource(R.drawable.ic_play);
                        seekBar.setProgress(0);
                        tvCurrent.setText("00:00");
                    });
                } catch (Exception e) {
                    Toast.makeText(getContext(), "Không thể phát bản thu: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            } else if (previewPlayer[0].isPlaying()) {
                previewPlayer[0].pause();
                btnPlay.setImageResource(R.drawable.ic_play);
            } else {
                previewPlayer[0].start();
                btnPlay.setImageResource(R.drawable.ic_pause);
                if (previewRunnable[0] != null) previewHandler.post(previewRunnable[0]);
            }
        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser && previewPlayer[0] != null) {
                    previewPlayer[0].seekTo(progress);
                    tvCurrent.setText(formatTime(progress));
                }
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        btnShare.setOnClickListener(v -> RecordingManager.shareRecording(requireContext(), item));

        btnLibrary.setOnClickListener(v -> {
            dialog.dismiss();
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).showLibraryRecordingsTab();
            }
        });

        btnClose.setOnClickListener(v -> dialog.dismiss());

        dialog.setOnDismissListener(d -> {
            previewHandler.removeCallbacksAndMessages(null);
            if (previewPlayer[0] != null) {
                try {
                    if (previewPlayer[0].isPlaying()) previewPlayer[0].stop();
                    previewPlayer[0].release();
                } catch (Exception ignored) {}
                previewPlayer[0] = null;
            }
        });

        dialog.show();
    }

    private void updateShuffleButtonUI(ImageView btnShuffle) {
        if (btnShuffle == null || getContext() == null) return;
        boolean enabled = MusicPlayerManager.getInstance().isShuffleEnabled();
        int color = enabled ? getResources().getColor(R.color.coral_primary, null) : getResources().getColor(R.color.text_primary_adaptive, null);
        btnShuffle.setColorFilter(color);
    }

    private void updateRepeatButtonUI(ImageView btnRepeat) {
        if (btnRepeat == null || getContext() == null) return;
        int mode = MusicPlayerManager.getInstance().getRepeatMode();
        if (mode == 0) {
            btnRepeat.setImageResource(R.drawable.ic_repeat);
            btnRepeat.setColorFilter(getResources().getColor(R.color.text_primary_adaptive, null));
        } else if (mode == 1) {
            btnRepeat.setImageResource(R.drawable.ic_repeat);
            btnRepeat.setColorFilter(getResources().getColor(R.color.coral_primary, null));
        } else {
            btnRepeat.setImageResource(R.drawable.ic_repeat_one);
            btnRepeat.setColorFilter(getResources().getColor(R.color.coral_primary, null));
        }
    }

    public void updateFavoriteButtonUI(Track track) {
        if (getView() == null || getContext() == null) return;
        ImageView btnFavorite = getView().findViewById(R.id.now_playing_btn_favorite);
        if (btnFavorite == null) return;

        boolean isFav = false;
        if (track != null && AuthManager.getInstance().isLoggedIn()) {
            isFav = PlaylistManager.getInstance(requireContext()).isTrackFavorite(track);
        }

        if (isFav) {
            btnFavorite.setImageResource(R.drawable.ic_heart_filled);
            btnFavorite.setColorFilter(Color.parseColor("#FF2D55"));
        } else {
            btnFavorite.setImageResource(R.drawable.ic_heart_outline);
            btnFavorite.setColorFilter(getResources().getColor(R.color.text_tertiary_adaptive, null));
        }
        btnFavorite.setScaleX(1.0f);
        btnFavorite.setScaleY(1.0f);
    }

    private void toggleFavoriteWithAnimation(ImageView heartView, View burstRing, View burstDots) {
        if (getContext() == null || heartView == null) return;
        Track currentTrack = MusicPlayerManager.getInstance().getCurrentTrack();
        if (currentTrack == null) {
            Toast.makeText(getContext(), "Không có bài hát đang phát", Toast.LENGTH_SHORT).show();
            return;
        }

        // Haptic feedback for responsive tactile feeling
        heartView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);

        PlaylistManager pm = PlaylistManager.getInstance(requireContext());
        boolean isNowFav = pm.toggleFavorite(currentTrack);

        heartView.animate().cancel();

        if (isNowFav) {
            // Favorited: Filled red/pink heart + spring pop + particle explosion
            heartView.setImageResource(R.drawable.ic_heart_filled);
            heartView.setColorFilter(Color.parseColor("#FF2D55"));

            // Heart bounce animation: 0.6 -> 1.38 -> 1.0
            heartView.setScaleX(0.6f);
            heartView.setScaleY(0.6f);
            heartView.animate()
                    .scaleX(1.38f)
                    .scaleY(1.38f)
                    .setDuration(240)
                    .setInterpolator(new OvershootInterpolator(4.0f))
                    .withEndAction(() -> {
                        heartView.animate()
                                .scaleX(1.0f)
                                .scaleY(1.0f)
                                .setDuration(120)
                                .setInterpolator(new DecelerateInterpolator())
                                .start();
                    })
                    .start();

            // Burst ring expansion & fade
            if (burstRing != null) {
                burstRing.animate().cancel();
                burstRing.setVisibility(View.VISIBLE);
                burstRing.setScaleX(0.2f);
                burstRing.setScaleY(0.2f);
                burstRing.setAlpha(1.0f);
                burstRing.animate()
                        .scaleX(2.0f)
                        .scaleY(2.0f)
                        .alpha(0.0f)
                        .setDuration(380)
                        .setInterpolator(new DecelerateInterpolator(2.0f))
                        .withEndAction(() -> burstRing.setVisibility(View.INVISIBLE))
                        .start();
            }

            // Burst sparkle dots expansion & slight spin
            if (burstDots != null) {
                burstDots.animate().cancel();
                burstDots.setVisibility(View.VISIBLE);
                burstDots.setScaleX(0.3f);
                burstDots.setScaleY(0.3f);
                burstDots.setAlpha(1.0f);
                burstDots.setRotation(0f);
                burstDots.animate()
                        .scaleX(1.75f)
                        .scaleY(1.75f)
                        .rotation(28f)
                        .alpha(0.0f)
                        .setDuration(450)
                        .setInterpolator(new DecelerateInterpolator(1.8f))
                        .withEndAction(() -> burstDots.setVisibility(View.INVISIBLE))
                        .start();
            }

            Toast.makeText(getContext(), "Đã thêm vào Bài hát yêu thích", Toast.LENGTH_SHORT).show();
        } else {
            // Unfavorited: Smooth deflate bounce back to outline
            if (burstRing != null) burstRing.setVisibility(View.INVISIBLE);
            if (burstDots != null) burstDots.setVisibility(View.INVISIBLE);

            heartView.animate()
                    .scaleX(0.75f)
                    .scaleY(0.75f)
                    .setDuration(120)
                    .withEndAction(() -> {
                        heartView.setImageResource(R.drawable.ic_heart_outline);
                        heartView.setColorFilter(getResources().getColor(R.color.text_tertiary_adaptive, null));
                        heartView.animate()
                                .scaleX(1.0f)
                                .scaleY(1.0f)
                                .setDuration(150)
                                .setInterpolator(new DecelerateInterpolator())
                                .start();
                    })
                    .start();

            Toast.makeText(getContext(), "Đã xóa khỏi Bài hát yêu thích", Toast.LENGTH_SHORT).show();
        }
    }

    private void showQueueDialog() {
        if (getContext() == null) return;
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_queue, null);
        dialog.setContentView(dialogView);

        TextView tvTitle = dialogView.findViewById(R.id.dialog_queue_title);
        TextView btnClear = dialogView.findViewById(R.id.dialog_queue_clear);
        View btnClose = dialogView.findViewById(R.id.dialog_queue_close);
        RecyclerView recyclerView = dialogView.findViewById(R.id.dialog_queue_recycler);
        View emptyView = dialogView.findViewById(R.id.dialog_queue_empty);

        btnClose.setOnClickListener(v -> dialog.dismiss());

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        List<Track> queueTracks = MusicPlayerManager.getInstance().getQueue();

        tvTitle.setText("Hàng chờ phát (" + queueTracks.size() + " bài)");

        if (queueTracks.isEmpty()) {
            recyclerView.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            emptyView.setVisibility(View.GONE);
        }

        QueueAdapter queueAdapter = new QueueAdapter(queueTracks, MusicPlayerManager.getInstance().getCurrentQueueIndex(), new QueueAdapter.QueueListener() {
            @Override
            public void onTrackClick(int position) {
                MusicPlayerManager.getInstance().playTrackAt(position);
                dialog.dismiss();
            }

            @Override
            public void onRemoveClick(int position) {
                MusicPlayerManager.getInstance().removeFromQueue(position);
                List<Track> updated = MusicPlayerManager.getInstance().getQueue();
                tvTitle.setText("Hàng chờ phát (" + updated.size() + " bài)");
                if (updated.isEmpty()) {
                    recyclerView.setVisibility(View.GONE);
                    emptyView.setVisibility(View.VISIBLE);
                }
            }
        });
        recyclerView.setAdapter(queueAdapter);

        btnClear.setOnClickListener(v -> {
            MusicPlayerManager.getInstance().clearQueue();
            List<Track> updated = MusicPlayerManager.getInstance().getQueue();
            queueAdapter.updateTracks(updated, MusicPlayerManager.getInstance().getCurrentQueueIndex());
            tvTitle.setText("Hàng chờ phát (" + updated.size() + " bài)");
            if (updated.isEmpty()) {
                recyclerView.setVisibility(View.GONE);
                emptyView.setVisibility(View.VISIBLE);
            }
            Toast.makeText(getContext(), "Đã làm sạch hàng chờ", Toast.LENGTH_SHORT).show();
        });

        dialog.show();
    }

    private void showAddToPlaylistDialog() {
        Track track = MusicPlayerManager.getInstance().getCurrentTrack();
        if (track == null || getContext() == null) {
            Toast.makeText(getContext(), "Chưa có bài hát nào đang phát", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!AuthManager.getInstance().isLoggedIn()) {
            AuthDialogHelper.showGuestPrompt(requireContext(),
                    "Vui lòng đăng nhập để thêm bài hát vào danh sách phát.",
                    this::showAddToPlaylistDialog);
            return;
        }
        PlaylistDialogHelper.showAddToPlaylistDialog(requireContext(), track, null);
    }

    private View syncPanel;
    private TextView tvSyncValue;
    private TextView tvSyncStatus;
    private TextView tvSyncHint;
    private SeekBar syncSeekBar;
    private boolean isUpdatingSeekBar = false;

    private void setupSyncPanel(View view) {
        syncPanel = view.findViewById(R.id.now_playing_lyrics_sync_panel);
        if (syncPanel == null) return;

        tvSyncValue = view.findViewById(R.id.sync_panel_tv_value);
        tvSyncStatus = view.findViewById(R.id.sync_panel_tv_status);
        tvSyncHint = view.findViewById(R.id.sync_panel_tv_hint);
        syncSeekBar = view.findViewById(R.id.sync_panel_seekbar);

        View btnClose = view.findViewById(R.id.sync_panel_btn_close);
        if (btnClose != null) {
            UiAnimationHelper.applyTouchBounce(btnClose);
            btnClose.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                hideSyncPanel();
            });
        }

        if (syncSeekBar != null) {
            syncSeekBar.setMax(200); // Represents -10s to +10s (center 100 = 0s)
            syncSeekBar.setProgress(100);
            syncSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser && !isUpdatingSeekBar) {
                        userSongOffsetMs = (progress - 100) * 100L;
                        updateSyncPanelUI(false);
                        triggerLiveLyricsUpdate();
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    saveSyncOffset();
                    animateActiveLyricBounce(0);
                }
            });
        }

        // Stepper buttons with jump values (-0.5s slow down, +0.5s speed up)
        View chipMinusHalf = view.findViewById(R.id.sync_panel_chip_minus_half);
        if (chipMinusHalf != null) {
            UiAnimationHelper.applyTouchBounce(chipMinusHalf);
            setupAutoRepeat(chipMinusHalf, -500);
        }
        View chipPlusHalf = view.findViewById(R.id.sync_panel_chip_plus_half);
        if (chipPlusHalf != null) {
            UiAnimationHelper.applyTouchBounce(chipPlusHalf);
            setupAutoRepeat(chipPlusHalf, 500);
        }

        // Smart 1-Tap Sync button: aligns lyric to singer's voice right now
        View btnSyncNow = view.findViewById(R.id.sync_panel_btn_sync_now);
        if (btnSyncNow != null) {
            UiAnimationHelper.applyTouchBounce(btnSyncNow);
            btnSyncNow.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                syncCurrentLyricLineNow();
            });
        }

        // Replay 3s button to immediately listen and test
        View btnReplay = view.findViewById(R.id.sync_panel_btn_replay);
        if (btnReplay != null) {
            UiAnimationHelper.applyTouchBounce(btnReplay);
            btnReplay.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                replayThreeSeconds();
            });
        }

        // Reset to 0s
        View chipReset = view.findViewById(R.id.sync_panel_chip_reset);
        if (chipReset != null) {
            UiAnimationHelper.applyTouchBounce(chipReset);
            chipReset.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                resetSyncOffset();
            });
        }
    }

    private boolean hasSyncedLyrics() {
        if (lyricLines == null || lyricLines.isEmpty()) return false;
        for (LyricLine line : lyricLines) {
            if (line.timeMs >= 0) return true;
        }
        return false;
    }

    private void syncCurrentLyricLineNow() {
        MusicPlayerManager manager = MusicPlayerManager.getInstance();
        if (manager == null || lyricLines == null || lyricLines.isEmpty()) {
            if (getContext() != null) {
                Toast.makeText(getContext(), "Không có lời bài hát để đồng bộ", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        if (!hasSyncedLyrics()) {
            if (getContext() != null) {
                Toast.makeText(getContext(), "Bài hát này dùng lời văn bản thường, chưa có mốc thời gian", Toast.LENGTH_LONG).show();
            }
            return;
        }

        int currentPos = manager.getCurrentPosition();

        // Target line to align: preferentially currentLyricIndex, or closest upcoming/previous synced line
        int targetIdx = currentLyricIndex;
        if (targetIdx < 0 || targetIdx >= lyricLines.size() || lyricLines.get(targetIdx).timeMs < 0) {
            long minDiff = Long.MAX_VALUE;
            for (int i = 0; i < lyricLines.size(); i++) {
                LyricLine line = lyricLines.get(i);
                if (line.timeMs >= 0) {
                    long diff = Math.abs(line.timeMs - currentPos);
                    if (diff < minDiff) {
                        minDiff = diff;
                        targetIdx = i;
                    }
                }
            }
        }

        if (targetIdx >= 0 && targetIdx < lyricLines.size()) {
            LyricLine targetLine = lyricLines.get(targetIdx);
            if (targetLine.timeMs >= 0) {
                // To make lyric trigger right now at currentPos:
                // currentPos + userSongOffsetMs = targetLine.timeMs
                long newOffset = targetLine.timeMs - currentPos;
                userSongOffsetMs = Math.max(-10000, Math.min(10000, newOffset));
                saveSyncOffset();
                updateSyncPanelUI();
                triggerLiveLyricsUpdate();
                animateActiveLyricBounce(0);

                if (getContext() != null) {
                    double sec = Math.abs(userSongOffsetMs) / 1000.0;
                    String direction = userSongOffsetMs >= 0 ? "sớm" : "chậm";
                    String snippet = targetLine.text;
                    if (snippet.length() > 22) snippet = snippet.substring(0, 20) + "...";
                    String msg = String.format(java.util.Locale.US, "🎯 Đã khớp câu \"%s\" (%s %.2fs)", snippet, direction, sec);
                    Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void toggleSyncPanel() {
        if (syncPanel == null) return;
        if (syncPanel.getVisibility() == View.VISIBLE) {
            hideSyncPanel();
        } else {
            showSyncPanel();
        }
    }

    private void showSyncPanel() {
        if (syncPanel == null) return;
        cancelControlsAutoHide();

        // Hide bottom controls & tap target completely so they never overlap or intercept clicks
        if (cachedBottomControls != null) {
            cachedBottomControls.animate().cancel();
            cachedBottomControls.setVisibility(View.GONE);
            cachedBottomControls.setAlpha(0f);
        }
        if (cachedBottomTapTarget != null) {
            cachedBottomTapTarget.setVisibility(View.GONE);
        }
        isControlsHiddenInLyrics = true;
        updateBottomLyricsBlur(false, false);

        syncPanel.bringToFront();

        updateSyncPanelUI();
        syncPanel.setAlpha(0f);
        syncPanel.setTranslationY(60f);
        syncPanel.setVisibility(View.VISIBLE);
        syncPanel.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(260)
                .setInterpolator(new DecelerateInterpolator(1.8f))
                .start();
    }

    private void hideSyncPanel() {
        if (syncPanel == null || syncPanel.getVisibility() != View.VISIBLE) return;
        syncPanel.animate()
                .alpha(0f)
                .translationY(60f)
                .setDuration(200)
                .withEndAction(() -> {
                    syncPanel.setVisibility(View.GONE);
                    if (isLyricsVisible) {
                        if (cachedBottomTapTarget != null && isControlsHiddenInLyrics) {
                            cachedBottomTapTarget.setVisibility(View.VISIBLE);
                            startArrowHintBounce();
                        }
                    }
                })
                .start();
    }

    private void adjustSyncOffset(long deltaMs) {
        userSongOffsetMs += deltaMs;
        userSongOffsetMs = Math.max(-10000, Math.min(10000, userSongOffsetMs));
        saveSyncOffset();
        updateSyncPanelUI();
        triggerLiveLyricsUpdate();
        animateActiveLyricBounce(deltaMs);
    }

    private void animateActiveLyricBounce(long deltaMs) {
        if (cachedLyricsContainer == null) return;
        int targetIdx = currentLyricIndex;
        if (targetIdx < 0 && cachedLyricsContainer.getChildCount() > 0) {
            targetIdx = 0;
        }
        if (targetIdx >= 0 && targetIdx < cachedLyricsContainer.getChildCount()) {
            View activeView = cachedLyricsContainer.getChildAt(targetIdx);
            if (activeView != null) {
                float transX = deltaMs > 0 ? 16f : (deltaMs < 0 ? -16f : 0f);
                if (transX != 0f) {
                    activeView.setTranslationX(transX);
                    activeView.animate()
                            .translationX(0f)
                            .setDuration(220)
                            .setInterpolator(new DecelerateInterpolator(1.8f))
                            .start();
                } else {
                    activeView.setScaleX(1.08f);
                    activeView.setScaleY(1.08f);
                    activeView.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(240)
                            .setInterpolator(new DecelerateInterpolator(2.0f))
                            .start();
                }
            }
        }
    }

    private void resetSyncOffset() {
        userSongOffsetMs = 0;
        Track track = MusicPlayerManager.getInstance().getCurrentTrack();
        if (track != null && getContext() != null) {
            String offsetKey = "lyric_offset_" + (track.getId() > 0 ? String.valueOf(track.getId()) : track.getTitle());
            requireContext().getSharedPreferences("muzzic_preferences", android.content.Context.MODE_PRIVATE)
                    .edit().remove(offsetKey).apply();
        }
        updateSyncPanelUI();
        triggerLiveLyricsUpdate();
        animateActiveLyricBounce(0);
        if (getContext() != null) {
            android.widget.Toast.makeText(getContext(), "Đã đặt lại về 0.00s", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private void replayThreeSeconds() {
        MusicPlayerManager manager = MusicPlayerManager.getInstance();
        if (manager != null) {
            int cur = manager.getCurrentPosition();
            manager.seekTo(Math.max(0, cur - 3000));
            triggerLiveLyricsUpdate();
            if (getContext() != null) {
                android.widget.Toast.makeText(getContext(), "↺ Đang tua lùi 3 giây để nghe thử", android.widget.Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void saveSyncOffset() {
        Track track = MusicPlayerManager.getInstance().getCurrentTrack();
        if (track != null && getContext() != null) {
            String offsetKey = "lyric_offset_" + (track.getId() > 0 ? String.valueOf(track.getId()) : track.getTitle());
            if (userSongOffsetMs == 0) {
                requireContext().getSharedPreferences("muzzic_preferences", android.content.Context.MODE_PRIVATE)
                        .edit().remove(offsetKey).apply();
            } else {
                userSongOffsetMs = Math.max(-10000, Math.min(10000, userSongOffsetMs));
                requireContext().getSharedPreferences("muzzic_preferences", android.content.Context.MODE_PRIVATE)
                        .edit().putLong(offsetKey, userSongOffsetMs).apply();
            }
        }
    }

    private void triggerLiveLyricsUpdate() {
        MusicPlayerManager manager = MusicPlayerManager.getInstance();
        if (manager == null || lyricLines.isEmpty()) return;
        int currentPos = manager.getCurrentPosition();
        int dur = manager.getDuration();
        
        int activeIndex = -1;
        long adjustedPos = currentPos + userSongOffsetMs;
        for (int i = 0; i < lyricLines.size(); i++) {
            if (lyricLines.get(i).timeMs >= 0 && lyricLines.get(i).timeMs <= adjustedPos) {
                activeIndex = i;
            }
        }
        
        currentLyricIndex = activeIndex;
        // Always force update UI and scroll view with animation
        updateLyricsUI(activeIndex, true);
    }

    private void updateSyncPanelUI() {
        updateSyncPanelUI(true);
    }

    private void updateSyncPanelUI(boolean updateSeekBar) {
        if (tvSyncValue == null || tvSyncStatus == null) return;

        if (!hasSyncedLyrics()) {
            tvSyncValue.setText("--");
            tvSyncValue.setTextColor(Color.parseColor("#94A3B8"));
            tvSyncStatus.setText("Lời bài hát chưa hỗ trợ đồng bộ nhịp");
            if (tvSyncHint != null) {
                tvSyncHint.setText("⚠️ Bài hát này là lời văn bản thường, chưa có mốc thời gian từng câu nên không thể chỉnh nhịp.");
                tvSyncHint.setTextColor(Color.parseColor("#F59E0B"));
            }
            if (syncSeekBar != null) syncSeekBar.setEnabled(false);
            return;
        }

        if (syncSeekBar != null) syncSeekBar.setEnabled(true);
        if (tvSyncHint != null) tvSyncHint.setTextColor(Color.parseColor("#94A3B8"));

        double sec = userSongOffsetMs / 1000.0;
        
        if (updateSeekBar && syncSeekBar != null) {
            isUpdatingSeekBar = true;
            int prog = (int) (userSongOffsetMs / 100 + 100);
            prog = Math.max(0, Math.min(200, prog));
            syncSeekBar.setProgress(prog);
            isUpdatingSeekBar = false;
        }

        String lineSnippet = "";
        if (currentLyricIndex >= 0 && currentLyricIndex < lyricLines.size()) {
            lineSnippet = " • " + lyricLines.get(currentLyricIndex).text;
            if (lineSnippet.length() > 28) {
                lineSnippet = lineSnippet.substring(0, 26) + "...";
            }
        }

        if (userSongOffsetMs > 0) {
            tvSyncValue.setText(String.format(java.util.Locale.US, "+%.2fs", sec));
            tvSyncValue.setTextColor(Color.parseColor("#00E5FF")); // Cyan (pushed earlier)
            tvSyncStatus.setText("Lời chạy sớm hơn" + lineSnippet);
            if (tvSyncHint != null) {
                tvSyncHint.setText("⏩ Lời đang chạy sớm hơn nhạc " + String.format(java.util.Locale.US, "%.1fs", sec) + ". Bấm [↺ Nghe lại 3s] để nghe thử.");
            }
        } else if (userSongOffsetMs < 0) {
            tvSyncValue.setText(String.format(java.util.Locale.US, "%.2fs", sec));
            tvSyncValue.setTextColor(Color.parseColor("#BB86FC")); // Purple (delayed)
            tvSyncStatus.setText("Lời chạy trễ lại" + lineSnippet);
            if (tvSyncHint != null) {
                tvSyncHint.setText("⏪ Lời đang chạy chậm hơn nhạc " + String.format(java.util.Locale.US, "%.1fs", Math.abs(sec)) + ". Bấm [↺ Nghe lại 3s] để nghe thử.");
            }
        } else {
            tvSyncValue.setText("0.00s");
            tvSyncValue.setTextColor(Color.WHITE);
            tvSyncStatus.setText("Khớp chuẩn 0s" + lineSnippet);
            if (tvSyncHint != null) {
                tvSyncHint.setText("💡 Mẹo: Bấm 'Khớp ngay câu này' đúng lúc ca sĩ cất giọng để tự đồng bộ chuẩn 100%.");
            }
        }
    }

    private void setupAutoRepeat(View btn, long deltaMs) {
        if (btn == null) return;
        final Handler handler = new Handler(Looper.getMainLooper());
        final boolean[] isLongPress = new boolean[]{false};
        final Runnable runnable = new Runnable() {
            @Override
            public void run() {
                adjustSyncOffset(deltaMs);
                handler.postDelayed(this, 120);
            }
        };

        btn.setOnClickListener(v -> adjustSyncOffset(deltaMs));
        btn.setOnLongClickListener(v -> {
            isLongPress[0] = true;
            handler.post(runnable);
            return true;
        });
        btn.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                if (isLongPress[0]) {
                    handler.removeCallbacks(runnable);
                    isLongPress[0] = false;
                }
            }
            return false;
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        syncStateWithPlayer();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) {
            syncStateWithPlayer();
        }
    }

    public void syncStateWithPlayer() {
        if (getView() == null) return;
        MusicPlayerManager manager = MusicPlayerManager.getInstance();
        if (manager == null) return;
        Track current = manager.getCurrentTrack();
        if (current != null) {
            onTrackChanged(current);
            onPlaybackStateChanged(manager.isPlaying());
            int dur = manager.getDuration();
            if (dur <= 0 && current.getDurationMillis() > 0) {
                dur = (int) current.getDurationMillis();
            }
            onProgressUpdated(manager.getCurrentPosition(), dur);
        }
        updateShuffleButtonUI(getView().findViewById(R.id.now_playing_btn_shuffle));
        updateRepeatButtonUI(getView().findViewById(R.id.now_playing_btn_repeat));
        updateFavoriteButtonUI(current);
        updateVolumeButtonIcon();
    }

    private void updateVolumeButtonIcon() {
        if (getView() == null || getContext() == null) return;
        ImageView btnVolume = getView().findViewById(R.id.now_playing_btn_volume);
        if (btnVolume == null) return;

        android.media.AudioManager am = (android.media.AudioManager) requireContext().getSystemService(android.content.Context.AUDIO_SERVICE);
        boolean hasHeadset = false;
        if (am != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            android.media.AudioDeviceInfo[] devices = am.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS);
            for (android.media.AudioDeviceInfo d : devices) {
                int t = d.getType();
                if (t == android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET
                        || t == android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES
                        || t == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                        || t == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                        || t == android.media.AudioDeviceInfo.TYPE_USB_HEADSET) {
                    hasHeadset = true;
                    break;
                }
            }
        }
        btnVolume.setImageResource(hasHeadset ? R.drawable.ic_headset : R.drawable.ic_volume_up);
    }

    @Override
    public void onTrackChanged(Track track) {
        if (getView() == null || track == null) return;
        updateFavoriteButtonUI(track);
        TextView tvTitle = getView().findViewById(R.id.now_playing_title);
        TextView tvArtist = getView().findViewById(R.id.now_playing_artist);
        ImageView ivArt = getView().findViewById(R.id.now_playing_album_art);
        
        if (tvTitle != null) tvTitle.setText(track.getTitle());
        if (tvArtist != null) tvArtist.setText(track.getArtist());
        
        if (ivArt != null) {
            String highResUrl = track.getHighResArtworkUrl();
            String lowResUrl = track.getArtworkUrl();
            String targetUrl = (highResUrl != null && !highResUrl.trim().isEmpty()) ? highResUrl : lowResUrl;

            if (targetUrl != null && !targetUrl.trim().isEmpty()) {
                com.bumptech.glide.RequestBuilder<android.graphics.drawable.Drawable> request = Glide.with(this)
                        .load(targetUrl)
                        .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                        .placeholder(R.drawable.bg_album_art)
                        .transition(com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade(200));

                if (lowResUrl != null && !lowResUrl.trim().isEmpty() && !lowResUrl.equals(targetUrl)) {
                    request = request.thumbnail(Glide.with(this).load(lowResUrl))
                                     .error(Glide.with(this).load(lowResUrl));
                } else {
                    request = request.error(R.drawable.bg_album_art);
                }

                request.into(ivArt);
            } else {
                ivArt.setImageResource(R.drawable.bg_album_art);
            }
        }

        // Update compact header views in Lyrics mode
        if (cachedCompactTitle != null) cachedCompactTitle.setText(track.getTitle());
        if (cachedCompactArtist != null) cachedCompactArtist.setText(track.getArtist());
        if (cachedCompactArt != null) {
            String highResUrl = track.getHighResArtworkUrl();
            String lowResUrl = track.getArtworkUrl();
            String targetUrl = (highResUrl != null && !highResUrl.trim().isEmpty()) ? highResUrl : lowResUrl;
            if (targetUrl != null && !targetUrl.trim().isEmpty()) {
                Glide.with(this)
                     .load(targetUrl)
                     .override(240, 240)
                     .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                     .placeholder(R.drawable.bg_album_art)
                     .error(R.drawable.bg_album_art)
                     .into(cachedCompactArt);
            } else {
                cachedCompactArt.setImageResource(R.drawable.bg_album_art);
            }
        }

        SeekBar progressBar = getView().findViewById(R.id.now_playing_progress_bar);
        progressBar.setProgress(0);
        ((TextView) getView().findViewById(R.id.now_playing_time_current)).setText(formatTime(0));
        
        int totalMs = (int) track.getDurationMillis();
        if (totalMs > 0) {
            progressBar.setMax(totalMs);
            ((TextView) getView().findViewById(R.id.now_playing_time_total)).setText(formatTime(totalMs));
        } else {
            ((TextView) getView().findViewById(R.id.now_playing_time_total)).setText(formatTime(0));
        }
        
        currentLyricIndex = -1;
        previousActiveLyricIndex = -1;
        isUserScrolling = false;
        lyricsHandler.removeCallbacks(resumeAutoScrollRunnable);
        if (currentScrollAnimator != null) {
            currentScrollAnimator.cancel();
        }

        if (getContext() != null) {
            String offsetKey = "lyric_offset_" + (track.getId() > 0 ? String.valueOf(track.getId()) : track.getTitle());
            userSongOffsetMs = requireContext().getSharedPreferences("muzzic_preferences", android.content.Context.MODE_PRIVATE)
                    .getLong(offsetKey, 0);
            if (Math.abs(userSongOffsetMs) > 10000) {
                userSongOffsetMs = 0;
                requireContext().getSharedPreferences("muzzic_preferences", android.content.Context.MODE_PRIVATE)
                        .edit().remove(offsetKey).apply();
            }
        } else {
            userSongOffsetMs = 0;
        }
        updateSyncPanelUI();

        fetchRealLyrics(track);

        // Apple Music-style: Extract dominant color from album art for dynamic background
        extractDynamicBackgroundColor(track);
    }

    private void extractDynamicBackgroundColor(Track track) {
        if (getView() == null || getContext() == null) return;
        if (track == null) {
            applyDynamicBackground(getDefaultAppleMusicGradient());
            return;
        }
        String artUrl = track.getHighResArtworkUrl();
        if (artUrl == null || artUrl.trim().isEmpty()) artUrl = track.getArtworkUrl();
        if (artUrl == null || artUrl.trim().isEmpty()) {
            applyDynamicBackground(getDefaultAppleMusicGradient());
            return;
        }
        try {
            Glide.with(this)
                 .asBitmap()
                 .load(artUrl)
                 .override(128, 128)
                 .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                 .into(new CustomTarget<Bitmap>() {
                     @Override
                     public void onResourceReady(@NonNull Bitmap bitmap, @Nullable Transition<? super Bitmap> transition) {
                         Palette.from(bitmap).maximumColorCount(24).generate(palette -> {
                             if (palette == null || getView() == null) return;
                             int bestColor = selectBestSignatureColor(palette);
                             int[] gradientColors = generateAppleMusicGradient(bestColor);
                             applyDynamicBackground(gradientColors);
                         });
                     }

                     @Override
                     public void onLoadCleared(@Nullable android.graphics.drawable.Drawable placeholder) {
                         // no-op
                     }

                     @Override
                     public void onLoadFailed(@Nullable android.graphics.drawable.Drawable errorDrawable) {
                         applyDynamicBackground(getDefaultAppleMusicGradient());
                     }
                 });
        } catch (Exception e) {
            applyDynamicBackground(getDefaultAppleMusicGradient());
        }
    }

    private int selectBestSignatureColor(Palette palette) {
        List<Palette.Swatch> swatches = palette.getSwatches();
        if (swatches == null || swatches.isEmpty()) {
            Palette.Swatch dom = palette.getDominantSwatch();
            return dom != null ? dom.getRgb() : Color.parseColor("#181B22");
        }

        Palette.Swatch bestSwatch = null;
        float bestScore = -1f;

        for (Palette.Swatch s : swatches) {
            float[] hsv = new float[3];
            Color.colorToHSV(s.getRgb(), hsv);
            float hue = hsv[0];
            float sat = hsv[1];
            float val = hsv[2];

            // Filter out near-blacks and near-whites
            if (val < 0.12f) continue;
            if (val > 0.88f && sat < 0.18f) continue;
            if (sat < 0.10f && val < 0.70f) continue;

            float popWeight = (float) Math.sqrt(s.getPopulation());
            float satWeight = (float) Math.pow(sat, 1.4);
            float score = popWeight * satWeight;

            // Heavily penalize muddy browns/dirty dark tones (e.g., hue 18° to 45° with low value/saturation)
            if (hue >= 18f && hue <= 45f && sat < 0.65f && val < 0.45f) {
                score *= 0.15f;
            }

            // Strongly boost signature vibrant artistic colors
            if ((hue >= 295f || hue <= 15f) && sat > 0.30f) {
                score *= 1.8f; // Pinks, Magentas, Roses, Crimson
            } else if (hue >= 165f && hue <= 265f && sat > 0.25f) {
                score *= 1.5f; // Cyan, Teal, Sky Blue, Royal Navy
            } else if (hue >= 265f && hue < 295f && sat > 0.25f) {
                score *= 1.6f; // Violet, Deep Purple
            } else if (hue >= 45f && hue <= 85f && sat > 0.40f) {
                score *= 1.3f; // Lime, Emerald, Fresh Green
            }

            if (score > bestScore) {
                bestScore = score;
                bestSwatch = s;
            }
        }

        if (bestSwatch != null) {
            return bestSwatch.getRgb();
        }

        // Fallbacks
        Palette.Swatch fallback = palette.getVibrantSwatch();
        if (fallback == null) fallback = palette.getLightVibrantSwatch();
        if (fallback == null) fallback = palette.getDarkVibrantSwatch();
        if (fallback == null) fallback = palette.getMutedSwatch();
        if (fallback == null) fallback = palette.getDominantSwatch();

        return fallback != null ? fallback.getRgb() : Color.parseColor("#181B22");
    }

    private int[] getDefaultAppleMusicGradient() {
        return new int[]{
            Color.parseColor("#222834"),
            Color.parseColor("#141820"),
            Color.parseColor("#0E1116")
        };
    }

    public int getCurrentBottomColor() {
        if (currentGradientColors != null && currentGradientColors.length >= 3) {
            return currentGradientColors[2];
        }
        return Color.parseColor("#0E1116");
    }

    private int[] generateAppleMusicGradient(int swatchRgb) {
        float[] hsv = new float[3];
        Color.colorToHSV(swatchRgb, hsv);

        // Apple Music atmospheric balance:
        // 1. Maintain the track's distinctive album hue
        // 2. Bound saturation to keep it atmospheric (rich and elegant, not neon)
        if (hsv[1] > 0.08f) {
            hsv[1] = Math.max(0.40f, Math.min(hsv[1], 0.68f));
        }

        // 3. Center ambient tone (~0.25 brightness for clear color presence)
        float centerV = 0.25f;
        int centerColor = Color.HSVToColor(new float[]{hsv[0], hsv[1], centerV});

        // 4. Top glowing ambient tint (~0.35 brightness) behind album artwork
        float topV = 0.35f;
        int topColor = Color.HSVToColor(new float[]{hsv[0], Math.min(hsv[1] * 1.05f, 0.70f), topV});

        // 5. Bottom deep shade (~0.10 brightness) for high contrast behind playback controls
        float bottomV = 0.10f;
        int bottomColor = Color.HSVToColor(new float[]{hsv[0], hsv[1] * 0.90f, bottomV});

        return new int[]{topColor, centerColor, bottomColor};
    }

    private void applyDynamicBackground(int[] targetColors) {
        if (getView() == null || targetColors == null || targetColors.length < 3) return;
        View root = getView().findViewById(R.id.now_playing_root);
        if (root == null) return;

        if (currentGradientColors == null) {
            // First time load: apply immediately with zero delay
            GradientDrawable initialGradient = new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    targetColors
            );
            root.setBackground(initialGradient);
            currentGradientColors = targetColors.clone();
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).updateNowPlayingBottomNavColor(targetColors[2]);
            }
            return;
        }

        // Smooth crossfade animation between tracks (Apple Music style)
        if (dynamicBgAnimator != null) {
            dynamicBgAnimator.cancel();
            dynamicBgAnimator = null;
        }

        final int[] prevColors = currentGradientColors.clone();
        currentGradientColors = targetColors.clone();

        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).updateNowPlayingBottomNavColor(targetColors[2]);
        }

        dynamicBgAnimator = android.animation.ValueAnimator.ofFloat(0f, 1f);
        dynamicBgAnimator.setDuration(600);
        dynamicBgAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        dynamicBgAnimator.addUpdateListener(animation -> {
            if (root == null) return;
            float fraction = (float) animation.getAnimatedValue();
            int top = blendColors(prevColors[0], targetColors[0], fraction);
            int center = blendColors(prevColors[1], targetColors[1], fraction);
            int bottom = blendColors(prevColors[2], targetColors[2], fraction);

            GradientDrawable gd = new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{top, center, bottom}
            );
            root.setBackground(gd);
        });
        dynamicBgAnimator.start();
    }

    private int blendColors(int from, int to, float ratio) {
        float inverseRatio = 1f - ratio;
        float a = Color.alpha(from) * inverseRatio + Color.alpha(to) * ratio;
        float r = Color.red(from) * inverseRatio + Color.red(to) * ratio;
        float g = Color.green(from) * inverseRatio + Color.green(to) * ratio;
        float b = Color.blue(from) * inverseRatio + Color.blue(to) * ratio;
        return Color.argb((int) a, (int) r, (int) g, (int) b);
    }
    
    private void fetchRealLyrics(Track track) {
        if (track == null || track.getTitle() == null || track.getArtist() == null) return;
        if (track.getDurationMillis() <= 0) {
            MusicPlayerManager manager = MusicPlayerManager.getInstance();
            if (manager != null && manager.getDuration() > 0) {
                track.setDurationMillis(manager.getDuration());
            }
        }
        lyricsRequestKey = track.getArtist() + "\n" + track.getTitle();
        String requestKey = lyricsRequestKey;
        lyricLines.clear();
        lyricLines.add(new LyricLine(-1, "Đang tải lời bài hát cho: " + track.getTitle()));
        populateLyrics();
        
        LyricsApiService lyricsApi = new LyricsApiService();
        lyricsApi.fetchLyrics(track, new LyricsApiService.LyricsCallback() {
            @Override
            public void onSuccess(LyricsApiService.LyricsResult result) {
                if (getActivity() == null || !requestKey.equals(lyricsRequestKey)) return;
                lyricLines.clear();
                if (result.isInstrumental) {
                    lyricLines.add(new LyricLine(-1, "Bài hát không lời (Instrumental)"));
                } else {
                    if (!result.syncedLyrics.trim().isEmpty()) {
                        lyricLines.addAll(parseLrc(result.syncedLyrics));
                    }
                    if (lyricLines.isEmpty() && result.plainLyrics != null) {
                        for (String line : result.plainLyrics.split("\\r?\\n")) {
                            String trimmed = line.trim();
                            if (!trimmed.isEmpty() && !"null".equalsIgnoreCase(trimmed)) {
                                lyricLines.add(new LyricLine(-1, trimmed));
                            }
                        }
                    }
                }
                if (lyricLines.isEmpty()) {
                    lyricLines.add(new LyricLine(-1, "Không tìm thấy lời bài hát cho ca khúc này."));
                } else {
                    Collections.sort(lyricLines, (a, b) -> Long.compare(a.timeMs, b.timeMs));
                }
                populateLyrics();
                MusicPlayerManager manager = MusicPlayerManager.getInstance();
                if (manager != null) {
                    int dur = manager.getDuration();
                    if (dur <= 0 && track.getDurationMillis() > 0) {
                        dur = (int) track.getDurationMillis();
                    }
                    onProgressUpdated(manager.getCurrentPosition(), dur);
                }
            }

            @Override
            public void onError(Exception e) {
                if (getActivity() == null || !requestKey.equals(lyricsRequestKey)) return;
                // Fallback text if lyrics not found
                lyricLines.clear();
                lyricLines.add(new LyricLine(-1, "Không tìm thấy lời bài hát cho ca khúc này."));
                populateLyrics();
            }
        });
    }
    
    private void populateLyrics() {
        if (getView() == null || getContext() == null) return;
        LinearLayout lyricsContainer = getView().findViewById(R.id.now_playing_lyrics_container);
        if (lyricsContainer == null) return;
        
        lyricsContainer.removeAllViews();
        currentLyricIndex = -1;
        
        float density = getResources().getDisplayMetrics().density;
        int padBottom = (int) (24 * density);

        TypedValue rippleValue = new TypedValue();
        int rippleResId = 0;
        if (getContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, rippleValue, true)) {
            rippleResId = rippleValue.resourceId;
        }

        boolean hasAnySyncedLine = false;
        for (LyricLine line : lyricLines) {
            if (line.timeMs >= 0) {
                hasAnySyncedLine = true;
                break;
            }
        }

        for (int i = 0; i < lyricLines.size(); i++) {
            LyricLine line = lyricLines.get(i);
            TextView tv = new TextView(getContext());
            tv.setText(line.text);
            tv.setTextColor(Color.WHITE);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
            tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            tv.setLineSpacing(0, 1.25f);
            tv.setAlpha(hasAnySyncedLine ? 0.35f : 0.95f);
            tv.setScaleX(1.0f);
            tv.setScaleY(1.0f);
            tv.setPivotX(0f);
            tv.setTranslationX(0f);
            tv.setPadding(0, 0, (int) (36 * density), padBottom);
            
            if (line.timeMs >= 0) {
                tv.setClickable(true);
                tv.setFocusable(true);
                if (rippleResId != 0) {
                    tv.setBackgroundResource(rippleResId);
                }
                final int lineIdx = i;
                tv.setOnClickListener(v -> {
                    MusicPlayerManager manager = MusicPlayerManager.getInstance();
                    if (manager != null) {
                        manager.seekTo((int) Math.max(0, line.timeMs - userSongOffsetMs));
                    }
                    isUserScrolling = false;
                    currentLyricIndex = lineIdx;
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    updateLyricsUI(lineIdx, true);
                    if (syncPanel != null && syncPanel.getVisibility() == View.VISIBLE) {
                        updateSyncPanelUI();
                    }
                });
            }
            lyricsContainer.addView(tv);
        }

    }

    @Override
    public void onPlaybackStateChanged(boolean isPlaying) {
        if (getView() == null) return;
        ImageView iconPlayPause = getView().findViewById(R.id.now_playing_icon_play_pause);
        if (iconPlayPause != null) {
            if (isPlaying) {
                iconPlayPause.setImageResource(R.drawable.ic_pause_large);
            } else {
                iconPlayPause.setImageResource(R.drawable.ic_play_large);
            }
        }
    }

    @Override
    public void onProgressUpdated(int currentPos, int duration) {
        if (getView() == null) return;
        
        MusicPlayerManager manager = MusicPlayerManager.getInstance();
        Track currentTrack = manager != null ? manager.getCurrentTrack() : null;
        if (duration <= 0 && currentTrack != null && currentTrack.getDurationMillis() > 0) {
            duration = (int) currentTrack.getDurationMillis();
        }

        if (cachedProgressBar != null) {
            if (duration > 0 && cachedProgressBar.getMax() != duration) {
                cachedProgressBar.setMax(duration);
            }
            cachedProgressBar.setProgress(currentPos);
        }
        
        int currentSec = currentPos / 1000;
        if (currentSec != lastRenderedSecond) {
            lastRenderedSecond = currentSec;
            if (cachedTvCurrent != null) {
                cachedTvCurrent.setText(formatTime(currentPos));
            }
            if (cachedTvTotal != null && duration > 0) {
                cachedTvTotal.setText(formatTime(duration));
            }
        }

        if (lyricLines.isEmpty()) return;

        // Accurate lyrics positioning based on player time + user offset
        long adjustedPos = currentPos + userSongOffsetMs;
        int activeIndex = -1;
        for (int i = 0; i < lyricLines.size(); i++) {
            if (lyricLines.get(i).timeMs >= 0 && lyricLines.get(i).timeMs <= adjustedPos) {
                activeIndex = i;
            }
        }
        
        if (activeIndex != currentLyricIndex) {
            currentLyricIndex = activeIndex;
            updateLyricsUI(activeIndex, true);
        }
    }
    
    private void updateLyricsUI(int activeIndex) {
        updateLyricsUI(activeIndex, true);
    }

    private void updateLyricsUI(int activeIndex, boolean animate) {
        if (getView() == null || getContext() == null || cachedLyricsContainer == null) return;
        
        int childCount = cachedLyricsContainer.getChildCount();
        if (childCount == 0) return;

        boolean isSynced = hasSyncedLyrics();

        if (activeIndex < 0) {
            // Before the first lyric line starts (or user rewinds to intro)
            float targetAlpha = isSynced ? 0.35f : 0.95f;
            for (int i = 0; i < childCount; i++) {
                View child = cachedLyricsContainer.getChildAt(i);
                if (child instanceof TextView) {
                    if (Math.abs(child.getAlpha() - targetAlpha) > 0.05f) {
                        if (animate) {
                            child.animate().alpha(targetAlpha).setDuration(200).start();
                        } else {
                            child.animate().cancel();
                            child.setAlpha(targetAlpha);
                        }
                    }
                }
            }
            previousActiveLyricIndex = -1;
            if (cachedLyricsScroll != null && !isUserScrolling) {
                if (animate && cachedLyricsScroll.getScrollY() > 0) {
                    if (currentScrollAnimator != null && currentScrollAnimator.isRunning()) {
                        currentScrollAnimator.cancel();
                    }
                    currentScrollAnimator = ObjectAnimator.ofInt(cachedLyricsScroll, "scrollY", 0);
                    currentScrollAnimator.setDuration(280);
                    currentScrollAnimator.setInterpolator(new DecelerateInterpolator(1.6f));
                    currentScrollAnimator.start();
                } else {
                    cachedLyricsScroll.scrollTo(0, 0);
                }
            }
            return;
        }

        // Active line exists: ensure activeIndex is highlighted (1.0f) and all other lines are dimmed (0.35f)
        for (int i = 0; i < childCount; i++) {
            View child = cachedLyricsContainer.getChildAt(i);
            if (child instanceof TextView) {
                float targetAlpha = (i == activeIndex) ? 1.0f : 0.35f;
                if (Math.abs(child.getAlpha() - targetAlpha) > 0.05f) {
                    if (animate) {
                        child.animate().alpha(targetAlpha).setDuration(200).start();
                    } else {
                        child.animate().cancel();
                        child.setAlpha(targetAlpha);
                    }
                }
            }
        }
        previousActiveLyricIndex = activeIndex;

        if (!isUserScrolling && cachedLyricsScroll != null && activeIndex < childCount) {
            View activeView = cachedLyricsContainer.getChildAt(activeIndex);
            if (activeView != null) {
                int targetY = Math.max(0, activeView.getTop() - (int) (cachedLyricsScroll.getHeight() * 0.36f));
                if (currentScrollAnimator != null && currentScrollAnimator.isRunning()) {
                    currentScrollAnimator.cancel();
                }
                if (animate && cachedLyricsScroll.getHeight() > 0) {
                    currentScrollAnimator = ObjectAnimator.ofInt(cachedLyricsScroll, "scrollY", targetY);
                    currentScrollAnimator.setDuration(280);
                    currentScrollAnimator.setInterpolator(new DecelerateInterpolator(1.6f));
                    currentScrollAnimator.start();
                } else {
                    cachedLyricsScroll.scrollTo(0, targetY);
                }
            }
        }

        if (isLyricsVisible && !isControlsHiddenInLyrics) {
            cachedLyricsContainer.post(() -> updateBottomLinesBlur(true));
        }
    }

    private List<LyricLine> parseLrc(String syncedLyrics) {
        List<LyricLine> parsed = new ArrayList<>();
        long fileOffsetMs = 0;
        java.util.regex.Pattern offsetPattern = java.util.regex.Pattern.compile("(?i)\\[offset:\\s*([+-]?\\d+)\\]");
        java.util.regex.Pattern linePattern = java.util.regex.Pattern.compile("\\[(\\d+):(\\d{2})(?:\\.(\\d{1,3}))?\\]");
        for (String rawLine : syncedLyrics.split("\\r?\\n")) {
            String trimmed = rawLine.trim();
            if (trimmed.isEmpty()) continue;
            java.util.regex.Matcher offsetMatcher = offsetPattern.matcher(trimmed);
            if (offsetMatcher.find()) {
                try {
                    fileOffsetMs = Long.parseLong(offsetMatcher.group(1));
                } catch (Exception ignored) {}
                continue;
            }
            java.util.regex.Matcher matcher = linePattern.matcher(trimmed);
            List<Long> times = new ArrayList<>();
            int lastEnd = 0;
            while (matcher.find()) {
                long minutes = Long.parseLong(matcher.group(1));
                long seconds = Long.parseLong(matcher.group(2));
                String fraction = matcher.group(3);
                long millis = 0;
                if (fraction != null) {
                    if (fraction.length() == 1) millis = Long.parseLong(fraction) * 100;
                    else if (fraction.length() == 2) millis = Long.parseLong(fraction) * 10;
                    else millis = Long.parseLong(fraction.substring(0, 3));
                }
                long time = Math.max(0, minutes * 60000 + seconds * 1000 + millis + fileOffsetMs);
                times.add(time);
                lastEnd = matcher.end();
            }
            if (!times.isEmpty()) {
                String text = trimmed.substring(lastEnd).trim();
                if (!text.isEmpty() && !"null".equalsIgnoreCase(text)) {
                    for (Long t : times) {
                        parsed.add(new LyricLine(t, text));
                    }
                }
            }
        }
        return parsed;
    }

    private static class LyricLine {
        final long timeMs;
        final String text;

        LyricLine(long timeMs, String text) {
            this.timeMs = timeMs;
            this.text = text;
        }
    }

    private String formatTime(int milliseconds) {
        int totalSeconds = Math.max(0, milliseconds / 1000);
        return String.format(java.util.Locale.US, "%d:%02d", totalSeconds / 60, totalSeconds % 60);
    }

    @Override
    public void onPlaylistsChanged() {
        if (getView() != null) {
            getView().post(() -> {
                Track current = MusicPlayerManager.getInstance().getCurrentTrack();
                updateFavoriteButtonUI(current);
            });
        }
    }
}
