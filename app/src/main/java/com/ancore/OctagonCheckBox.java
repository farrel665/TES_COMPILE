package com.ancore;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

public class OctagonCheckBox extends View {

    private boolean isChecked = false;
    private Paint borderPaint;
    private Paint fillPaint;
    private Paint checkPaint;
    
    private Path octagonPath;
    private Path checkPath;

    // Warna menyesuaikan tema Gaming Overlay (Gambar 2)
    private int colorUncheckedBorder = Color.parseColor("#7A7272"); // Abu-abu saat tidak aktif
    private int colorCheckedBorder = Color.parseColor("#FF002B");   // Merah saat aktif
    private int colorCheckedFill = Color.parseColor("#FF002B");     // Isian merah saat aktif

    private OnCheckedChangeListener listener;

    // Interface Listener
    public interface OnCheckedChangeListener {
        void onCheckedChanged(OctagonCheckBox checkBox, boolean isChecked);
    }

    public OctagonCheckBox(Context context) {
        super(context);
        init();
    }

    public OctagonCheckBox(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public OctagonCheckBox(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // Paint untuk garis tepi (Border)
        borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(dpToPx(2.5f));

        // Paint untuk isian background saat Checked
        fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillPaint.setStyle(Paint.Style.FILL);

        // Paint untuk tanda centang (Checkmark)
        checkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        checkPaint.setStyle(Paint.Style.STROKE);
        checkPaint.setColor(Color.WHITE);
        checkPaint.setStrokeWidth(dpToPx(3f));
        checkPaint.setStrokeCap(Paint.Cap.ROUND);
        checkPaint.setStrokeJoin(Paint.Join.ROUND);

        octagonPath = new Path();
        checkPath = new Path();

        // Mengatur event klik untuk mengubah state
        setOnClickListener(v -> toggle());
    }

    public void toggle() {
        setChecked(!isChecked);
    }

    public boolean isChecked() {
        return isChecked;
    }

    public void setChecked(boolean checked) {
        if (this.isChecked != checked) {
            this.isChecked = checked;
            invalidate(); // Redraw Tampilan
            if (listener != null) {
                listener.onCheckedChanged(this, this.isChecked);
            }
        }
    }

    public void setOnCheckedChangeListener(OnCheckedChangeListener listener) {
        this.listener = listener;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float width = getWidth();
        float height = getHeight();
        float strokeWidth = borderPaint.getStrokeWidth();

        // Area menggambar (mengurangi strokeWidth agar tidak terpotong tepi canvas)
        float left = strokeWidth / 2;
        float top = strokeWidth / 2;
        float right = width - strokeWidth / 2;
        float bottom = height - strokeWidth / 2;

        // Potongan sudut oktagon (20% dari ukuran terkecil)
        float cut = Math.min(width, height) * 0.20f;

        // Membuat jalur bentuk Oktagon
        octagonPath.reset();
        octagonPath.moveTo(left + cut, top);
        octagonPath.lineTo(right - cut, top);
        octagonPath.lineTo(right, top + cut);
        octagonPath.lineTo(right, bottom - cut);
        octagonPath.lineTo(right - cut, bottom);
        octagonPath.lineTo(left + cut, bottom);
        octagonPath.lineTo(left, bottom - cut);
        octagonPath.lineTo(left, top + cut);
        octagonPath.close();

        if (isChecked) {
            // Gambar background terisi warna merah
            fillPaint.setColor(colorCheckedFill);
            canvas.drawPath(octagonPath, fillPaint);

            // Gambar border merah
            borderPaint.setColor(colorCheckedBorder);
            canvas.drawPath(octagonPath, borderPaint);

            // Gambar tanda centang putih
            float w = right - left;
            float h = bottom - top;
            checkPath.reset();
            checkPath.moveTo(left + w * 0.28f, top + h * 0.52f);
            checkPath.lineTo(left + w * 0.44f, top + h * 0.68f);
            checkPath.lineTo(left + w * 0.72f, top + h * 0.35f);
            canvas.drawPath(checkPath, checkPaint);

        } else {
            // Gambar border abu-abu saat Unchecked (seperti Gambar 1)
            borderPaint.setColor(colorUncheckedBorder);
            canvas.drawPath(octagonPath, borderPaint);
        }
    }

    private float dpToPx(float dp) {
        return dp * getContext().getResources().getDisplayMetrics().density;
    }
}
