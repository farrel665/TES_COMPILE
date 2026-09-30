package com.ancore;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

/** Floating macro trigger. Drag in Macro Manager; tap to run macro. */
public class TriggerView extends View {
    public interface Listener {
        void onHoldStart();
        void onHoldStop();
        void onTap();
        void onMove(float dx, float dy);
        boolean isMoveEnabled();
    }

    private final Paint circle = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Listener listener;
    private float downX, downY;
    private boolean moved;
    private float density;

    public TriggerView(Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        circle.setStyle(Paint.Style.STROKE);
        circle.setStrokeWidth(dp(3));
        circle.setColor(0xFF20E6AA);
        text.setColor(0xFFFFFFFF);
        text.setTextSize(dp(16));
        text.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        text.setTextAlign(Paint.Align.CENTER);
        setClickable(true);
        setContentDescription("Macro Trigger");
    }

    public void setListener(Listener listener) { this.listener = listener; }

    public void setOpacity(float value) {
        int alpha = (int)(Math.max(0f, Math.min(1f, value)) * 255f);
        circle.setAlpha(alpha);
        text.setAlpha(Math.min(255, alpha + 35));
        invalidate();
    }

    public void setDiameter(int dp) {
        int px = Math.round(dp(Math.max(48, Math.min(260, dp))));
        android.view.ViewGroup.LayoutParams lp = getLayoutParams();
        if (lp != null) {
            lp.width = px;
            lp.height = px;
            setLayoutParams(lp);
        }
        invalidate();
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float r = Math.min(getWidth(), getHeight()) * .44f;
        c.drawCircle(cx, cy, r, circle);
        c.drawText("Trigger", cx, cy + dp(6), text);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (listener == null) return true;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getRawX();
                downY = e.getRawY();
                moved = false;
                return true;

            case MotionEvent.ACTION_MOVE:
                float dx = e.getRawX() - downX;
                float dy = e.getRawY() - downY;
                if (!moved && Math.hypot(dx, dy) > dp(10)) {
                    moved = true;
                    listener.onHoldStop();
                }
                if (moved && listener.isMoveEnabled()) {
                    listener.onMove(dx, dy);
                    downX = e.getRawX();
                    downY = e.getRawY();
                }
                return true;

            case MotionEvent.ACTION_UP:
                if (!moved) {
                    // A normal click toggles the macro. Dragging remains a position-only action.
                    listener.onTap();
                } else {
                    listener.onHoldStop();
                }
                return true;

            case MotionEvent.ACTION_CANCEL:
                listener.onHoldStop();
                return true;
            default:
                return true;
        }
    }

    private float dp(float value) { return value * density; }
}
