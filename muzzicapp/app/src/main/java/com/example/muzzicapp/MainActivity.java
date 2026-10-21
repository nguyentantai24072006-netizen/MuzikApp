package com.example.muzzicapp;

import android.os.Bundle;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.example.muzzicapp.model.Track;


import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity implements MusicPlayerManager.OnPlaybackChangeListener {

    private BottomNavigationView bottomNavigationView;

    // Keep fragment instances to avoid recreation
    private HomeFragment homeFragment;
    private ExploreFragment exploreFragment;
    private LibraryFragment libraryFragment;
    private NowPlayingFragment nowPlayingFragment;
    private Fragment activeFragment;
    private int previousContentTabId = R.id.nav_home;
    private androidx.activity.OnBackPressedCallback backPressedCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        setupFragments(savedInstanceState);
        setupBottomNavigation();
        setupMiniPlayer();

        backPressedCallback = new androidx.activity.OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                collapseNowPlaying();
            }
        };
        getOnBackPressedDispatcher().addCallback(this, backPressedCallback);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);

            // Apply bottom padding to the bottom navigation
            if (bottomNavigationView != null) {
                bottomNavigationView.setPadding(0, 0, 0, systemBars.bottom);
            }
            return insets;
        });
        
        MusicPlayerManager.getInstance(this).addListener(this);
        
        // Request POST_NOTIFICATIONS on Android 13+ for media player notification
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 1001);
            }
        }

        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(android.content.Intent intent) {
        if (intent == null) return;

        if (intent.getBooleanExtra("from_notification", false)) {
            showNowPlayingTab();
            return;
        }

        if (intent.hasExtra("play_title")) {
            String title = intent.getStringExtra("play_title");
            String artist = intent.getStringExtra("play_artist");
            Track track = new Track();
            track.setTitle(title);
            track.setArtist(artist != null ? artist : "Unknown Artist");
            MusicPlayerManager.getInstance(this).playTrack(track);
            if (bottomNavigationView != null) {
                bottomNavigationView.setSelectedItemId(R.id.nav_now_playing);
            }
        }
    }

    public void showExploreTab() {
        if (bottomNavigationView != null) {
            bottomNavigationView.setSelectedItemId(R.id.nav_explore);
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        MusicPlayerManager.getInstance(this).removeListener(this);
    }

    private void setupFragments(Bundle savedInstanceState) {
        if (savedInstanceState == null) {
            homeFragment = new HomeFragment();
            exploreFragment = new ExploreFragment();
            libraryFragment = new LibraryFragment();
            nowPlayingFragment = new NowPlayingFragment();
            
            activeFragment = homeFragment;

            // Add all fragments, hide all except the active one
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.fragment_container, nowPlayingFragment, "now_playing").hide(nowPlayingFragment)
                    .add(R.id.fragment_container, libraryFragment, "library").hide(libraryFragment)
                    .add(R.id.fragment_container, exploreFragment, "explore").hide(exploreFragment)
                    .add(R.id.fragment_container, homeFragment, "home")
                    .commit();
        } else {
            homeFragment = (HomeFragment) getSupportFragmentManager().findFragmentByTag("home");
            exploreFragment = (ExploreFragment) getSupportFragmentManager().findFragmentByTag("explore");
            libraryFragment = (LibraryFragment) getSupportFragmentManager().findFragmentByTag("library");
            nowPlayingFragment = (NowPlayingFragment) getSupportFragmentManager().findFragmentByTag("now_playing");
            
            // Re-find active fragment
            for (Fragment fragment : getSupportFragmentManager().getFragments()) {
                if (fragment != null && fragment.isVisible()) {
                    activeFragment = fragment;
                    break;
                }
            }
            if (activeFragment == null) activeFragment = homeFragment;
        }
    }

    private int currentTabId = R.id.nav_home;

    private void setupBottomNavigation() {
        bottomNavigationView = findViewById(R.id.bottom_navigation);
        bottomNavigationView.setItemActiveIndicatorEnabled(false);
        bottomNavigationView.setItemRippleColor(android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT));
        bottomNavigationView.setSelectedItemId(R.id.nav_home);
        bottomNavigationView.post(() -> moveBottomNavSlider(R.id.nav_home, false));

        bottomNavigationView.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            int newWidth = right - left;
            int oldWidth = oldRight - oldLeft;
            if (newWidth > 0 && newWidth != oldWidth) {
                moveBottomNavSlider(currentTabId, false);
            }
        });

        bottomNavigationView.setOnItemSelectedListener(item -> {
            Fragment selectedFragment;
            int itemId = item.getItemId();

            if (itemId == R.id.nav_home) {
                selectedFragment = homeFragment;
            } else if (itemId == R.id.nav_explore) {
                selectedFragment = exploreFragment;
            } else if (itemId == R.id.nav_library) {
                selectedFragment = libraryFragment;
            } else if (itemId == R.id.nav_now_playing) {
                selectedFragment = nowPlayingFragment;
            } else {
                return false;
            }

            int oldTabId = currentTabId;
            currentTabId = itemId;

            if (selectedFragment != activeFragment) {
                // Animate bottom nav taskbar motion: directional glow lamp beam and vertical bounce
                UiAnimationHelper.animateTabSwitch(bottomNavigationView, oldTabId, itemId);
                moveBottomNavSlider(itemId, true);

                // If leaving a main content tab (Home, Explore, Library), save it
                if (activeFragment == homeFragment) {
                    previousContentTabId = R.id.nav_home;
                } else if (activeFragment == exploreFragment) {
                    previousContentTabId = R.id.nav_explore;
                } else if (activeFragment == libraryFragment) {
                    previousContentTabId = R.id.nav_library;
                }

                getSupportFragmentManager().beginTransaction()
                        .hide(activeFragment)
                        .show(selectedFragment)
                        .commit();
                activeFragment = selectedFragment;

                // Animate incoming fragment with fluid pop-up effect
                if (selectedFragment.getView() != null) {
                    UiAnimationHelper.animateFragmentPopUp(selectedFragment.getView());
                }
                
                // Hide mini player and seamlessly blend bottom nav in Now Playing screen
                android.view.View miniPlayer = findViewById(R.id.mini_player_container);
                android.view.View navSlider = findViewById(R.id.bottom_nav_slider);
                if (itemId == R.id.nav_now_playing) {
                    if (miniPlayer != null) miniPlayer.setVisibility(android.view.View.GONE);
                    if (navSlider != null) navSlider.setVisibility(android.view.View.GONE);
                    if (nowPlayingFragment != null && bottomNavigationView != null) {
                        bottomNavigationView.setBackgroundColor(nowPlayingFragment.getCurrentBottomColor());
                    }
                    if (backPressedCallback != null) backPressedCallback.setEnabled(true);
                } else {
                    if (miniPlayer != null) miniPlayer.setVisibility(android.view.View.VISIBLE);
                    if (navSlider != null) navSlider.setVisibility(android.view.View.VISIBLE);
                    if (bottomNavigationView != null) {
                        bottomNavigationView.setBackgroundResource(R.color.nav_bg);
                    }
                    if (backPressedCallback != null) backPressedCallback.setEnabled(false);
                }
            } else {
                moveBottomNavSlider(itemId, true);
                // When reselecting Explore tab, reset it to root screen
                if (itemId == R.id.nav_explore && exploreFragment != null) {
                    exploreFragment.resetToMainScreen();
                }
            }

            return true;
        });

        // Listen for tab reselection (tapping an already selected tab)
        bottomNavigationView.setOnItemReselectedListener(item -> {
            if (item.getItemId() == R.id.nav_explore && exploreFragment != null) {
                exploreFragment.resetToMainScreen();
            }
            // Only Explore tab performs reset, other tabs remain unaffected
        });
    }

    private int getTabPosition(int itemId) {
        if (itemId == R.id.nav_home) return 0;
        if (itemId == R.id.nav_explore) return 1;
        if (itemId == R.id.nav_library) return 2;
        if (itemId == R.id.nav_now_playing) return 3;
        return 0;
    }

    private void moveBottomNavSlider(int targetItemId, boolean animate) {
        if (bottomNavigationView == null) return;
        android.view.View slider = findViewById(R.id.bottom_nav_slider);
        android.view.View container = findViewById(R.id.bottom_nav_container);
        if (slider == null || container == null) return;

        Runnable moveAction = () -> {
            int tabIndex = getTabPosition(targetItemId);

            float totalWidth = bottomNavigationView.getWidth() > 0 ? bottomNavigationView.getWidth() : container.getWidth();
            if (totalWidth <= 0) {
                totalWidth = getResources().getDisplayMetrics().widthPixels;
            }

            float targetCenterX;
            android.view.ViewGroup menuView = (bottomNavigationView.getChildCount() > 0 && bottomNavigationView.getChildAt(0) instanceof android.view.ViewGroup)
                    ? (android.view.ViewGroup) bottomNavigationView.getChildAt(0)
                    : null;

            if (menuView != null && tabIndex >= 0 && tabIndex < menuView.getChildCount()) {
                android.view.View tabView = menuView.getChildAt(tabIndex);
                if (tabView != null && tabView.getWidth() > 0) {
                    targetCenterX = bottomNavigationView.getLeft() + menuView.getLeft() + tabView.getLeft() + tabView.getWidth() / 2f;
                } else {
                    float tabWidth = totalWidth / 4f;
                    targetCenterX = (tabIndex + 0.5f) * tabWidth;
                }
            } else {
                float tabWidth = totalWidth / 4f;
                targetCenterX = (tabIndex + 0.5f) * tabWidth;
            }

            float sliderW = slider.getWidth() > 0 ? slider.getWidth() : UiAnimationHelper.dpToPx(this, 96f);
            float targetX = targetCenterX - sliderW / 2f;

            slider.animate().cancel();
            slider.setScaleX(1.0f);
            slider.setScaleY(1.0f);
            slider.setPivotX(sliderW / 2f);
            slider.setPivotY(0f);

            if (!animate) {
                slider.setTranslationX(targetX);
                slider.setAlpha(1.0f);
            } else {
                // Smoothly glides across between tabs: gentle, graceful speed (340ms with smooth cubic ease)
                slider.animate()
                        .translationX(targetX)
                        .setDuration(340)
                        .setInterpolator(new android.view.animation.PathInterpolator(0.25f, 0.1f, 0.25f, 1.0f))
                        .start();
            }
        };

        if (bottomNavigationView.getWidth() > 0) {
            moveAction.run();
        } else {
            bottomNavigationView.post(moveAction);
        }
    }

    private void setupMiniPlayer() {
        LinearLayout miniPlayerContainer = findViewById(R.id.mini_player_container);
        ImageView miniPlayerPause = findViewById(R.id.mini_player_pause);
        ImageView miniPlayerNext = findViewById(R.id.mini_player_next);

        // Tactile touch bounce feedback on interactive elements
        UiAnimationHelper.applyTouchBounce(miniPlayerContainer);
        UiAnimationHelper.applyTouchBounce(miniPlayerPause);
        UiAnimationHelper.applyTouchBounce(miniPlayerNext);
        miniPlayerPause.setColorFilter(0xFFFFFFFF);
        miniPlayerNext.setColorFilter(0xFFFFFFFF);

        // Click on the mini player expands to Now Playing
        miniPlayerContainer.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
            if (bottomNavigationView != null) {
                bottomNavigationView.setSelectedItemId(R.id.nav_now_playing);
            }
        });

        miniPlayerPause.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
            MusicPlayerManager.getInstance(this).togglePlayPause();
        });

        miniPlayerNext.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
            MusicPlayerManager.getInstance(this).nextTrack();
        });
    }

    public void showHomeTab() {
        if (bottomNavigationView != null) {
            bottomNavigationView.setSelectedItemId(R.id.nav_home);
        }
    }

    public void collapseNowPlaying() {
        int targetTab = (previousContentTabId != 0 && previousContentTabId != R.id.nav_now_playing)
                ? previousContentTabId
                : R.id.nav_home;
        if (bottomNavigationView != null) {
            bottomNavigationView.setSelectedItemId(targetTab);
        }
    }

    public void showNowPlayingTab() {
        if (bottomNavigationView != null) {
            bottomNavigationView.setSelectedItemId(R.id.nav_now_playing);
        }
    }

    public void updateNowPlayingBottomNavColor(int bottomColor) {
        if (currentTabId == R.id.nav_now_playing && bottomNavigationView != null) {
            bottomNavigationView.setBackgroundColor(bottomColor);
        }
    }

    public void showLibraryRecordingsTab() {
        if (bottomNavigationView != null) {
            bottomNavigationView.setSelectedItemId(R.id.nav_library);
            if (libraryFragment != null) {
                libraryFragment.selectRecordingsFilter();
            }
        }
    }

    @Override
    public void onTrackChanged(Track track) {
        TextView tvTitle = findViewById(R.id.mini_player_title);
        TextView tvArtist = findViewById(R.id.mini_player_artist);
        ImageView ivArt = findViewById(R.id.mini_player_thumb);
        
        if (track != null) {
            if (tvTitle != null) tvTitle.setText(track.getTitle());
            if (tvArtist != null) tvArtist.setText(track.getArtist());
            if (ivArt != null && track.getArtworkUrl() != null && !track.getArtworkUrl().trim().isEmpty()) {
                Glide.with(this)
                     .load(track.getArtworkUrl())
                     .override(120, 120)
                     .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                     .placeholder(R.drawable.bg_thumb_purple)
                     .error(R.drawable.bg_thumb_purple)
                     .into(ivArt);
            }
        }
    }

    @Override
    public void onPlaybackStateChanged(boolean isPlaying) {
        ImageView miniPlayerPause = findViewById(R.id.mini_player_pause);
        if (miniPlayerPause != null) {
            miniPlayerPause.setImageResource(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play_large);
            miniPlayerPause.setColorFilter(0xFFFFFFFF);
        }
    }

    @Override
    public void onProgressUpdated(int currentPos, int duration) {
        // Mini player doesn't have a progress bar, ignore
    }
}