package com.ankita.h2sdosimeter.ui.dashboard;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.ankita.h2sdosimeter.R;

/**
 * ExposureGaugeView - custom circular ring gauge.
 *
 * Draws a 270deg arc ring showing the estimated exposure level.
 * Includes an animated fill on attach.
 *
 * Colour changes automatically based on exposure level:
 *   LOW      -> teal/green
 *   ELEVATED -> amber
 *   HIGH     -> red
 *
 * IMPORTANT: The value displayed is a DEMO indicator only.
 * It does NOT represent a validated H2S measurement.
 */
public class ExposureGaugeView extends View {

    // -- constants --------------------------------------
    /** Total arc sweep in degrees (270deg = 3/4 circle) */
    private static final float SWEEP_DEGREES = 270f;
    /** Start angle: 135deg puts the gap at the bottom-center */
    private static final float START_ANGLE = 135f;
    /** Stroke width as fraction of view size */
    private static final float STROKE_FRACTION = 0.09f;
    /** Animation duration ms */
    private static final long ANIM_DURATION = 1200L;

    // -- state ------------------------------------------
    /** 0.0 - 1.0: current animated fill fraction */
    private float fillFraction = 0f;

    /** Target fill fraction (set by caller) */
    private float targetFraction = 0.3f; // default: LOW demo

    public enum Level { LOW, ELEVATED, HIGH }
    private Level level = Level.LOW;

    // -- paint objects ----------------------------------
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint demoPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);

    // -- rect for arc drawing ---------------------------
    private final RectF arcRect = new RectF();

    public ExposureGaugeView(@NonNull Context context) {
        super(context);
        init(context);
    }

    public ExposureGaugeView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public ExposureGaugeView(@NonNull Context context,
                              @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        // Track (background ring)
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeCap(Paint.Cap.ROUND);
        trackPaint.setColor(ContextCompat.getColor(context, R.color.colorGaugeTrack));

        // Fill arc
        fillPaint.setStyle(Paint.Style.STROKE);
        fillPaint.setStrokeCap(Paint.Cap.ROUND);

        // Center value text
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);
        textPaint.setColor(ContextCompat.getColor(context, R.color.colorTextPrimary));

        // Center sub-label ("ESTIMATED")
        labelPaint.setStyle(Paint.Style.FILL);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setColor(ContextCompat.getColor(context, R.color.colorTextHint));

        // DEMO label
        demoPaint.setStyle(Paint.Style.FILL);
        demoPaint.setTextAlign(Paint.Align.CENTER);
        demoPaint.setFakeBoldText(true);
        demoPaint.setColor(ContextCompat.getColor(context, R.color.colorDemoLabel));
    }

    // -- Public API -------------------------------------

    /**
     * Set the exposure level to display.
     * @param fraction  0.0-1.0 (demo value only)
     * @param level     LOW / ELEVATED / HIGH
     */
    public void setExposureLevel(float fraction, Level level) {
        this.targetFraction = Math.max(0f, Math.min(1f, fraction));
        this.level = level;
        updateFillColour();
        animateFill();
    }

    // -- Drawing ----------------------------------------

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        float size = Math.min(w, h);
        float cx = w / 2f;
        float cy = h / 2f;

        float strokeWidth = size * STROKE_FRACTION;
        trackPaint.setStrokeWidth(strokeWidth);
        fillPaint.setStrokeWidth(strokeWidth);

        float padding = strokeWidth / 2f + 8f;
        arcRect.set(cx - size / 2f + padding,
                    cy - size / 2f + padding,
                    cx + size / 2f - padding,
                    cy + size / 2f - padding);

        // Track
        canvas.drawArc(arcRect, START_ANGLE, SWEEP_DEGREES, false, trackPaint);

        // Fill
        float sweep = SWEEP_DEGREES * fillFraction;
        if (sweep > 0) {
            canvas.drawArc(arcRect, START_ANGLE, sweep, false, fillPaint);
        }

        // Center text: level name
        String levelText = level == Level.HIGH ? "HIGH"
                         : level == Level.ELEVATED ? "ELEV."
                         : "LOW";
        textPaint.setTextSize(size * 0.15f);
        canvas.drawText(levelText, cx, cy - size * 0.04f, textPaint);

        // Sub-label
        labelPaint.setTextSize(size * 0.09f);
        canvas.drawText("ESTIMATED", cx, cy + size * 0.10f, labelPaint);

        // DEMO badge text
        demoPaint.setTextSize(size * 0.08f);
        canvas.drawText("[ DEMO ]", cx, cy + size * 0.21f, demoPaint);
    }

    // -- Animation --------------------------------------

    private void animateFill() {
        ValueAnimator anim = ValueAnimator.ofFloat(0f, targetFraction);
        anim.setDuration(ANIM_DURATION);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.addUpdateListener(a -> {
            fillFraction = (float) a.getAnimatedValue();
            invalidate();
        });
        anim.start();
    }

    private void updateFillColour() {
        int color;
        switch (level) {
            case HIGH:
                color = ContextCompat.getColor(getContext(), R.color.colorGaugeHigh);
                textPaint.setColor(ContextCompat.getColor(getContext(), R.color.colorStatusDanger));
                break;
            case ELEVATED:
                color = ContextCompat.getColor(getContext(), R.color.colorGaugeMedium);
                textPaint.setColor(ContextCompat.getColor(getContext(), R.color.colorStatusWarning));
                break;
            default:
                color = ContextCompat.getColor(getContext(), R.color.colorGaugeLow);
                textPaint.setColor(ContextCompat.getColor(getContext(), R.color.colorStatusSafe));
                break;
        }
        fillPaint.setColor(color);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateFillColour();
        animateFill();
    }
}
