package com.ancore;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.widget.ScrollView;

public class SafeScrollView extends ScrollView {

    public SafeScrollView(Context context) {
        super(context);
        init();
    }

    public SafeScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SafeScrollView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setVerticalScrollBarEnabled(false);
        setHorizontalScrollBarEnabled(false);
        setOverScrollMode(OVER_SCROLL_NEVER);
    }

    // FIX UTAMA: override supaya tidak NPE di overlay window
    @Override
    protected void onDrawScrollBars(Canvas canvas) {
        try {
            super.onDrawScrollBars(canvas);
        } catch (NullPointerException ignored) {
            // ScrollBarDrawable null di TYPE_APPLICATION_OVERLAY — aman diabaikan
        }
    }
}