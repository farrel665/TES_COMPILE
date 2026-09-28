package com.ancore;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

public class GamingSwitch extends View {

    // Interface Listener untuk mendeteksi perubahan status switch
    public interface OnCheckedChangeListener {
        void onCheckedChanged(GamingSwitch switchView, boolean isChecked);
    }

    private boolean isChecked = true;
    private OnCheckedChangeListener listener;

    private Paint trackPaint;
    private Paint thumbPaint;
    private Path trackPath;
    private Path thumbPath;

    // Warna UI sesuai dengan contoh gambar
    private int colorTrackOn = Color.parseColor("#E51010");   // Merah menyala
    private int colorThumbOn = Color.parseColor("#1A0505");   // Gelap / Hitam
    private int colorTrackOff = Color.parseColor("#2B2B2B");  // Abu-abu gelap
    private int colorThumbOff = Color.parseColor("#777777");  // Abu-abu sedang

    // Progress animasi (0.0f = OFF / Kiri, 1.0f = ON / Kanan)
    private float animatedValue = 1.0f;
    private ValueAnimator animator;

    public GamingSwitch(Context context) {
        super(context);
        init();
    }

    public GamingSwitch(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public GamingSwitch(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        trackPath = new Path();
        thumbPath = new Path();

        setOnClickListener(v -> toggle());
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Ukuran default jika tidak ditentukan di XML (60dp x 32dp)
        int defaultWidth = (int) dpToPx(60);
        int defaultHeight = (int) dpToPx(32);

        int width = resolveSize(defaultWidth, widthMeasureSpec);
        int height = resolveSize(defaultHeight, heightMeasureSpec);

        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float width = getWidth();
        float height = getHeight();
        float padding = height * 0.12f; // Padding dalam untuk thumb
        float cutSize = height * 0.25f; // Ukuran potongan sudut (chamfer)

        // 1. Gambar Track (Background)
        trackPaint.setColor(isChecked ? colorTrackOn : colorTrackOff);
        createChamferedPath(trackPath, 0, 0, width, height, cutSize);
        canvas.drawPath(trackPath, trackPaint);

        // 2. Hitung Posisi Thumb (Toggle Inner Box)
        float thumbSize = height - (padding * 2);
        float minX = padding;
        float maxX = width - padding - thumbSize;
        float currentX = minX + (maxX - minX) * animatedValue;

        // 3. Gambar Thumb
        thumbPaint.setColor(isChecked ? colorThumbOn : colorThumbOff);
        float thumbCut = thumbSize * 0.25f;
        createChamferedPath(thumbPath, currentX, padding, currentX + thumbSize, padding + thumbSize, thumbCut);
        canvas.drawPath(thumbPath, thumbPaint);
    }

    /**
     * Membuat Path persegi dengan sudut terpotong 45 derajat (Chamfer)
     */
    private void createChamferedPath(Path path, float left, float top, float right, float bottom, float cut) {
        path.reset();
        path.moveTo(left + cut, top);
        path.lineTo(right - cut, top);
        path.lineTo(right, top + cut);
        path.lineTo(right, bottom - cut);
        path.lineTo(right - cut, bottom);
        path.lineTo(left + cut, bottom);
        path.lineTo(left, bottom - cut);
        path.lineTo(left, top + cut);
        path.close();
    }

    public void toggle() {
        setChecked(!isChecked);
    }

    public boolean isChecked() {
        return isChecked;
    }

    public void setChecked(boolean checked) {
        setChecked(checked, true);
    }

    public void setChecked(boolean checked, boolean animate) {
        if (this.isChecked == checked && animator != null && !animator.isRunning()) {
            return;
        }

        this.isChecked = checked;

        if (listener != null) {
            listener.onCheckedChanged(this, isChecked);
        }

        float targetValue = checked ? 1.0f : 0.0f;

        if (animate) {
            if (animator != null && animator.isRunning()) {
                animator.cancel();
            }
            animator = ValueAnimator.ofFloat(animatedValue, targetValue);
            animator.setDuration(200);
            animator.setInterpolator(new DecelerateInterpolator());
            animator.addUpdateListener(animation -> {
                animatedValue = (float) animation.getAnimatedValue();
                invalidate();
            });
            animator.start();
        } else {
            animatedValue = targetValue;
            invalidate();
        }
    }

    public void setOnCheckedChangeListener(OnCheckedChangeListener listener) {
        this.listener = listener;
    }

    private float dpToPx(float dp) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                getResources().getDisplayMetrics()
        );
    }
}
