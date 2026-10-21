package com.example.muzzicapp.widget;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.Nullable;

/**
 * Custom View that renders an ultra-realistic, atmospheric overhead spotlight beam
 * ("ánh đèn sân khấu chiếu rọi từ trên xuống dưới") for the bottom navigation bar.
 *
 * Visual design principles:
 * 1. Natural optical beam profile (parabolic flare) using curved paths with Gaussian feathering.
 * 2. Ethereal, recessed top light aperture (seamless radiance, eliminating any cartoonish UFO/casing).
 * 3. Multi-layer volumetric density: Ambient haze + main cone + luminous central ray.
 * 4. Ground illumination: Soft elliptical pool of light that bathes the active icon in a real beam.
 * 5. Subtle micro-motes illuminated in the beam for cinematic realism.
 */
public class SpotlightBeamView extends View {

    // Paints
    private final Paint ambientHazePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mainBeamPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint coreRayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint topHaloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint emitterAperturePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint emitterCorePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Geometry Paths & Rects
    private final Path ambientHazePath = new Path();
    private final Path mainBeamPath = new Path();
    private final Path coreRayPath = new Path();
    private final RectF emitterApertureRect = new RectF();
    private final RectF emitterCoreRect = new RectF();

    private float topHaloRadius = 0f;

    public SpotlightBeamView(Context context) {
        super(context);
        init();
    }

    public SpotlightBeamView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SpotlightBeamView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setClickable(false);
        setFocusable(false);
        // Enable software layer for high-precision Gaussian blur & feathered optical masking
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return false;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w <= 0 || h <= 0) return;

        float density = getResources().getDisplayMetrics().density;
        float cx = w / 2f;

        // 1. Sleek top recessed emitter aperture (A delicate glowing slit)
        float slitW = 18f * density;
        float slitH = 1.0f * density;
        emitterApertureRect.set(cx - slitW / 2f, 0f, cx + slitW / 2f, slitH);

        LinearGradient slitShader = new LinearGradient(
                cx - slitW / 2f, 0f,
                cx + slitW / 2f, 0f,
                new int[]{0x0000E5FF, 0x5500E5FF, 0xFFFFFFFF, 0x5500E5FF, 0x0000E5FF},
                new float[]{0.0f, 0.25f, 0.5f, 0.75f, 1.0f},
                Shader.TileMode.CLAMP
        );
        emitterAperturePaint.setShader(slitShader);
        emitterAperturePaint.setStyle(Paint.Style.FILL);

        // Brilliant white micro filament in the center
        float coreW = 6f * density;
        float coreH = 0.8f * density;
        emitterCoreRect.set(cx - coreW / 2f, 0f, cx + coreW / 2f, coreH);
        emitterCorePaint.setColor(0xFFFFFFFF);
        emitterCorePaint.setStyle(Paint.Style.FILL);

        // 2. Optical Halation / Bloom around the top emitter
        topHaloRadius = 13f * density;
        RadialGradient topHaloShader = new RadialGradient(
                cx, 0f,
                topHaloRadius,
                new int[]{0x65FFFFFF, 0x2A00E5FF, 0x0C00E5FF, 0x00000000},
                new float[]{0.0f, 0.25f, 0.60f, 1.0f},
                Shader.TileMode.CLAMP
        );
        topHaloPaint.setShader(topHaloShader);

        // 3. Ambient Volumetric Outer Haze:
        // Broad, gentle flare fanning outward ("tản nhẹ ra"), reaching mid-icon and dissolving to transparent
        float hazeTopW = 12f * density;
        float hazeBottomW = 86f * density; // Generous wide spread, avoiding any hugging of icon contour
        float hazeH = 40f * density;       // Extends softly past mid-icon (~30dp) and dissolves before text

        ambientHazePath.reset();
        ambientHazePath.moveTo(cx - hazeTopW / 2f, 0f);
        ambientHazePath.quadTo(cx - hazeTopW / 2f - 12f * density, hazeH * 0.45f, cx - hazeBottomW / 2f, hazeH);
        ambientHazePath.lineTo(cx + hazeBottomW / 2f, hazeH);
        ambientHazePath.quadTo(cx + hazeTopW / 2f + 12f * density, hazeH * 0.45f, cx + hazeTopW / 2f, 0f);
        ambientHazePath.close();

        LinearGradient hazeShader = new LinearGradient(
                cx, 0f,
                cx, hazeH,
                new int[]{0x3000E5FF, 0x2000E5FF, 0x1000B0FF, 0x0400B0FF, 0x00000000},
                new float[]{0.0f, 0.35f, 0.65f, 0.82f, 1.0f},
                Shader.TileMode.CLAMP
        );
        ambientHazePaint.setShader(hazeShader);
        ambientHazePaint.setStyle(Paint.Style.FILL);
        ambientHazePaint.setMaskFilter(new BlurMaskFilter(14f * density, BlurMaskFilter.Blur.NORMAL));

        // 4. Main Spotlight Cone:
        // Fanned optical beam that illuminates the upper half of the icon, then fades away at the icon center
        float beamTopW = 7f * density;
        float beamBottomW = 66f * density; // Wide flare so it does not hug the 24dp icon
        float beamH = 34f * density;       // Reaches through the middle of the icon (~30dp) then fades away

        mainBeamPath.reset();
        mainBeamPath.moveTo(cx - beamTopW / 2f, 0f);
        mainBeamPath.quadTo(cx - beamTopW / 2f - 8f * density, beamH * 0.45f, cx - beamBottomW / 2f, beamH);
        mainBeamPath.lineTo(cx + beamBottomW / 2f, beamH);
        mainBeamPath.quadTo(cx + beamTopW / 2f + 8f * density, beamH * 0.45f, cx + beamTopW / 2f, 0f);
        mainBeamPath.close();

        LinearGradient beamShader = new LinearGradient(
                cx, 0f,
                cx, beamH,
                new int[]{0x5500E5FF, 0x3600E5FF, 0x1A00B0FF, 0x0700B0FF, 0x00000000},
                new float[]{0.0f, 0.30f, 0.60f, 0.80f, 1.0f},
                Shader.TileMode.CLAMP
        );
        mainBeamPaint.setShader(beamShader);
        mainBeamPaint.setStyle(Paint.Style.FILL);
        mainBeamPaint.setMaskFilter(new BlurMaskFilter(7f * density, BlurMaskFilter.Blur.NORMAL));

        // 5. Intense Core Central Ray:
        // Soft white-cyan highlight down the upper axis, fading completely right at the icon center (~28dp)
        float rayTopW = 3f * density;
        float rayBottomW = 26f * density;
        float rayH = 28f * density;

        coreRayPath.reset();
        coreRayPath.moveTo(cx - rayTopW / 2f, 0f);
        coreRayPath.quadTo(cx - rayTopW / 2f - 3f * density, rayH * 0.40f, cx - rayBottomW / 2f, rayH);
        coreRayPath.lineTo(cx + rayBottomW / 2f, rayH);
        coreRayPath.quadTo(cx + rayTopW / 2f + 3f * density, rayH * 0.40f, cx + rayTopW / 2f, 0f);
        coreRayPath.close();

        LinearGradient coreShader = new LinearGradient(
                cx, 0f,
                cx, rayH,
                new int[]{0x78FFFFFF, 0x3DE0F7FA, 0x1400E5FF, 0x00000000},
                new float[]{0.0f, 0.25f, 0.65f, 1.0f},
                Shader.TileMode.CLAMP
        );
        coreRayPaint.setShader(coreShader);
        coreRayPaint.setStyle(Paint.Style.FILL);
        coreRayPaint.setMaskFilter(new BlurMaskFilter(3.5f * density, BlurMaskFilter.Blur.NORMAL));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float cx = getWidth() / 2f;

        // 1. Draw outer ambient volumetric haze (deep soft Gaussian blur, wide flare, zero hard borders)
        canvas.drawPath(ambientHazePath, ambientHazePaint);

        // 2. Draw main focused spotlight beam (covers upper icon, fades smoothly at mid-icon)
        canvas.drawPath(mainBeamPath, mainBeamPaint);

        // 3. Draw central luminous core ray
        canvas.drawPath(coreRayPath, coreRayPaint);

        // 4. Draw top optical bloom/halation
        if (topHaloRadius > 0) {
            canvas.drawCircle(cx, 0f, topHaloRadius, topHaloPaint);
        }

        // 5. Draw sleek recessed aperture slit (delicate optical emitter line)
        float r = emitterApertureRect.height() / 2f;
        canvas.drawRoundRect(emitterApertureRect, r, r, emitterAperturePaint);

        // 6. Draw brilliant white pinpoint filament at center of emitter
        float cr = emitterCoreRect.height() / 2f;
        canvas.drawRoundRect(emitterCoreRect, cr, cr, emitterCorePaint);
    }
}
