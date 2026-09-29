package com.ancore;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.drawable.GradientDrawable;
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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public class OverlayService extends Service {

    private static final String TAG   = "ancore_overlay";
    private static final String CH_ID = "ancore_overlay";

    public static final String ACTION_SHOW = "com.ancore.OVERLAY_SHOW";
    public static final String ACTION_HIDE = "com.ancore.OVERLAY_HIDE";
    public static final String ACTION_STOP = "com.ancore.OVERLAY_STOP";

    // ── Window ────────────────────────────────────────────────────────
    private WindowManager wm;
    private View root, bubble;
    private HexagonLinearLayout panel;
    private WindowManager.LayoutParams lp;
    private final Handler main = new Handler(Looper.getMainLooper());

    // ── State ─────────────────────────────────────────────────────────
    private int sensArea = 1;
    private static final int CURVE_MODE = 2;
    private float sensX = 1.00f;
    private float sensY = 1.00f;
    private boolean tactix          = false;
    private boolean workerRunning   = false;
    private boolean panelVisible    = true;
    private boolean isSyncingTactix = false;

    // ── View references ───────────────────────────────────────────────
    private TextView            statusTv;
    private TextView            valX, valY, valStrength, valResponsive;
    private TextView            txtLeft, txtAll, txtRight;
    private ImageView           imgLeft, imgAll, imgRight, btnHide;
    private HexagonLinearLayout areaLeft, areaAll, areaRight;
    private GamingSwitch        switchMagicTouch;
    private OctagonCheckBox     checkTactix;
    private GamingSeekBar       sx, sy, seekStrength, seekResponsive;

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

    // ═════════════════════════════════════════════════════════════════
    //  Inflate overlay
    // ═════════════════════════════════════════════════════════════════

    private void inflateOverlay() {
        LayoutInflater inf = LayoutInflater.from(this);
        root   = inf.inflate(R.layout.overlay_panel, null);
        panel  = root.findViewById(R.id.Panel);
        bubble = root.findViewById(R.id.bubble);
        btnHide = root.findViewById(R.id.btn_hide);

        statusTv      = root.findViewById(R.id.textview29);
        valX          = root.findViewById(R.id.val_sens_x);
        valY          = root.findViewById(R.id.val_sens_y);
        valStrength   = root.findViewById(R.id.val_strength);
        valResponsive = root.findViewById(R.id.val_resposive);

        areaLeft  = root.findViewById(R.id.area_left);
        areaAll   = root.findViewById(R.id.area_all);
        areaRight = root.findViewById(R.id.area_right);

        imgLeft  = root.findViewById(R.id.img_left);
        imgAll   = root.findViewById(R.id.img_all);
        imgRight = root.findViewById(R.id.img_right);

        txtLeft  = root.findViewById(R.id.txt_left);
        txtAll   = root.findViewById(R.id.txt_all);
        txtRight = root.findViewById(R.id.txt_right);

        HexagonLinearLayout btnSensXMinus      = root.findViewById(R.id.btn_sens_x_minus);
        HexagonLinearLayout btnSensXPlus       = root.findViewById(R.id.btn_sens_x_plus);
        HexagonLinearLayout btnSensYMinus      = root.findViewById(R.id.btn_sens_y_minus);
        HexagonLinearLayout btnSensYPlus       = root.findViewById(R.id.btn_sens_y_plus);
        HexagonLinearLayout btnStrengthMinus   = root.findViewById(R.id.btn_strength_minus);
        HexagonLinearLayout btnStrengthPlus    = root.findViewById(R.id.btn_strength_plus);
        HexagonLinearLayout btnResponsiveMinus = root.findViewById(R.id.btn_responsive_minus);
        HexagonLinearLayout btnResponsivePlus  = root.findViewById(R.id.btn_responsive_plus);

        ImageView    btnResetX = root.findViewById(R.id.btn_reset_x);
        ImageView    btnResetY = root.findViewById(R.id.btn_reset_y);
        LinearLayout info      = root.findViewById(R.id.info);

        // ── Panel & Button Styling ────────────────────────────────────
        if (panel != null) {
            panel.setBackgroundColor(Color.parseColor("#221D19"));
            panel.setCornerCut(11f);
            panel.setStrokeEnabled(false);
        }

        if (areaLeft != null) {
            areaLeft.setBackgroundColor(Color.parseColor("#403B3D"));
            areaLeft.setCornerCut(6f);
            areaLeft.setStrokeEnabled(false);
        }

        if (areaAll != null) {
            areaAll.setBackgroundColor(Color.parseColor("#E61F33"));
            areaAll.setCornerCut(6f);
            areaAll.setStrokeEnabled(false);
        }

        if (areaRight != null) {
            areaRight.setBackgroundColor(Color.parseColor("#403B3D"));
            areaRight.setCornerCut(6f);
            areaRight.setStrokeEnabled(false);
        }

        setupHexagonButton(btnSensXMinus);
        setupHexagonButton(btnSensXPlus);
        setupHexagonButton(btnSensYMinus);
        setupHexagonButton(btnSensYPlus);
        setupHexagonButton(btnResponsiveMinus);
        setupHexagonButton(btnResponsivePlus);
        setupHexagonButton(btnStrengthMinus);
        setupHexagonButton(btnStrengthPlus);

        // ── Hide / Show ───────────────────────────────────────────────
        if (btnHide != null) {
            btnHide.setOnClickListener(v -> hideToBubble());
        }

        if (bubble != null) {
            bubble.setOnClickListener(v -> {
                if (!dragging) showPanel();
            });
        }

        // ── Area buttons ──────────────────────────────────────────────
        if (areaLeft != null) {
            areaLeft.setOnClickListener(_view -> {
                sensArea = 0;
                updateAreaUI(areaLeft, areaAll, areaRight,
                        imgLeft, imgAll, imgRight, txtLeft, txtAll, txtRight);
                if (workerRunning) doStart();
            });
        }

        if (areaAll != null) {
            areaAll.setOnClickListener(_view -> {
                sensArea = 1;
                updateAreaUI(areaAll, areaLeft, areaRight,
                        imgAll, imgLeft, imgRight, txtAll, txtLeft, txtRight);
                if (workerRunning) doStart();
            });
        }

        if (areaRight != null) {
            areaRight.setOnClickListener(_view -> {
                sensArea = 2;
                updateAreaUI(areaRight, areaLeft, areaAll,
                        imgRight, imgLeft, imgAll, txtRight, txtLeft, txtAll);
                if (workerRunning) doStart();
            });
        }

        // ── Reset buttons ─────────────────────────────────────────────
        if (btnResetX != null) {
            btnResetX.setOnClickListener(_view -> {
                if (sx != null) sx.setProgress(0);
                sensX = 1.00f;
                if (valX != null) valX.setText("1.00×");
                if (workerRunning) doStart();
            });
        }

        if (btnResetY != null) {
            btnResetY.setOnClickListener(_view -> {
                if (sy != null) sy.setProgress(0);
                sensY = 1.00f;
                if (valY != null) valY.setText("1.00×");
                if (workerRunning) doStart();
            });
        }

        // ── Info background ───────────────────────────────────────────
        if (info != null) {
            info.setBackground(new GradientDrawable() {
                public GradientDrawable getIns(int a, int b) {
                    this.setCornerRadius(a);
                    this.setColor(b);
                    return this;
                }
            }.getIns(9, 0xFF403B3D));
        }

        // ── Magic Touch Switch & CheckBox ──────────────────────────────
        switchMagicTouch = root.findViewById(R.id.switch_magic_touch);
        checkTactix      = root.findViewById(R.id.check_tactix);

        if (switchMagicTouch != null) {
            switchMagicTouch.setOnCheckedChangeListener((btn, checked) ->
                    setTactixState(checked, true));
        }

        if (checkTactix != null) {
            checkTactix.setOnCheckedChangeListener((b, checked) ->
                    setTactixState(checked, true));
        }

        // ── SeekBars ──────────────────────────────────────────────────
        sx = root.findViewById(R.id.seek_sens_x);
        sy = root.findViewById(R.id.seek_sens_y);

        if (sx != null) {
            sx.setMin(0);
            sx.setMax(900);
            sx.setProgress(0);
            sx.setOnProgressChangeListener((seekBar, progress, fromUser) -> {
                sensX = 1.0f + progress / 100.f;
                if (valX != null) valX.setText(String.format(Locale.US, "%.2fx", sensX));
                if (fromUser && workerRunning) doStart();
            });
        }

        if (sy != null) {
            sy.setMin(0);
            sy.setMax(900);
            sy.setProgress(0);
            sy.setOnProgressChangeListener((seekBar, progress, fromUser) -> {
                sensY = 1.0f + progress / 100.f;
                if (valY != null) valY.setText(String.format(Locale.US, "%.2fx", sensY));
                if (fromUser && workerRunning) doStart();
            });
        }

        if (btnSensXMinus != null) btnSensXMinus.setOnClickListener(v -> stepSens(sx, valX, -10, true));
        if (btnSensXPlus  != null) btnSensXPlus .setOnClickListener(v -> stepSens(sx, valX, +10, true));
        if (btnSensYMinus != null) btnSensYMinus.setOnClickListener(v -> stepSens(sy, valY, -10, false));
        if (btnSensYPlus  != null) btnSensYPlus .setOnClickListener(v -> stepSens(sy, valY, +10, false));

        seekStrength   = root.findViewById(R.id.seek_strength);
        seekResponsive = root.findViewById(R.id.seek_responsive);

        if (seekStrength != null) {
            seekStrength.setMin(0);
            seekStrength.setMax(100);
            seekStrength.setProgress(0);
            seekStrength.setOnProgressChangeListener((seekBar, progress, fromUser) -> {
                if (valStrength != null) valStrength.setText(progress + "%");
            });
        }

        if (seekResponsive != null) {
            seekResponsive.setMin(0);
            seekResponsive.setMax(100);
            seekResponsive.setProgress(0);
            seekResponsive.setOnProgressChangeListener((seekBar, progress, fromUser) -> {
                if (valResponsive != null) valResponsive.setText(progress + "%");
            });
        }

        if (btnStrengthMinus   != null) btnStrengthMinus  .setOnClickListener(v -> stepGeneric(seekStrength,   valStrength,   -5));
        if (btnStrengthPlus    != null) btnStrengthPlus   .setOnClickListener(v -> stepGeneric(seekStrength,   valStrength,   +5));
        if (btnResponsiveMinus != null) btnResponsiveMinus.setOnClickListener(v -> stepGeneric(seekResponsive, valResponsive, -5));
        if (btnResponsivePlus  != null) btnResponsivePlus .setOnClickListener(v -> stepGeneric(seekResponsive, valResponsive, +5));

        // ── Drag ──────────────────────────────────────────────────────
        View.OnTouchListener drag = this::onDrag;
        if (panel  != null) panel .setOnTouchListener(drag);
        if (bubble != null) bubble.setOnTouchListener(drag);

        // ── Window params ─────────────────────────────────────────────
        lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
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
    //  Panel visibility & Drag
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
    //  State Sync & Worker Control
    // ═════════════════════════════════════════════════════════════════

    private void setTactixState(boolean checked, boolean triggerWorker) {
        if (isSyncingTactix) return;
        isSyncingTactix = true;

        tactix = checked;
        if (switchMagicTouch != null && switchMagicTouch.isChecked() != checked)
            switchMagicTouch.setChecked(checked);
        if (checkTactix != null && checkTactix.isChecked() != checked)
            checkTactix.setChecked(checked);

        isSyncingTactix = false;

        if (triggerWorker) {
            if (checked) doStart();
            else         doStop();
        }
    }

    // ── FIX UTAMA: pakai callback supaya tunggu Shizuku siap ──────────
    private void doStart() {
        if (statusTv != null) statusTv.setText("Connecting to Shizuku…");

        ShizukuHelper.init(success -> main.post(() -> {
            if (!success) {
                workerRunning = false;
                setTactixState(false, false);
                if (statusTv != null) statusTv.setText("Shizuku not ready");
                Toast.makeText(this, "Shizuku not ready", Toast.LENGTH_SHORT).show();
                return;
            }

            if (statusTv != null) statusTv.setText("Starting…");
            try {
                String bin = getApplicationInfo().nativeLibraryDir + "/libancoreengine.so";
                if (!new java.io.File(bin).exists()) bin = "/data/local/tmp/ancore_engine";

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
                    if (statusTv != null) {
                        statusTv.setText(String.format(Locale.US,
                                "Engaged  X=%.2f  Y=%.2f  %s%s",
                                sensX, sensY, areas[sensArea], (tactix ? "  TX" : "")));
                    }
                    Toast.makeText(this, "Worker started", Toast.LENGTH_SHORT).show();
                } else {
                    setTactixState(false, false);
                    if (statusTv != null) statusTv.setText("Start failed — binary error");
                    Toast.makeText(this, "Start failed", Toast.LENGTH_SHORT).show();
                }
            } catch (Throwable t) {
                if (statusTv != null) statusTv.setText("Error: " + t.getMessage());
                Log.e(TAG, "doStart", t);
            }
        }));
    }

    private void doStop() {
        try { ShizukuHelper.stopWorker(); } catch (Throwable t) { Log.e(TAG, "stopWorker", t); }
        workerRunning = false;
        if (statusTv != null) statusTv.setText("Disengaged");
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
    //  Helpers
    // ═════════════════════════════════════════════════════════════════

    private void setupHexagonButton(HexagonLinearLayout btn) {
        if (btn != null) {
            btn.setBackgroundColor(Color.parseColor("#733516"));
            btn.setCornerCut(6f);
            btn.setStrokeEnabled(false);
        }
    }

    private void updateAreaUI(HexagonLinearLayout activeArea,
                              HexagonLinearLayout in1, HexagonLinearLayout in2,
                              ImageView activeImg, ImageView inImg1, ImageView inImg2,
                              TextView activeTxt, TextView inTxt1, TextView inTxt2) {
        if (activeArea != null) activeArea.setBackgroundColor(Color.parseColor("#E61F33"));
        if (in1 != null)        in1.setBackgroundColor(Color.parseColor("#403B3D"));
        if (in2 != null)        in2.setBackgroundColor(Color.parseColor("#403B3D"));

        applyTintColor(activeImg, 0xFF221D19);
        applyTintColor(inImg1,   0xFFE0E0E0);
        applyTintColor(inImg2,   0xFFE0E0E0);

        if (activeTxt != null) activeTxt.setTextColor(0xFF221D19);
        if (inTxt1    != null) inTxt1.setTextColor(0xFFE0E0E0);
        if (inTxt2    != null) inTxt2.setTextColor(0xFFE0E0E0);
    }

    private void applyTintColor(ImageView img, int color) {
        if (img == null) return;
        if (img.getBackground() != null)
            img.getBackground().setColorFilter(color, PorterDuff.Mode.SRC_IN);
        img.setColorFilter(color, PorterDuff.Mode.SRC_IN);
    }

    private void stepSens(GamingSeekBar bar, TextView label, int delta, boolean isX) {
        if (bar == null) return;
        int p = Math.max(0, Math.min(bar.getMax(), bar.getProgress() + delta));
        bar.setProgress(p);
        float v = 1.0f + p / 100.f;
        if (isX) sensX = v; else sensY = v;
        if (label != null) label.setText(String.format(Locale.US, "%.2fx", v));
        if (workerRunning) doStart();
    }

    private void stepGeneric(GamingSeekBar bar, TextView valTv, int delta) {
        if (bar == null) return;
        int p = Math.max(0, Math.min(bar.getMax(), bar.getProgress() + delta));
        bar.setProgress(p);
        if (valTv != null) valTv.setText(p + "%");
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