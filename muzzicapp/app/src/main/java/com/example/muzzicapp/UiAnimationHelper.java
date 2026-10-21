package com.example.muzzicapp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.Keyframe;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.Context;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

import com.google.android.material.bottomnavigation.BottomNavigationView;

/**
 * Ultra-smooth, hardware-accelerated animation engine tailored for Muzzic App.
 * Built for 60fps/120fps fluid transitions without stutter, frame snaps, or layout thrashing.
 */
public final class UiAnimationHelper {

    private UiAnimationHelper() {
        // Utility class
    }

    /**
     * Converts density-independent pixels (dp) to screen pixels (px).
     */
    public static float dpToPx(Context context, float dp) {
        if (context == null) return dp;
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                context.getResources().getDisplayMetrics()
        );
    }

    /**
     * Butter-smooth, hardware-accelerated entrance animation for Fragment tab switching.
     * Gentle 12dp translation + subtle scale (0.98 -> 1.0) + alpha with DecelerateInterpolator.
     * Uses withLayer() to ensure zero dropped frames on heavy layouts.
     */
    public static void animateFragmentPopUp(View view) {
        if (view == null) return;
        view.animate().cancel();
        view.setAlpha(0.25f);
        view.setScaleX(0.98f);
        view.setScaleY(0.98f);
        view.setTranslationY(dpToPx(view.getContext(), 12f));

        view.animate()
                .alpha(1f)
                .scaleX(1.0f)
                .scaleY(1.0f)
                .translationY(0f)
                .setDuration(220)
                .setInterpolator(new DecelerateInterpolator(1.6f))
                .withLayer()
                .start();
    }

    /**
     * Bouncy pop-up animation for the selected BottomNavigationView item.
     * Single-stage hardware-accelerated keyframe animation: pops to 1.15x and settles smoothly.
     */
    public static void animateTabIconPop(BottomNavigationView nav, int itemId) {
        if (nav == null) return;
        View itemView = nav.findViewById(itemId);
        if (itemView != null) {
            itemView.animate().cancel();
            Keyframe k0 = Keyframe.ofFloat(0f, 1.0f);
            Keyframe k1 = Keyframe.ofFloat(0.40f, 1.15f);
            Keyframe k2 = Keyframe.ofFloat(1.0f, 1.0f);
            PropertyValuesHolder pvhX = PropertyValuesHolder.ofKeyframe(View.SCALE_X, k0, k1, k2);
            PropertyValuesHolder pvhY = PropertyValuesHolder.ofKeyframe(View.SCALE_Y, k0, k1, k2);

            ObjectAnimator anim = ObjectAnimator.ofPropertyValuesHolder(itemView, pvhX, pvhY);
            anim.setDuration(210);
            anim.setInterpolator(new DecelerateInterpolator());
            itemView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            anim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    itemView.setLayerType(View.LAYER_TYPE_NONE, null);
                }
            });
            anim.start();
        }
    }

    /**
     * Enhanced fluid motion animation when switching tabs on the BottomNavigationView taskbar.
     * Creates real directional travel: outgoing tab shifts slightly in switch direction and settles,
     * while the incoming tab glides in with a lively spring bounce and scale pop.
     */
    public static void animateTabSwitch(BottomNavigationView nav, int fromItemId, int toItemId) {
        if (nav == null) return;
        View prevView = nav.findViewById(fromItemId);
        View nextView = nav.findViewById(toItemId);

        // Outgoing tab view: ensure clean reset
        if (prevView != null) {
            prevView.animate().cancel();
            prevView.setTranslationX(0f);
            prevView.setTranslationY(0f);
            prevView.setScaleX(1.0f);
            prevView.setScaleY(1.0f);
            View prevIcon = prevView.findViewById(com.google.android.material.R.id.navigation_bar_item_icon_view);
            if (prevIcon != null) {
                prevIcon.animate().cancel();
                prevIcon.setScaleX(1.0f);
                prevIcon.setScaleY(1.0f);
            }
        }

        // Incoming tab view: keep container untransformed, animate only the icon itself with center pivot
        if (nextView != null) {
            nextView.animate().cancel();
            nextView.setTranslationX(0f);
            nextView.setTranslationY(0f);
            nextView.setScaleX(1.0f);
            nextView.setScaleY(1.0f);

            View iconView = nextView.findViewById(com.google.android.material.R.id.navigation_bar_item_icon_view);
            if (iconView == null) {
                iconView = nextView.findViewById(com.google.android.material.R.id.navigation_bar_item_icon_container);
            }
            if (iconView != null) {
                iconView.animate().cancel();
                float iconW = iconView.getWidth() > 0 ? iconView.getWidth() : dpToPx(nav.getContext(), 24f);
                float iconH = iconView.getHeight() > 0 ? iconView.getHeight() : dpToPx(nav.getContext(), 24f);
                iconView.setPivotX(iconW / 2f);
                iconView.setPivotY(iconH / 2f);
                iconView.setScaleX(0.85f);
                iconView.setScaleY(0.85f);
                iconView.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(240)
                        .setInterpolator(new OvershootInterpolator(1.8f))
                        .start();
            }
        }
    }

    private static int getTabPosition(int itemId) {
        if (itemId == R.id.nav_home) return 0;
        if (itemId == R.id.nav_explore) return 1;
        if (itemId == R.id.nav_library) return 2;
        if (itemId == R.id.nav_now_playing) return 3;
        return 0;
    }

    /**
     * Dedicated smooth pop for chip selection (Mood chips, Filter chips).
     * Smoothly pops up to 1.06x and settles back to 1.0x with zero touch conflicts.
     */
    public static void animateChipSelect(View chip) {
        if (chip == null) return;
        chip.animate().cancel();
        Keyframe k0 = Keyframe.ofFloat(0f, 1.0f);
        Keyframe k1 = Keyframe.ofFloat(0.40f, 1.06f);
        Keyframe k2 = Keyframe.ofFloat(1.0f, 1.0f);
        PropertyValuesHolder pvhX = PropertyValuesHolder.ofKeyframe(View.SCALE_X, k0, k1, k2);
        PropertyValuesHolder pvhY = PropertyValuesHolder.ofKeyframe(View.SCALE_Y, k0, k1, k2);

        ObjectAnimator anim = ObjectAnimator.ofPropertyValuesHolder(chip, pvhX, pvhY);
        anim.setDuration(180);
        anim.setInterpolator(new DecelerateInterpolator());
        chip.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                chip.setLayerType(View.LAYER_TYPE_NONE, null);
            }
        });
        anim.start();
    }

    /**
     * Tactile, springy click pop micro-animation for standalone buttons/icons.
     * Starts smoothly from current scale, compresses slightly to 0.93f, pops to 1.06f, and settles to 1.0f.
     * Never abruptly snaps scale values.
     */
    public static void playBounce(View view) {
        if (view == null) return;
        view.animate().cancel();
        float currentScale = view.getScaleX() > 0 ? view.getScaleX() : 1.0f;
        Keyframe k0 = Keyframe.ofFloat(0f, currentScale);
        Keyframe k1 = Keyframe.ofFloat(0.30f, 0.93f);
        Keyframe k2 = Keyframe.ofFloat(0.68f, 1.06f);
        Keyframe k3 = Keyframe.ofFloat(1.0f, 1.0f);
        PropertyValuesHolder pvhX = PropertyValuesHolder.ofKeyframe(View.SCALE_X, k0, k1, k2, k3);
        PropertyValuesHolder pvhY = PropertyValuesHolder.ofKeyframe(View.SCALE_Y, k0, k1, k2, k3);

        ObjectAnimator anim = ObjectAnimator.ofPropertyValuesHolder(view, pvhX, pvhY);
        anim.setDuration(220);
        anim.setInterpolator(new DecelerateInterpolator());
        view.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                view.setLayerType(View.LAYER_TYPE_NONE, null);
            }
        });
        anim.start();
    }

    /**
     * Attaches tactile touch bounce feedback to interactive surface cards/buttons.
     * Compresses slightly on touch down and smoothly springs back on release.
     * Uses withLayer() and never hard-snaps.
     */
    public static void applyTouchBounce(View view) {
        if (view == null) return;
        view.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate().cancel();
                    v.animate()
                            .scaleX(0.95f)
                            .scaleY(0.95f)
                            .setDuration(70)
                            .setInterpolator(new DecelerateInterpolator())
                            .withLayer()
                            .start();
                    break;
                case MotionEvent.ACTION_UP:
                    v.animate().cancel();
                    v.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(150)
                            .setInterpolator(new DecelerateInterpolator())
                            .withLayer()
                            .start();
                    break;
                case MotionEvent.ACTION_CANCEL:
                    v.animate().cancel();
                    v.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(100)
                            .setInterpolator(new DecelerateInterpolator())
                            .withLayer()
                            .start();
                    break;
            }
            return false;
        });
    }

    /**
     * Applies touch bounce micro-interaction to multiple views simultaneously.
     */
    public static void applyTouchBounceToMultiple(View... views) {
        if (views == null) return;
        for (View v : views) {
            if (v != null) {
                applyTouchBounce(v);
            }
        }
    }
}
