package com.ancore;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class GamingSeekBar extends View {

    private int max = 100;
    private int min = 0;
    private int progress = 50;
    private int step = 1;

    // =========================
    // UKURAN BUTTON
    // =========================
    // Ubah angka ini untuk mengatur ukuran button - dan +
    private float buttonSizeDp = 28f;

    // Warna
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
    private Path buttonPath = new Path();

    private OnProgressChangeListener listener;

    public interface OnProgressChangeListener {
        void onProgressChanged(
                GamingSeekBar seekBar,
                int progress,
                boolean fromUser
        );
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

        // =========================
        // UKURAN BUTTON
        // =========================
        float btnSize = dpToPx(buttonSizeDp);

        // Jangan sampai button lebih tinggi dari GamingSeekBar
        btnSize = Math.min(btnSize, height);

        float padding = dpToPx(12);
        float cut = Math.max(dpToPx(5), btnSize * 0.18f);

        // Posisi vertikal button di tengah
        float buttonTop = (height - btnSize) / 2f;
        float buttonBottom = buttonTop + btnSize;

        // =========================
        // BUTTON MINUS
        // =========================
        minusBtnRect.set(
                0,
                buttonTop,
                btnSize,
                buttonBottom
        );

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(buttonBgColor);

        drawChamferedRect(
                canvas,
                minusBtnRect,
                cut,
                paint
        );

        // Simbol -
        paint.setColor(buttonTextColor);
        paint.setStrokeWidth(dpToPx(3));
        paint.setStrokeCap(Paint.Cap.ROUND);

        canvas.drawLine(
                btnSize * 0.3f,
                height / 2f,
                btnSize * 0.7f,
                height / 2f,
                paint
        );

        // =========================
        // BUTTON PLUS
        // =========================
        plusBtnRect.set(
                width - btnSize,
                buttonTop,
                width,
                buttonBottom
        );

        paint.setColor(buttonBgColor);
        paint.setStyle(Paint.Style.FILL);

        drawChamferedRect(
                canvas,
                plusBtnRect,
                cut,
                paint
        );

        // Simbol +
        paint.setColor(buttonTextColor);
        paint.setStrokeWidth(dpToPx(3));

        // Horizontal
        canvas.drawLine(
                width - btnSize + (btnSize * 0.3f),
                height / 2f,
                width - (btnSize * 0.3f),
                height / 2f,
                paint
        );

        // Vertical
        canvas.drawLine(
                width - (btnSize * 0.5f),
                buttonTop + (btnSize * 0.3f),
                width - (btnSize * 0.5f),
                buttonTop + (btnSize * 0.7f),
                paint
        );

        // =========================
        // TRACK
        // =========================
        float trackLeft = btnSize + padding;
        float trackRight = width - btnSize - padding;

        float trackWidth = trackRight - trackLeft;

        float centerY = height / 2f;
        float trackHeight = dpToPx(4);

        trackRect.set(
                trackLeft,
                centerY - (trackHeight / 2f),
                trackRight,
                centerY + (trackHeight / 2f)
        );

        paint.setColor(trackBgColor);
        paint.setStyle(Paint.Style.FILL);

        canvas.drawRoundRect(
                trackRect,
                trackHeight / 2f,
                trackHeight / 2f,
                paint
        );

        // =========================
        // PROGRESS
        // =========================
        float progressPercent;

        if (max == min) {
            progressPercent = 0f;
        } else {
            progressPercent =
                    (float) (progress - min) / (max - min);
        }

        float currentThumbX =
                trackLeft + (trackWidth * progressPercent);

        progressRect.set(
                trackLeft,
                centerY - (trackHeight / 2f),
                currentThumbX,
                centerY + (trackHeight / 2f)
        );

        paint.setColor(progressColor);

        canvas.drawRoundRect(
                progressRect,
                trackHeight / 2f,
                trackHeight / 2f,
                paint
        );

        // =========================
        // THUMB
        // =========================
        float thumbWidth = dpToPx(8);
        float thumbHeight = dpToPx(20);

        RectF thumbRect = new RectF(
                currentThumbX - (thumbWidth / 2f),
                centerY - (thumbHeight / 2f),
                currentThumbX + (thumbWidth / 2f),
                centerY + (thumbHeight / 2f)
        );

        paint.setColor(thumbColor);

        canvas.drawRoundRect(
                thumbRect,
                dpToPx(2),
                dpToPx(2),
                paint
        );
    }

    private void drawChamferedRect(
            Canvas canvas,
            RectF r,
            float cut,
            Paint p
    ) {
        buttonPath.reset();

        float c = Math.min(
                cut,
                Math.min(r.width(), r.height()) / 2f
        );

        buttonPath.moveTo(r.left + c, r.top);
        buttonPath.lineTo(r.right - c, r.top);
        buttonPath.lineTo(r.right, r.top + c);
        buttonPath.lineTo(r.right, r.bottom - c);
        buttonPath.lineTo(r.right - c, r.bottom);
        buttonPath.lineTo(r.left + c, r.bottom);
        buttonPath.lineTo(r.left, r.bottom - c);
        buttonPath.lineTo(r.left, r.top + c);
        buttonPath.close();

        canvas.drawPath(buttonPath, p);
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

        float btnSize = dpToPx(buttonSizeDp);

        btnSize = Math.min(
                btnSize,
                getHeight()
        );

        float padding = dpToPx(12);

        float trackLeft = btnSize + padding;
        float trackRight = getWidth() - btnSize - padding;

        float trackWidth = trackRight - trackLeft;

        if (trackWidth <= 0) return;

        float clampedX = Math.max(
                trackLeft,
                Math.min(x, trackRight)
        );

        float fraction =
                (clampedX - trackLeft) / trackWidth;

        int newProgress = Math.round(
                min + fraction * (max - min)
        );

        setProgress(newProgress, true);
    }

    public void setProgress(int newProgress) {
        setProgress(newProgress, false);
    }

    private void setProgress(
            int newProgress,
            boolean fromUser
    ) {

        int clamped = Math.max(
                min,
                Math.min(newProgress, max)
        );

        if (this.progress != clamped) {

            this.progress = clamped;

            invalidate();

            if (listener != null) {
                listener.onProgressChanged(
                        this,
                        this.progress,
                        fromUser
                );
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

    public int getMin() {
        return min;
    }

    public void setMin(int min) {
        this.min = min;
        invalidate();
    }

    public void setStep(int step) {
        this.step = step;
    }

    // =========================
    // SET UKURAN BUTTON DARI JAVA
    // =========================
    public void setButtonSize(float dp) {
        buttonSizeDp = Math.max(1f, dp);
        invalidate();
    }

    public float getButtonSize() {
        return buttonSizeDp;
    }

    public void setOnProgressChangeListener(
            OnProgressChangeListener listener
    ) {
        this.listener = listener;
    }

    private float dpToPx(float dp) {
        return dp *
                getResources()
                        .getDisplayMetrics()
                        .density;
    }
}