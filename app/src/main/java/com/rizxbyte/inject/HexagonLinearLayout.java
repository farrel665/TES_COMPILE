package com.my.newproject;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.widget.LinearLayout;

public class HexagonLinearLayout extends LinearLayout {

    private Path hexagonPath = new Path();
    private Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    
    private boolean hasBgColor = false;
    private boolean showBorder = true;
    private float strokeWidthPx = 0f;
    private float cornerCutPx = 0f;

    public HexagonLinearLayout(Context context) {
        super(context);
        init(context);
    }

    public HexagonLinearLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public HexagonLinearLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        setWillNotDraw(false);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);

        fillPaint.setStyle(Paint.Style.FILL);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setColor(Color.BLACK);
        
        setCornerCut(12f);
        setStrokeWidth(1f);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        createCutPath(w, h);
    }

    private void createCutPath(float width, float height) {
        hexagonPath.reset();

        float strokeOffset = showBorder ? (strokeWidthPx / 2f) : 0f;
        float L = strokeOffset;
        float T = strokeOffset;
        float R = width - strokeOffset;
        float B = height - strokeOffset;

        float c = cornerCutPx;
        float maxCut = Math.min((R - L) / 2f, (B - T) / 2f);
        if (c > maxCut) c = maxCut;

        hexagonPath.moveTo(L + c, T);
        hexagonPath.lineTo(R - c, T);
        hexagonPath.lineTo(R, T + c);
        hexagonPath.lineTo(R, B - c);
        hexagonPath.lineTo(R - c, B);
        hexagonPath.lineTo(L + c, B);
        hexagonPath.lineTo(L, B - c);
        hexagonPath.lineTo(L, T + c);
        hexagonPath.close();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        canvas.save();
        canvas.clipPath(hexagonPath);

        if (hasBgColor) {
            canvas.drawPath(hexagonPath, fillPaint);
        }

        super.dispatchDraw(canvas);
        canvas.restore();

        if (showBorder && strokeWidthPx > 0) {
            canvas.drawPath(hexagonPath, borderPaint);
        }
    }

    public void setCornerCut(float cutInDp) {
        this.cornerCutPx = dpToPx(cutInDp);
        createCutPath(getWidth(), getHeight());
        invalidate();
    }

    public void setStrokeColor(int color) {
        borderPaint.setColor(color);
        invalidate();
    }

    public void setStrokeColor(String hexColor) {
        try {
            if (!hexColor.startsWith("#")) hexColor = "#" + hexColor;
            setStrokeColor(Color.parseColor(hexColor));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void setStrokeWidth(float widthInDp) {
        this.strokeWidthPx = dpToPx(widthInDp);
        borderPaint.setStrokeWidth(this.strokeWidthPx);
        createCutPath(getWidth(), getHeight());
        invalidate();
    }

    public void setStrokeEnabled(boolean enabled) {
        this.showBorder = enabled;
        createCutPath(getWidth(), getHeight());
        invalidate();
    }

    @Override
    public void setBackgroundColor(int color) {
        this.hasBgColor = true;
        fillPaint.setColor(color);
        invalidate();
    }

    public void setBackgroundColor(String hexColor) {
        try {
            if (!hexColor.startsWith("#")) hexColor = "#" + hexColor;
            setBackgroundColor(Color.parseColor(hexColor));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private float dpToPx(float dp) {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            getContext().getResources().getDisplayMetrics()
        );
    }
}

