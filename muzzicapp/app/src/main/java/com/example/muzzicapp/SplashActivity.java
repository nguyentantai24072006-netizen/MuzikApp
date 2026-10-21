package com.example.muzzicapp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * YouTube-style animated splash screen for MuzzicApp:
 * 1. Initial State: Centered small electric-blue note icon pops into view.
 * 2. Progress State: Sleek horizontal neon loading line smoothly fills up.
 * 3. Logo Reveal: Icon & progress bar morph/fade, revealing the full official Muzzic squircle
 *    logo with an ambient neon radial glow, app title, and tagline with overshoot bounce.
 * 4. Transition: Smooth fade into MainActivity.
 */
public class SplashActivity extends AppCompatActivity {

    private FrameLayout splashRoot;
    private View splashGlow;
    private LinearLayout splashLoadingContainer;
    private ImageView splashIcon;
    private FrameLayout splashProgressContainer;
    private View splashProgressFill;
    private LinearLayout splashLogoContainer;
    private ImageView splashLogo;
    private TextView splashAppName;
    private TextView splashTagline;

    private boolean hasNavigated = false;
    private AnimatorSet activeAnimatorSet;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_splash);

        splashRoot = findViewById(R.id.splash_root);
        splashGlow = findViewById(R.id.splash_glow);
        splashLoadingContainer = findViewById(R.id.splash_loading_container);
        splashIcon = findViewById(R.id.splash_icon);
        splashProgressContainer = findViewById(R.id.splash_progress_container);
        splashProgressFill = findViewById(R.id.splash_progress_fill);
        splashLogoContainer = findViewById(R.id.splash_logo_container);
        splashLogo = findViewById(R.id.splash_logo);
        splashAppName = findViewById(R.id.splash_app_name);
        splashTagline = findViewById(R.id.splash_tagline);

        ViewCompat.setOnApplyWindowInsetsListener(splashRoot, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        // Tap to skip splash directly to MainActivity
        splashRoot.setOnClickListener(v -> launchMainActivity());

        // Start animation once the layout pass is done
        splashProgressContainer.post(this::startAnimation);
    }

    private void startAnimation() {
        if (isFinishing() || isDestroyed()) return;

        // ---------------------------------------------------------------------
        // PHASE 1: Note icon appears with an energetic bounce (YouTube start)
        // ---------------------------------------------------------------------
        splashIcon.setScaleX(0.35f);
        splashIcon.setScaleY(0.35f);
        splashIcon.setAlpha(0f);

        ObjectAnimator noteAlpha = ObjectAnimator.ofFloat(splashIcon, View.ALPHA, 0f, 1f);
        noteAlpha.setDuration(350);
        noteAlpha.setInterpolator(new DecelerateInterpolator());

        ObjectAnimator noteScaleX = ObjectAnimator.ofFloat(splashIcon, View.SCALE_X, 0.35f, 1f);
        ObjectAnimator noteScaleY = ObjectAnimator.ofFloat(splashIcon, View.SCALE_Y, 0.35f, 1f);
        noteScaleX.setDuration(400);
        noteScaleY.setDuration(400);
        noteScaleX.setInterpolator(new OvershootInterpolator(2.2f));
        noteScaleY.setInterpolator(new OvershootInterpolator(2.2f));

        AnimatorSet phase1 = new AnimatorSet();
        phase1.playTogether(noteAlpha, noteScaleX, noteScaleY);

        // ---------------------------------------------------------------------
        // PHASE 2: Progress container fades in & fills 0% to 100%
        // ---------------------------------------------------------------------
        ObjectAnimator progressFadeIn = ObjectAnimator.ofFloat(splashProgressContainer, View.ALPHA, 0f, 1f);
        progressFadeIn.setDuration(220);

        int totalTrackWidth = splashProgressContainer.getWidth();
        if (totalTrackWidth <= 0) {
            totalTrackWidth = dpToPx(160);
        }

        final int targetWidth = totalTrackWidth;
        ValueAnimator fillAnimator = ValueAnimator.ofInt(0, targetWidth);
        fillAnimator.setDuration(750);
        fillAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        fillAnimator.addUpdateListener(animation -> {
            int width = (int) animation.getAnimatedValue();
            ViewGroup.LayoutParams lp = splashProgressFill.getLayoutParams();
            if (lp != null) {
                lp.width = width;
                splashProgressFill.setLayoutParams(lp);
            }
        });

        AnimatorSet phase2 = new AnimatorSet();
        phase2.playTogether(progressFadeIn, fillAnimator);
        phase2.setStartDelay(100);

        // ---------------------------------------------------------------------
        // PHASE 3: Loading fades out & Official Logo reveals with glow + overshoot
        // ---------------------------------------------------------------------
        // Fade out & scale up loading container
        ObjectAnimator loadingFadeOut = ObjectAnimator.ofFloat(splashLoadingContainer, View.ALPHA, 1f, 0f);
        ObjectAnimator loadingScaleX = ObjectAnimator.ofFloat(splashLoadingContainer, View.SCALE_X, 1f, 1.25f);
        ObjectAnimator loadingScaleY = ObjectAnimator.ofFloat(splashLoadingContainer, View.SCALE_Y, 1f, 1.25f);
        loadingFadeOut.setDuration(280);
        loadingScaleX.setDuration(280);
        loadingScaleY.setDuration(280);

        AnimatorSet loadingOut = new AnimatorSet();
        loadingOut.playTogether(loadingFadeOut, loadingScaleX, loadingScaleY);

        // Ambient neon glow expands behind logo
        splashGlow.setScaleX(0.4f);
        splashGlow.setScaleY(0.4f);
        splashGlow.setAlpha(0f);
        ObjectAnimator glowAlpha = ObjectAnimator.ofFloat(splashGlow, View.ALPHA, 0f, 0.75f);
        ObjectAnimator glowScaleX = ObjectAnimator.ofFloat(splashGlow, View.SCALE_X, 0.4f, 1.2f);
        ObjectAnimator glowScaleY = ObjectAnimator.ofFloat(splashGlow, View.SCALE_Y, 0.4f, 1.2f);
        glowAlpha.setDuration(550);
        glowScaleX.setDuration(550);
        glowScaleY.setDuration(550);
        glowAlpha.setInterpolator(new DecelerateInterpolator());

        // Logo Container springs into view
        splashLogoContainer.setScaleX(0.65f);
        splashLogoContainer.setScaleY(0.65f);
        splashLogoContainer.setAlpha(0f);
        ObjectAnimator logoAlpha = ObjectAnimator.ofFloat(splashLogoContainer, View.ALPHA, 0f, 1f);
        ObjectAnimator logoScaleX = ObjectAnimator.ofFloat(splashLogoContainer, View.SCALE_X, 0.65f, 1f);
        ObjectAnimator logoScaleY = ObjectAnimator.ofFloat(splashLogoContainer, View.SCALE_Y, 0.65f, 1f);
        logoAlpha.setDuration(450);
        logoScaleX.setDuration(550);
        logoScaleY.setDuration(550);
        logoScaleX.setInterpolator(new OvershootInterpolator(1.4f));
        logoScaleY.setInterpolator(new OvershootInterpolator(1.4f));

        // Subtitle & tagline slide up smoothly
        splashAppName.setTranslationY(dpToPx(12));
        splashTagline.setTranslationY(dpToPx(12));
        ObjectAnimator titleSlide = ObjectAnimator.ofFloat(splashAppName, View.TRANSLATION_Y, dpToPx(12), 0f);
        ObjectAnimator tagSlide = ObjectAnimator.ofFloat(splashTagline, View.TRANSLATION_Y, dpToPx(12), 0f);
        titleSlide.setDuration(450);
        tagSlide.setDuration(450);
        titleSlide.setInterpolator(new DecelerateInterpolator());
        tagSlide.setInterpolator(new DecelerateInterpolator());

        AnimatorSet logoIn = new AnimatorSet();
        logoIn.playTogether(glowAlpha, glowScaleX, glowScaleY, logoAlpha, logoScaleX, logoScaleY, titleSlide, tagSlide);

        AnimatorSet phase3 = new AnimatorSet();
        phase3.playSequentially(loadingOut, logoIn);

        // ---------------------------------------------------------------------
        // PHASE 4: Hold momentarily, then fade out screen and launch MainActivity
        // ---------------------------------------------------------------------
        ObjectAnimator screenFadeOut = ObjectAnimator.ofFloat(splashRoot, View.ALPHA, 1f, 0f);
        screenFadeOut.setDuration(350);
        screenFadeOut.setStartDelay(650); // Give user time to see the logo
        screenFadeOut.setInterpolator(new AccelerateInterpolator());
        screenFadeOut.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                launchMainActivity();
            }
        });

        // ---------------------------------------------------------------------
        // CHAIN EVERYTHING TOGETHER
        // ---------------------------------------------------------------------
        activeAnimatorSet = new AnimatorSet();
        activeAnimatorSet.playSequentially(phase1, phase2, phase3, screenFadeOut);
        activeAnimatorSet.start();
    }

    private synchronized void launchMainActivity() {
        if (hasNavigated) return;
        hasNavigated = true;

        if (activeAnimatorSet != null && activeAnimatorSet.isRunning()) {
            activeAnimatorSet.cancel();
        }

        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);

        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, android.R.anim.fade_in, android.R.anim.fade_out);
        } else {
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        }
        finish();
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (activeAnimatorSet != null && activeAnimatorSet.isRunning()) {
            activeAnimatorSet.cancel();
        }
    }
}
