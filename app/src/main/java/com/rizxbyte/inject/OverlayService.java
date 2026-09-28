package com.rizxbyte.inject;

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
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

/**
 * System overlay floating panel — lives outside the app Activity.
 */
public class OverlayService extends Service {

    private static final String TAG   = "rizxbyte_overlay";
    private static final String CH_ID = "rizxbyte_overlay";

    public static final String ACTION_SHOW = "com.rizxbyte.inject.OVERLAY_SHOW";
    public static final String ACTION_HIDE = "com.rizxbyte.inject.OVERLAY_HIDE";
    public static final String ACTION_STOP = "com.rizxbyte.inject.OVERLAY_STOP";

    // ── Window ────────────────────────────────────────────────────────
    private WindowManager wm;
    private View root, panel, bubble;
    private WindowManager.LayoutParams lp;
    private final Handler main = new Handler(Looper.getMainLooper());

    // ── State ─────────────────────────────────────────────────────────
    /** 0=Left  1=All  2=Right */
    private int sensArea = 1;
    /** Curve fixed to Decel (2) — UI removed */
    private static final int CURVE_MODE = 2;
    /** Default sensitivity = 1.00x */
    private float sensX = 1.00f;
    private float sensY = 1.00f;
    /** Magic Touch toggle (Switch in UI) */
    private boolean tactix        = false;
    private boolean workerRunning = false;
    private boolean panelVisible  = true;

    // ── View references ───────────────────────────────────────────────
    private TextView      statusTv;
    private TextView[]    areaBtns;
    private TextView      valX, valY;
    private Switch        switchMagicTouch;
    private GamingSeekBar sx, sy;

    // ── Drag state ────────────────────────────────────────────────────
    private int startX, startY, startTouchX, startTouchY;
    private boolean dragging;

    // ═════════════════════════════════════════════════════════════════
    //  Lifecycle
    // ═════════════════════════════════════════════════════════════════

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
                case ACTION_SHOW: showPanel();         break;
                case ACTION_HIDE: hideToBubble();      break;
                case ACTION_STOP: stopWorkerAndSelf(); break;
                default:          showPanel();         break;
            }
        } else {
            showPanel();
        }
        return START_STICKY;
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        removeOverlay();
        super.onDestroy();
    }

    // ═════════════════════════════════════════════════════════════════
    //  Notification
    // ═════════════════════════════════════════════════════════════════

    private void startForegroundNotif() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(
                    CH_ID, "rizxbyte overlay", NotificationManager.IMPORTANCE_LOW);
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
        Notification n = b.setContentTitle("rizxbyte inject")
                .setContentText("Floating panel active — tap to show")
                .setSmallIcon(android.R.drawable.ic_menu_manage)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
        startForeground(42, n);
    }

    // ═════════════════════════════════════════════════════════════════
    //  Inflate overlay
    // ═════════════════════════════════════════════════════════════════

    private void inflateOverlay() {
        LayoutInflater inf = LayoutInflater.from(this);
        root   = inf.inflate(R.layout.overlay_panel, null);
        panel  = root.findViewById(R.id.panel);
        bubble = root.findViewById(R.id.bubble);

        statusTv = root.findViewById(R.id.status_text);
        valX     = root.findViewById(R.id.val_sens_x);
        valY     = root.findViewById(R.id.val_sens_y);

        // ── Magic Touch switch → langsung start / stop worker ─────
        switchMagicTouch = root.findViewById(R.id.switch_magic_touch);
        switchMagicTouch.setOnCheckedChangeListener((btn, checked) -> {
            tactix = checked;
            if (checked) doStart();
            else         doStop();
        });

        // ── Sensitivity Area buttons ───────────────────────────────
        areaBtns = new TextView[]{
                root.findViewById(R.id.area_left),
                root.findViewById(R.id.area_all),
                root.findViewById(R.id.area_right)
        };
        for (int i = 0; i < areaBtns.length; i++) {
            final int idx = i;
            areaBtns[i].setOnClickListener(v -> {
                sensArea = idx;
                refreshAreaUi();
                if (workerRunning) doStart();
            });
        }

        // ── Custom GamingSeekBars — range 0..900 → 1.00x..10.00x ───
        sx = root.findViewById(R.id.seek_sens_x);
        sy = root.findViewById(R.id.seek_sens_y);

        if (sx != null) {
            sx.setMin(0);
            sx.setMax(900);
            sx.setProgress(0);
            sx.setOnProgressChangeListener((seekBar, progress, fromUser) -> {
                sensX = 1.0f + progress / 100.f;
                valX.setText(String.format(Locale.US, "%.2fx", sensX));
                if (fromUser && workerRunning) doStart();
            });
        }

        if (sy != null) {
            sy.setMin(0);
            sy.setMax(900);
            sy.setProgress(0);
            sy.setOnProgressChangeListener((seekBar, progress, fromUser) -> {
                sensY = 1.0f + progress / 100.f;
                valY.setText(String.format(Locale.US, "%.2fx", sensY));
                if (fromUser && workerRunning) doStart();
            });
        }

        // ── Sens step − / + ────────────────────────────────────────
        root.findViewById(R.id.btn_sens_x_minus).setOnClickListener(v -> stepSens(sx, valX, -10, true));
        root.findViewById(R.id.btn_sens_x_plus) .setOnClickListener(v -> stepSens(sx, valX, +10, true));
        root.findViewById(R.id.btn_sens_y_minus).setOnClickListener(v -> stepSens(sy, valY, -10, false));
        root.findViewById(R.id.btn_sens_y_plus) .setOnClickListener(v -> stepSens(sy, valY, +10, false));

        // ── TactiX checkbox (footer) ───────────────────────────────
        CheckBox tact = root.findViewById(R.id.check_tactix);
        tact.setOnCheckedChangeListener((b, checked) -> {
            tactix = checked;
            if (switchMagicTouch != null) switchMagicTouch.setChecked(checked);
            if (workerRunning) doStart();
        });

        // ── Hide / Close ───────────────────────────────────────────
        root.findViewById(R.id.btn_hide) .setOnClickListener(v -> hideToBubble());
        root.findViewById(R.id.btn_close).setOnClickListener(v -> stopWorkerAndSelf());

        // ── Drag: header (panel) + bubble ──────────────────────────
        View.OnTouchListener drag = this::onDrag;
        panel .setOnTouchListener(drag);
        bubble.setOnTouchListener(drag);

        // ── Initial UI state ───────────────────────────────────────
        refreshAreaUi();

        // ── Window params ──────────────────────────────────────────
        int type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;

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
        lp.x = (dm.widthPixels - dp(300)) / 2;
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

    // ═════════════════════════════════════════════════════════════════
    //  Panel visibility
    // ═════════════════════════════════════════════════════════════════

    private void showPanel() {
        panelVisible = true;
        if (panel  != null) panel .setVisibility(View.VISIBLE);
        if (bubble != null) bubble.setVisibility(View.GONE);
        if (lp != null) {
            lp.width  = WindowManager.LayoutParams.WRAP_CONTENT;
            lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
            try { wm.updateViewLayout(root, lp); } catch (Throwable ignored) {}
        }
    }

    private void hideToBubble() {
        panelVisible = false;
        if (panel  != null) panel .setVisibility(View.GONE);
        if (bubble != null) bubble.setVisibility(View.VISIBLE);
        if (lp != null) {
            lp.width  = dp(52);
            lp.height = dp(52);
            try { wm.updateViewLayout(root, lp); } catch (Throwable ignored) {}
        }
    }

    // ═════════════════════════════════════════════════════════════════
    //  Drag
    // ═════════════════════════════════════════════════════════════════

    private boolean onDrag(View v, MotionEvent e) {
        switch (e.getAction()) {
            case MotionEvent.ACTION_DOWN:
                dragging    = false;
                startX      = lp.x;
                startY      = lp.y;
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
                if (!dragging && v == bubble) showPanel();
                return true;
        }
        return false;
    }

    // ═════════════════════════════════════════════════════════════════
    //  Worker control
    // ═════════════════════════════════════════════════════════════════

    private void doStart() {
        statusTv.setText("Starting…");
        main.post(() -> {
            try {
                if (!ShizukuHelper.isReady()) ShizukuHelper.init();

                String bin = getApplicationInfo().nativeLibraryDir + "/librizxbyteengine.so";
                if (!new java.io.File(bin).exists()) bin = "/data/local/tmp/rizxbyte_engine";

                Log.i(TAG, "start bin=" + bin
                        + "  X=" + sensX + "  Y=" + sensY
                        + "  area=" + sensArea
                        + "  curve=" + CURVE_MODE
                        + "  tactix=" + tactix);

                boolean ok = ShizukuHelper.startWorker(
                        bin, CURVE_MODE, sensX, sensY, sensArea, tactix ? 1 : 0);

                workerRunning = ok;
                if (ok) {
                    String[] areas = {"LEFT", "ALL", "RIGHT"};
                    statusTv.setText(String.format(Locale.US, "Engaged  X=%.2f  Y=%.2f  %s%s",
                            sensX, sensY, areas[sensArea], (tactix ? "  TX" : "")));
                    Toast.makeText(this, "Worker started", Toast.LENGTH_SHORT).show();
                } else {
                    if (switchMagicTouch != null) {
                        switchMagicTouch.setOnCheckedChangeListener(null);
                        switchMagicTouch.setChecked(false);
                        switchMagicTouch.setOnCheckedChangeListener((btn, checked) -> {
                            tactix = checked;
                            if (checked) doStart();
                            else         doStop();
                        });
                    }
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
        try { ShizukuHelper.stopWorker(); } catch (Throwable t) { Log.e(TAG, "stopWorker", t); }
        workerRunning = false;
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

    // ═════════════════════════════════════════════════════════════════
    //  UI Refresh helpers
    // ═════════════════════════════════════════════════════════════════

    private void refreshAreaUi() {
        for (int i = 0; i < areaBtns.length; i++) {
            boolean active = (i == sensArea);
            areaBtns[i].setBackgroundResource(
                    active ? R.drawable.bg_hexagon_red : R.drawable.bg_hexagon_dark);
            areaBtns[i].setTextColor(active ? 0xFFFFFFFF : 0xFFB0B3BA);
        }
    }

    // ═════════════════════════════════════════════════════════════════
    //  Helpers
    // ═════════════════════════════════════════════════════════════════

    private void stepSens(GamingSeekBar bar, TextView label, int delta, boolean isX) {
        if (bar == null) return;
        int p = Math.max(0, Math.min(bar.getMax(), bar.getProgress() + delta));
        bar.setProgress(p);
        float v = 1.0f + p / 100.f;
        if (isX) sensX = v; else sensY = v;
        if (label != null) label.setText(String.format(Locale.US, "%.2fx", v));
        if (workerRunning) doStart();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    public static void launch(Context ctx) {
        Intent i = new Intent(ctx, OverlayService.class).setAction(ACTION_SHOW);
        if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i);
        else ctx.startService(i);
    }
}
