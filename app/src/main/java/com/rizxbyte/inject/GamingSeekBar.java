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

    private int max = 100;
    private int min = 0;
    private int progress = 50;
    private int step = 1;

    // Skema warna disesuaikan dengan tema UI Gaming (ROG/Game Space)
    private int buttonBgColor = Color.parseColor("#3B1E16");
    private int buttonTextColor = Color.parseColor("#E5A880");
    private int trackBgColor = Color.parseColor("#2A1210");
    private int progressColor = Color.parseColor("#FF0015");
    private int thumbColor = Color.parseColor("#FF0015");

    private Paint paint;
    private RectF minusBtnRect = new RectF();
    private RectF plusBtnRect = new RectF();
    private RectF trackRect = new RectF();
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
        int defaultHeight = (int) dpToPx(40);
        int defaultWidth = (int) dpToPx(200);

        int width = resolveSize(defaultWidth, widthMeasureSpec);
        int height = resolveSize(defaultHeight, heightMeasureSpec);

        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();

        if (width == 0 || height == 0) return;

        float btnSize = height;
        float btnRadius = dpToPx(6);
        float padding = dpToPx(12);

        // 1. Gambar Tombol Minus (-)
        minusBtnRect.set(0, 0, btnSize, btnSize);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(buttonBgColor);
        canvas.drawRoundRect(minusBtnRect, btnRadius, btnRadius, paint);

        paint.setColor(buttonTextColor);
        paint.setStrokeWidth(dpToPx(3));
        paint.setStrokeCap(Paint.Cap.ROUND);
        canvas.drawLine(btnSize * 0.3f, btnSize * 0.5f, btnSize * 0.7f, btnSize * 0.5f, paint);

        // 2. Gambar Tombol Plus (+)
        plusBtnRect.set(width - btnSize, 0, width, btnSize);
        paint.setColor(buttonBgColor);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(plusBtnRect, btnRadius, btnRadius, paint);

        paint.setColor(buttonTextColor);
        paint.setStrokeWidth(dpToPx(3));
        // Garis Horizontal
        canvas.drawLine(width - btnSize + (btnSize * 0.3f), btnSize * 0.5f, width - (btnSize * 0.3f), btnSize * 0.5f, paint);
        // Garis Vertikal
        canvas.drawLine(width - (btnSize * 0.5f), btnSize * 0.3f, width - (btnSize * 0.5f), btnSize * 0.7f, paint);

        // 3. Gambar Track Background
        float trackLeft = btnSize + padding;
        float trackRight = width - btnSize - padding;
        float trackWidth = trackRight - trackLeft;
        float centerY = height / 2f;
        float trackHeight = dpToPx(4);

        trackRect.set(trackLeft, centerY - (trackHeight / 2f), trackRight, centerY + (trackHeight / 2f));
        paint.setColor(trackBgColor);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(trackRect, trackHeight / 2f, trackHeight / 2f, paint);

        // 4. Gambar Progress Aktif (Merah)
        float progressPercent = (float) (progress - min) / (max - min);
        float currentThumbX = trackLeft + (trackWidth * progressPercent);

        progressRect.set(trackLeft, centerY - (trackHeight / 2f), currentThumbX, centerY + (trackHeight / 2f));
        paint.setColor(progressColor);
        canvas.drawRoundRect(progressRect, trackHeight / 2f, trackHeight / 2f, paint);

        // 5. Gambar Thumb (Batang Tegak Vertikal)
        float thumbWidth = dpToPx(8);
        float thumbHeight = dpToPx(20);
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
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (minusBtnRect.contains(x, y)) {
                    setProgress(progress - step, true);
                    return true;
                } else if (plusBtnRect.contains(x, y)) {
                    setProgress(progress + step, true);
                    return true;
                } else {
                    isDragging = true;
                    updateProgressFromTouch(x);
                    return true;
                }

            case MotionEvent.ACTION_MOVE:
                if (isDragging) {
                    updateProgressFromTouch(x);
                    return true;
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                isDragging = false;
                return true;
        }
        return super.onTouchEvent(event);
    }

    private void updateProgressFromTouch(float x) {
        float btnSize = getHeight();
        float padding = dpToPx(12);
        float trackLeft = btnSize + padding;
        float trackRight = getWidth() - btnSize - padding;
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

    public void setMin(int min) {
        this.min = min;
        invalidate();
    }

    public void setStep(int step) {
        this.step = step;
    }

    public void setOnProgressChangeListener(OnProgressChangeListener listener) {
        this.listener = listener;
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }
}
