package com.ancore;

import com.ancore.R;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * System overlay floating panel — lives outside the app Activity.
 * - START from Bootstrap launches this service
 * - Hide → shrinks to bubble (still on screen)
 * - Close / STOP → removes overlay + stops worker
 * - Drag bubble or panel freely
 */
public class OverlayService extends Service {
    private static final String TAG = "ancore_overlay";
    private static final String CH_ID = "ancore_overlay";
    public static final String ACTION_SHOW = "com.ancore.OVERLAY_SHOW";
    public static final String ACTION_HIDE = "com.ancore.OVERLAY_HIDE";
    public static final String ACTION_STOP = "com.ancore.OVERLAY_STOP";

    private WindowManager wm;
    private View root;
    private View panel;
    private View bubble;
    private WindowManager.LayoutParams lp;
    private final Handler main = new Handler(Looper.getMainLooper());

    private int sensArea = 1;   // 0 Left 1 All 2 Right
    private float sensX = 2.00f;
    private float sensY = 2.00f;
    private boolean tactix = false;
    private boolean workerRunning = false;
    private boolean panelVisible = true;

    private TextView statusTv;
    private TextView btnStart, btnStop;
    private TextView[] areaBtns;
    private TextView valX, valY;

    // drag state
    private int startX, startY, startTouchX, startTouchY;
    private boolean dragging;

    @Override
    public void onCreate() {
        super.onCreate();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        startForegroundNotif();
        if (!Settings.canDrawOverlays(this)) {
            Log.e(TAG, "no overlay permission");
            stopSelf();
            return;
        }
        inflateOverlay();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            switch (intent.getAction()) {
                case ACTION_SHOW:
                    showPanel();
                    break;
                case ACTION_HIDE:
                    hideToBubble();
                    break;
                case ACTION_STOP:
                    stopWorkerAndSelf();
                    break;
                default:
                    showPanel();
                    break;
            }
        } else {
            showPanel();
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        removeOverlay();
        super.onDestroy();
    }

    private void startForegroundNotif() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(
                    CH_ID, "ancore overlay", NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
        Intent open = new Intent(this, OverlayService.class).setAction(ACTION_SHOW);
        PendingIntent pi = PendingIntent.getService(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CH_ID)
                : new Notification.Builder(this);
        Notification n = b.setContentTitle("ancore inject")
                .setContentText("Floating panel active — tap to show")
                .setSmallIcon(android.R.drawable.ic_menu_manage)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
        startForeground(42, n);
    }

    private void inflateOverlay() {
        LayoutInflater inf = LayoutInflater.from(this);
        root = inf.inflate(R.layout.overlay_panel, null);
        panel = root.findViewById(R.id.panel);
        bubble = root.findViewById(R.id.bubble);

        statusTv = root.findViewById(R.id.status_text);
        btnStart = root.findViewById(R.id.btn_start);
        btnStop = root.findViewById(R.id.btn_stop);
        valX = root.findViewById(R.id.val_sens_x);
        valY = root.findViewById(R.id.val_sens_y);

        areaBtns = new TextView[]{
                root.findViewById(R.id.area_left),
                root.findViewById(R.id.area_all),
                root.findViewById(R.id.area_right)
        };

        // Area toggles
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            areaBtns[i].setOnClickListener(v -> {
                sensArea = idx;
                refreshAreaUi();
                if (workerRunning) doStart();
            });
        }

        GamingSeekBar sx = root.findViewById(R.id.seek_sens_x);
        GamingSeekBar sy = root.findViewById(R.id.seek_sens_y);
        // progress 0..900 → 1.00 .. 10.00  (+/- built into GamingSeekBar)
        if (sx != null) {
            sx.setMin(0);
            sx.setMax(900);
            sx.setStep(10);
            sx.setProgress(Math.round((sensX - 1.0f) * 100));
            sx.setOnProgressChangeListener((bar, progress, fromUser) -> {
                sensX = 1.0f + progress / 100.f;
                if (valX != null) valX.setText(String.format("%.2fx", sensX));
                if (fromUser && workerRunning) doStart();
            });
        }
        if (sy != null) {
            sy.setMin(0);
            sy.setMax(900);
            sy.setStep(10);
            sy.setProgress(Math.round((sensY - 1.0f) * 100));
            sy.setOnProgressChangeListener((bar, progress, fromUser) -> {
                sensY = 1.0f + progress / 100.f;
                if (valY != null) valY.setText(String.format("%.2fx", sensY));
                if (fromUser && workerRunning) doStart();
            });
        }

        CheckBox tact = root.findViewById(R.id.check_tactix);
        tact.setOnCheckedChangeListener((b, checked) -> {
            tactix = checked;
            if (workerRunning) doStart();
        });

        root.findViewById(R.id.btn_hide).setOnClickListener(v -> hideToBubble());
        root.findViewById(R.id.btn_close).setOnClickListener(v -> stopWorkerAndSelf());

        btnStart.setOnClickListener(v -> doStart());
        btnStop.setOnClickListener(v -> doStop());

        // drag on panel header / bubble
        View.OnTouchListener drag = this::onDrag;
        panel.setOnTouchListener(drag);
        bubble.setOnTouchListener(drag);

        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;

        DisplayMetrics dm = new DisplayMetrics();
        wm.getDefaultDisplay().getMetrics(dm);
        lp.x = (dm.widthPixels - dp(320)) / 2;
        lp.y = dm.heightPixels / 5;

        try {
            wm.addView(root, lp);
            showPanel();
            Log.i(TAG, "overlay added");
        } catch (Throwable t) {
            Log.e(TAG, "addView failed", t);
            Toast.makeText(this, "Overlay failed: " + t.getMessage(), Toast.LENGTH_LONG).show();
            stopSelf();
        }
    }

    private void showPanel() {
        panelVisible = true;
        if (panel != null) panel.setVisibility(View.VISIBLE);
        if (bubble != null) bubble.setVisibility(View.GONE);
        if (lp != null) {
            lp.width = WindowManager.LayoutParams.WRAP_CONTENT;
            lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
            try { wm.updateViewLayout(root, lp); } catch (Throwable ignored) {}
        }
    }

    private void hideToBubble() {
        panelVisible = false;
        if (panel != null) panel.setVisibility(View.GONE);
        if (bubble != null) bubble.setVisibility(View.VISIBLE);
        if (lp != null) {
            lp.width = dp(52);
            lp.height = dp(52);
            try { wm.updateViewLayout(root, lp); } catch (Throwable ignored) {}
        }
    }

    private boolean onDrag(View v, MotionEvent e) {
        switch (e.getAction()) {
            case MotionEvent.ACTION_DOWN:
                dragging = false;
                startX = lp.x;
                startY = lp.y;
                startTouchX = (int) e.getRawX();
                startTouchY = (int) e.getRawY();
                return true;
            case MotionEvent.ACTION_MOVE:
                int dx = (int) e.getRawX() - startTouchX;
                int dy = (int) e.getRawY() - startTouchY;
                if (Math.abs(dx) > 8 || Math.abs(dy) > 8) dragging = true;
                lp.x = startX + dx;
                lp.y = startY + dy;
                try { wm.updateViewLayout(root, lp); } catch (Throwable ignored) {}
                return true;
            case MotionEvent.ACTION_UP:
                if (!dragging && v == bubble) {
                    // tap bubble → expand panel
                    showPanel();
                }
                return true;
        }
        return false;
    }

    private void doStart() {
        statusTv.setText("Starting…");
        main.post(() -> {
            try {
                // ensure Shizuku bound
                if (!ShizukuHelper.isReady()) {
                    ShizukuHelper.init();
                }
                // Prefer APK-native lib, UserService will stage it to /data/local/tmp
                String bin = getApplicationInfo().nativeLibraryDir
                        + "/libancoreengine.so";
                java.io.File f = new java.io.File(bin);
                if (!f.exists()) {
                    bin = "/data/local/tmp/ancore_engine";
                }
                Log.i(TAG, "start bin=" + bin + " X=" + sensX + " Y=" + sensY
                        + " area=" + sensArea + " tactix=" + tactix);
                // preset fixed 0 — curve removed
                boolean ok = ShizukuHelper.startWorker(bin, 0, sensX, sensY,
                        sensArea, tactix ? 1 : 0);
                workerRunning = ok;
                if (ok) {
                    String[] areas = {"LEFT", "ALL", "RIGHT"};
                    statusTv.setText("Engaged  X=" + String.format("%.2f", sensX)
                            + " Y=" + String.format("%.2f", sensY)
                            + "  " + areas[sensArea]
                            + (tactix ? " TX" : ""));
                    btnStart.setVisibility(View.GONE);
                    btnStop.setVisibility(View.VISIBLE);
                    Toast.makeText(this, "Worker started", Toast.LENGTH_SHORT).show();
                } else {
                    statusTv.setText("Start failed — check Shizuku / binary");
                    Toast.makeText(this, "Start failed", Toast.LENGTH_SHORT).show();
                }
            } catch (Throwable t) {
                statusTv.setText("Error: " + t.getMessage());
                Log.e(TAG, "doStart", t);
            }
        });
    }

    private void doStop() {
        try {
            ShizukuHelper.stopWorker();
        } catch (Throwable t) {
            Log.e(TAG, "stopWorker", t);
        }
        workerRunning = false;
        btnStart.setVisibility(View.VISIBLE);
        btnStop.setVisibility(View.GONE);
        statusTv.setText("Disengaged");
    }

    private void stopWorkerAndSelf() {
        doStop();
        removeOverlay();
        stopForeground(true);
        stopSelf();
    }

    private void removeOverlay() {
        if (root != null && wm != null) {
            try { wm.removeView(root); } catch (Throwable ignored) {}
            root = null;
        }
    }

    private void refreshAreaUi() {
        for (int i = 0; i < 3; i++) {
            areaBtns[i].setBackgroundResource(i == sensArea
                    ? R.drawable.bg_btn_red : R.drawable.bg_btn_dark);
            areaBtns[i].setTextColor(i == sensArea ? 0xFFFFFFFF : 0xFFB0B3BA);
        }
    }

    private int dp(int v) {
        float d = getResources().getDisplayMetrics().density;
        return (int) (v * d + 0.5f);
    }

    /** Helper for Bootstrap / others */
    public static void launch(Context ctx) {
        Intent i = new Intent(ctx, OverlayService.class);
        i.setAction(ACTION_SHOW);
        if (Build.VERSION.SDK_INT >= 26) {
            ctx.startForegroundService(i);
        } else {
            ctx.startService(i);
        }
    }
}

