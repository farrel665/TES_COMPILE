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

    @Override
    public void onDrawForeground(Canvas canvas) {
        try {
            super.onDrawForeground(canvas);
        } catch (NullPointerException ignored) {
            // Bug AOSP: ScrollBarDrawable null di overlay window
        }
    }
}