// app/src/main/java/com/ancore/GamingSeekBar.java
package com.ancore;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class GamingSeekBar extends View {

    private int max      = 900;
    private int min      = 0;
    private int progress = 0;

    private int trackBgColor  = Color.parseColor("#252530");
    private int progressColor = Color.parseColor("#E61F33");
    private int thumbColor    = Color.parseColor("#E61F33");

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF trackRect    = new RectF();
    private final RectF progressRect = new RectF();
    private final RectF thumbRect    = new RectF();

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
        // Matikan scrollbar bawaan Android dan efek overscroll/glow
        setHorizontalScrollBarEnabled(false);
        setVerticalScrollBarEnabled(false);
        setOverScrollMode(View.OVER_SCROLL_NEVER);
        setScrollbarFadingEnabled(false);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = resolveSize((int) dpToPx(150), widthMeasureSpec);
        int h = resolveSize((int) dpToPx(28),  heightMeasureSpec);
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int width  = getWidth();
        int height = getHeight();
        if (width == 0 || height == 0) return;

        float pad         = dpToPx(4);
        float trackLeft   = pad;
        float trackRight  = width - pad;
        float trackWidth  = trackRight - trackLeft;
        float centerY     = height / 2f;
        float trackHeight = dpToPx(6);

        // 1. Track background
        trackRect.set(trackLeft, centerY - trackHeight / 2f,
                      trackRight, centerY + trackHeight / 2f);
        paint.setColor(trackBgColor);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(trackRect, trackHeight / 2f, trackHeight / 2f, paint);

        // 2. Progress fill
        float pct      = (float)(progress - min) / Math.max(1, max - min);
        float thumbX   = trackLeft + trackWidth * pct;
        progressRect.set(trackLeft, centerY - trackHeight / 2f,
                         thumbX, centerY + trackHeight / 2f);
        paint.setColor(progressColor);
        canvas.drawRoundRect(progressRect, trackHeight / 2f, trackHeight / 2f, paint);

        // 3. Thumb bar (tanpa glow, tanpa shadow)
        float tw = dpToPx(8);
        float th = dpToPx(18);
        thumbRect.set(thumbX - tw / 2f, centerY - th / 2f,
                      thumbX + tw / 2f, centerY + th / 2f);
        paint.setColor(thumbColor);
        canvas.drawRoundRect(thumbRect, dpToPx(2), dpToPx(2), paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                if (getParent() != null)
                    getParent().requestDisallowInterceptTouchEvent(true);
                updateFromTouch(event.getX());
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                return true;
        }
        return super.onTouchEvent(event);
    }

    private void updateFromTouch(float x) {
        float pad        = dpToPx(4);
        float trackLeft  = pad;
        float trackRight = getWidth() - pad;
        float trackWidth = trackRight - trackLeft;
        if (trackWidth <= 0) return;

        float clamped = Math.max(trackLeft, Math.min(x, trackRight));
        int newProg   = Math.round(min + (clamped - trackLeft) / trackWidth * (max - min));
        setProgress(newProg, true);
    }

    public void setProgress(int value) {
        setProgress(value, false);
    }

    private void setProgress(int value, boolean fromUser) {
        int clamped = Math.max(min, Math.min(value, max));
        if (progress != clamped) {
            progress = clamped;
            invalidate();
            if (listener != null)
                listener.onProgressChanged(this, progress, fromUser);
        }
    }

    public int getProgress() { return progress; }
    public int getMax()      { return max; }

    public void setMax(int max) { this.max = max; invalidate(); }
    public void setMin(int min) { this.min = min; invalidate(); }

    public void setOnProgressChangeListener(OnProgressChangeListener l) {
        this.listener = l;
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }
}