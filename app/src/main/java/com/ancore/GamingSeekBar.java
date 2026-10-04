package com.ancore;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * Lightweight track-only seek bar.
 * The +/- controls are intentionally separate views in XML so they do not
 * become part of the seek bar hit area or visual body.
 */
public class GamingSeekBar extends View {

    private int max = 100;
    private int min = 0;
    private int progress = 50;
    private Paint paint;
    private RectF trackRect = new RectF();
    private RectF progressRect = new RectF();
    private OnProgressChangeListener listener;
    private boolean dragging;

    private int trackBgColor = Color.parseColor("#2A1210");
    private int progressColor = Color.parseColor("#FF0015");
    private int thumbColor = Color.parseColor("#FF0015");

    public interface OnProgressChangeListener {
        void onProgressChanged(GamingSeekBar seekBar, int progress, boolean fromUser);
    }

    public GamingSeekBar(Context context) { super(context); init(); }
    public GamingSeekBar(Context context, AttributeSet attrs) { super(context, attrs); init(); }
    public GamingSeekBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr); init();
    }

    private void init() {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        setFocusable(false);
        setClickable(true);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int defaultHeight = (int) dp(28);
        int defaultWidth = (int) dp(180);
        setMeasuredDimension(
                resolveSize(defaultWidth, widthMeasureSpec),
                resolveSize(defaultHeight, heightMeasureSpec)
        );
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) return;

        float horizontalPad = dp(2);
        float centerY = height * 0.5f;
        float trackHeight = Math.max(dp(3), Math.min(dp(5), height * 0.18f));
        float left = horizontalPad;
        float right = width - horizontalPad;
        float trackWidth = Math.max(1f, right - left);

        trackRect.set(left, centerY - trackHeight / 2f, right, centerY + trackHeight / 2f);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(trackBgColor);
        canvas.drawRoundRect(trackRect, trackHeight / 2f, trackHeight / 2f, paint);

        float fraction = max == min ? 0f : (float)(progress - min) / (float)(max - min);
        fraction = Math.max(0f, Math.min(1f, fraction));
        float thumbX = left + trackWidth * fraction;

        progressRect.set(left, centerY - trackHeight / 2f, thumbX, centerY + trackHeight / 2f);
        paint.setColor(progressColor);
        canvas.drawRoundRect(progressRect, trackHeight / 2f, trackHeight / 2f, paint);

        float thumbW = dp(7);
        float thumbH = Math.min(dp(20), Math.max(dp(16), height - dp(2)));
        RectF thumb = new RectF(
                thumbX - thumbW / 2f, centerY - thumbH / 2f,
                thumbX + thumbW / 2f, centerY + thumbH / 2f
        );
        paint.setColor(thumbColor);
        canvas.drawRoundRect(thumb, dp(2), dp(2), paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragging = true;
                getParent().requestDisallowInterceptTouchEvent(true);
                updateFromX(event.getX());
                return true;
            case MotionEvent.ACTION_MOVE:
                if (dragging) {
                    updateFromX(event.getX());
                    return true;
                }
                return false;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (dragging) updateFromX(event.getX());
                dragging = false;
                getParent().requestDisallowInterceptTouchEvent(false);
                return true;
            default:
                return true;
        }
    }

    private void updateFromX(float x) {
        float left = dp(2);
        float right = getWidth() - dp(2);
        float span = right - left;
        if (span <= 0) return;
        float clamped = Math.max(left, Math.min(right, x));
        float fraction = (clamped - left) / span;
        int value = Math.round(min + fraction * (max - min));
        setProgress(value, true);
    }

    public void setProgress(int value) { setProgress(value, false); }

    private void setProgress(int value, boolean fromUser) {
        int clamped = Math.max(min, Math.min(max, value));
        if (progress == clamped) return;
        progress = clamped;
        invalidate();
        if (listener != null) listener.onProgressChanged(this, progress, fromUser);
    }

    public int getProgress() { return progress; }
    public int getMax() { return max; }
    public int getMin() { return min; }

    public void setMax(int max) {
        this.max = Math.max(min, max);
        progress = Math.max(min, Math.min(this.max, progress));
        invalidate();
    }

    public void setMin(int min) {
        this.min = Math.min(max, min);
        progress = Math.max(this.min, Math.min(max, progress));
        invalidate();
    }

    /** Kept for source compatibility; stepping is handled by external +/- buttons. */
    public void setStep(int step) { }
    public void setButtonSize(float dp) { }
    public void setOnProgressChangeListener(OnProgressChangeListener l) { listener = l; }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
