package com.rizxbyte.inject;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class GamingSeekBar extends View {

    private int max = 900;
    private int min = 0;
    private int progress = 0;

    // Warna disesuaikan dengan tema UI overlay merah/hitam
    private int trackBgColor  = Color.parseColor("#252530");
    private int progressColor = Color.parseColor("#E61F33");
    private int thumbColor    = Color.parseColor("#E61F33");

    private Paint paint;
    private RectF trackRect    = new RectF();
    private RectF progressRect = new RectF();

    private OnProgressChangeListener listener;

    public interface OnProgressChangeListener {
        void onProgressChanged(GamingSeekBar seekBar, int progress, boolean fromUser);
    }

    public GamingSeekBar(Context context) {
        super(context);
        init();
    }

    public GamingSeekBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public GamingSeekBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int defaultHeight = (int) dpToPx(28);
        int defaultWidth  = (int) dpToPx(150);

        int width  = resolveSize(defaultWidth, widthMeasureSpec);
        int height = resolveSize(defaultHeight, heightMeasureSpec);

        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();

        if (width == 0 || height == 0) return;

        float padding = dpToPx(4);
        float trackLeft = padding;
        float trackRight = width - padding;
        float trackWidth = trackRight - trackLeft;
        float centerY = height / 2f;
        float trackHeight = dpToPx(6);

        // 1. Draw Track Background
        trackRect.set(trackLeft, centerY - (trackHeight / 2f), trackRight, centerY + (trackHeight / 2f));
        paint.setColor(trackBgColor);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(trackRect, trackHeight / 2f, trackHeight / 2f, paint);

        // 2. Calculate Thumb Position
        float progressPercent = (float) (progress - min) / (float) Math.max(1, (max - min));
        float currentThumbX = trackLeft + (trackWidth * progressPercent);

        // 3. Draw Active Progress
        progressRect.set(trackLeft, centerY - (trackHeight / 2f), currentThumbX, centerY + (trackHeight / 2f));
        paint.setColor(progressColor);
        canvas.drawRoundRect(progressRect, trackHeight / 2f, trackHeight / 2f, paint);

        // 4. Draw Thumb Vertical Bar
        float thumbWidth = dpToPx(6);
        float thumbHeight = dpToPx(16);
        RectF thumbRect = new RectF(
                currentThumbX - (thumbWidth / 2f),
                centerY - (thumbHeight / 2f),
                currentThumbX + (thumbWidth / 2f),
                centerY + (thumbHeight / 2f)
        );
        paint.setColor(thumbColor);
        canvas.drawRoundRect(thumbRect, dpToPx(2), dpToPx(2), paint);
    }

    private boolean isDragging = false;

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                isDragging = true;
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                updateProgressFromTouch(x);
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                isDragging = false;
                return true;
        }
        return super.onTouchEvent(event);
    }

    private void updateProgressFromTouch(float x) {
        float padding = dpToPx(4);
        float trackLeft = padding;
        float trackRight = getWidth() - padding;
        float trackWidth = trackRight - trackLeft;

        if (trackWidth <= 0) return;

        float clampedX = Math.max(trackLeft, Math.min(x, trackRight));
        float fraction = (clampedX - trackLeft) / trackWidth;
        int newProgress = Math.round(min + fraction * (max - min));

        setProgress(newProgress, true);
    }

    public void setProgress(int newProgress) {
        setProgress(newProgress, false);
    }

    private void setProgress(int newProgress, boolean fromUser) {
        int clamped = Math.max(min, Math.min(newProgress, max));
        if (this.progress != clamped) {
            this.progress = clamped;
            invalidate();
            if (listener != null) {
                listener.onProgressChanged(this, this.progress, fromUser);
            }
        }
    }

    public int getProgress() {
        return progress;
    }

    public void setMax(int max) {
        this.max = max;
        invalidate();
    }

    public int getMax() {
        return max;
    }

    public void setMin(int min) {
        this.min = min;
        invalidate();
    }

    public void setOnProgressChangeListener(OnProgressChangeListener listener) {
        this.listener = listener;
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }
}
