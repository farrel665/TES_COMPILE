package com.ancore;

import android.content.Context;
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

    // onDrawScrollBars DIHAPUS — method ini final di API 33+
    // Tidak perlu di-override karena scrollbar sudah di-disable di init()
}