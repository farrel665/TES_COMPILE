package com.ancore;

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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

/** Shizuku-backed floating overlay. */
public class OverlayService extends Service {
    private static final String TAG = "ancore_overlay";
    private static final String CH_ID = "ancore_overlay";
    public static final String ACTION_SHOW = "com.ancore.OVERLAY_SHOW";
    public static final String ACTION_HIDE = "com.ancore.OVERLAY_HIDE";
    public static final String ACTION_STOP = "com.ancore.OVERLAY_STOP";

    private WindowManager wm;
    private View root, panel, bubble;
    private WindowManager.LayoutParams lp;
    private final Handler main = new Handler(Looper.getMainLooper());

    private int sensArea = 1;
    private float sensX = 1.00f;
    private float sensY = 1.00f;
    private boolean tactix;
    private boolean workerRunning;
    private boolean macroRunning;
    private int macroIntervalMs = 100;
    private TextView macroHoldButton, valMacroInterval;
    private GamingSeekBar seekMacroInterval;
    private float strength;
    private float responsiveness;

    private TextView statusTv, valX, valY, valStrength, valResponsiveness;
    private LinearLayout areaLeft, areaAll, areaRight;
    private TextView areaLeftText, areaAllText, areaRightText;
    private GamingSwitch switchActive;
    private GamingSeekBar seekStrength, seekResponsiveness;
    private OctagonCheckBox checkTactix;
    private ImageView imgResetX, imgResetY;


    @Override public void onCreate() {
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

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_SHOW.equals(action)) showPanel();
        else if (ACTION_HIDE.equals(action)) hideToBubble();
        else if (ACTION_STOP.equals(action)) stopWorkerAndSelf();
        else showPanel();
        return START_STICKY;
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onDestroy() {
        doStop();
        removeOverlay();
        super.onDestroy();
    }

    private void startForegroundNotif() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CH_ID, "Magic Manager",
                    NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
        Intent open = new Intent(this, OverlayService.class).setAction(ACTION_SHOW);
        PendingIntent pi = PendingIntent.getService(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CH_ID) : new Notification.Builder(this);
        Notification n = b.setContentTitle("Magic Manager")
                .setContentText("Free Fire • com.dts.freefireth")
                .setSmallIcon(android.R.drawable.ic_menu_manage)
                .setContentIntent(pi).setOngoing(true).build();
        startForeground(42, n);
    }

    private void inflateOverlay() {
        root = LayoutInflater.from(this).inflate(R.layout.overlay_panel, null);
        panel = root.findViewById(R.id.panel);
        if (panel instanceof HexagonLinearLayout) {
            HexagonLinearLayout hp = (HexagonLinearLayout) panel;
            hp.setBackgroundColor(0xF21C1C21);
            hp.setStrokeColor(0xFF3A3A40);
            hp.setStrokeWidth(1f);
            hp.setCornerCut(18f);
        }
        bubble = root.findViewById(R.id.bubble);
        statusTv = root.findViewById(R.id.status_text);
        valX = root.findViewById(R.id.val_sens_x);
        valY = root.findViewById(R.id.val_sens_y);
        valStrength = root.findViewById(R.id.val_strength);
        valResponsiveness = root.findViewById(R.id.val_responsiveness);

        switchActive = root.findViewById(R.id.switch_active);
        seekStrength = root.findViewById(R.id.seek_strength);
        seekResponsiveness = root.findViewById(R.id.seek_responsiveness);
        checkTactix = root.findViewById(R.id.check_tactix);
        imgResetX = root.findViewById(R.id.img_reset_x);
        imgResetY = root.findViewById(R.id.img_reset_y);
        macroHoldButton = root.findViewById(R.id.macro_hold_button);
        valMacroInterval = root.findViewById(R.id.val_macro_interval);
        seekMacroInterval = root.findViewById(R.id.seek_macro_interval);

        areaLeft = root.findViewById(R.id.area_left);
        areaAll = root.findViewById(R.id.area_all);
        areaRight = root.findViewById(R.id.area_right);
        areaLeftText = root.findViewById(R.id.area_left_text);
        areaAllText = root.findViewById(R.id.area_all_text);
        areaRightText = root.findViewById(R.id.area_right_text);

        GamingSeekBar sx = root.findViewById(R.id.seek_sens_x);
        GamingSeekBar sy = root.findViewById(R.id.seek_sens_y);
        configureSensitivity(sx, true);
        configureSensitivity(sy, false);

        if (imgResetX != null) imgResetX.setOnClickListener(v -> resetX(sx));
        if (imgResetY != null) imgResetY.setOnClickListener(v -> resetY(sy));

        if (checkTactix != null) {
            checkTactix.setChecked(false);
            checkTactix.setOnCheckedChangeListener((button, checked) -> {
                tactix = checked;
                restartIfRunning();
            });
        }
        if (seekStrength != null) {
            seekStrength.setMin(0); seekStrength.setMax(100); seekStrength.setStep(1);
            seekStrength.setProgress(0);
            seekStrength.setOnProgressChangeListener((bar, progress, fromUser) -> {
                strength = clamp(progress, 0f, 100f);
                setPercent(valStrength, strength);
                if (fromUser) restartIfRunning();
            });
        }
        if (seekResponsiveness != null) {
            seekResponsiveness.setMin(0); seekResponsiveness.setMax(100); seekResponsiveness.setStep(1);
            seekResponsiveness.setProgress(0);
            seekResponsiveness.setOnProgressChangeListener((bar, progress, fromUser) -> {
                responsiveness = clamp(progress, 0f, 100f);
                setPercent(valResponsiveness, responsiveness);
                if (fromUser) restartIfRunning();
            });
        }

        if (seekMacroInterval != null) {
            seekMacroInterval.setMin(20);
            seekMacroInterval.setMax(500);
            seekMacroInterval.setStep(10);
            seekMacroInterval.setProgress(macroIntervalMs);
            seekMacroInterval.setOnProgressChangeListener((bar, progress, fromUser) -> {
                macroIntervalMs = clamp(progress, 20, 500);
                if (valMacroInterval != null) valMacroInterval.setText(macroIntervalMs + "ms");
                if (macroRunning) {
                    stopMacro();
                    startMacro();
                }
            });
            seekMacroInterval.setButtonSize(22);
        }
        if (valMacroInterval != null) valMacroInterval.setText(macroIntervalMs + "ms");
        if (macroHoldButton != null) {
            macroHoldButton.setOnTouchListener((v, event) -> {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        v.setPressed(true);
                        startMacro();
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        v.setPressed(false);
                        stopMacro();
                        return true;
                    default:
                        return true;
                }
            });
        }

        View.OnClickListener areaListener = v -> {
            if (v == areaLeft) sensArea = 0;
            else if (v == areaAll) sensArea = 1;
            else sensArea = 2;
            refreshAreaUi();
            restartIfRunning();
        };
        areaLeft.setOnClickListener(areaListener);
        areaAll.setOnClickListener(areaListener);
        areaRight.setOnClickListener(areaListener);

        if (switchActive != null) {
            // Explicitly force initial OFF before listener is installed.
            switchActive.setChecked(false, false);
            switchActive.setOnCheckedChangeListener((sw, checked) -> {
                if (checked) doStart(); else doStop();
            });
        }

        root.findViewById(R.id.btn_hide).setOnClickListener(v -> hideToBubble());
        root.findViewById(R.id.btn_close).setOnClickListener(v -> stopWorkerAndSelf());

        // Panel is intentionally fixed: no drag listener is installed.
        View header = root.findViewById(R.id.drag_handle);
        if (header != null) header.setOnClickListener(v -> { /* fixed header */ });
        if (bubble != null) bubble.setOnClickListener(v -> showPanel());

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
        lp.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
        lp.x = dp(8);
        lp.y = 0;

        try {
            wm.addView(root, lp);
            refreshAreaUi();
            showPanel();
        } catch (Throwable t) {
            Log.e(TAG, "addView failed", t);
            Toast.makeText(this, "Overlay failed: " + t.getMessage(), Toast.LENGTH_LONG).show();
            stopSelf();
        }
    }

    private void configureSensitivity(GamingSeekBar bar, boolean horizontal) {
        if (bar == null) return;
        bar.setMin(0);
        bar.setMax(400); // 1.00 + 400/100 = 5.00
        bar.setStep(1);
        float value = horizontal ? sensX : sensY;
        bar.setProgress(Math.round((value - 1.0f) * 100f));
        bar.setOnProgressChangeListener((b, progress, fromUser) -> {
            float v = 1.0f + clamp(progress, 0, 400) / 100f;
            if (horizontal) {
                sensX = v; setSensitivityText(valX, sensX);
            } else {
                sensY = v; setSensitivityText(valY, sensY);
            }
            if (fromUser) restartIfRunning();
        });
        if (horizontal) setSensitivityText(valX, sensX);
        else setSensitivityText(valY, sensY);
    }

    private void resetX(GamingSeekBar bar) {
        sensX = 2.00f;
        if (bar != null) bar.setProgress(100);
        setSensitivityText(valX, sensX);
        restartIfRunning();
    }

    private void resetY(GamingSeekBar bar) {
        sensY = 2.00f;
        if (bar != null) bar.setProgress(100);
        setSensitivityText(valY, sensY);
        restartIfRunning();
    }

    private void setSensitivityText(TextView tv, float value) {
        if (tv != null) tv.setText(String.format(Locale.US, "%.2f×", value));
    }

    private void setPercent(TextView tv, float value) {
        if (tv != null) tv.setText(String.format(Locale.US, "%.0f%%", value));
    }

    private void restartIfRunning() {
        if (workerRunning) doStart();
    }

    private void showPanel() {
        if (panel != null) panel.setVisibility(View.VISIBLE);
        if (bubble != null) bubble.setVisibility(View.GONE);
        resizeWrap();
    }

    private void hideToBubble() {
        if (panel != null) panel.setVisibility(View.GONE);
        if (bubble != null) bubble.setVisibility(View.VISIBLE);
        if (lp != null) {
            lp.width = dp(52); lp.height = dp(52);
            try { wm.updateViewLayout(root, lp); } catch (Throwable ignored) {}
        }
    }

    private void resizeWrap() {
        if (lp != null) {
            lp.width = WindowManager.LayoutParams.WRAP_CONTENT;
            lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
            try { wm.updateViewLayout(root, lp); } catch (Throwable ignored) {}
        }
    }

    private void startMacro() {
        if (macroRunning) return;
        if (!ShizukuHelper.isReady()) {
            ShizukuHelper.init();
            if (statusTv != null) statusTv.setText("Waiting for Shizuku…");
            return;
        }
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int x = dm.widthPixels / 2;
        int y = dm.heightPixels / 2;
        boolean ok = ShizukuHelper.startMacro(x, y, macroIntervalMs);
        macroRunning = ok;
        if (ok) {
            if (statusTv != null) statusTv.setText("Macro running • " + macroIntervalMs + "ms");
        } else if (statusTv != null) {
            statusTv.setText("Macro start failed — check Shizuku");
        }
    }

    private void stopMacro() {
        if (!macroRunning) return;
        try { ShizukuHelper.stopMacro(); } catch (Throwable t) { Log.e(TAG, "stopMacro", t); }
        macroRunning = false;
        if (statusTv != null && !workerRunning) statusTv.setText("Disengaged");
    }

    private void doStart() {
        if (switchActive != null && !switchActive.isChecked()) return;
        if (statusTv != null) statusTv.setText("Starting…");
        main.post(() -> {
            try {
                if (!ShizukuHelper.isReady()) {
                    ShizukuHelper.init();
                    main.postDelayed(() -> {
                        if (switchActive != null && switchActive.isChecked() && !workerRunning) {
                            if (ShizukuHelper.isReady()) doStart();
                            else {
                                switchActive.setChecked(false, true);
                                if (statusTv != null) statusTv.setText("Shizuku not connected");
                            }
                        }
                    }, 450);
                    return;
                }
                String bin = getApplicationInfo().nativeLibraryDir + "/libancoreengine.so";
                if (!new java.io.File(bin).exists()) bin = "/data/local/tmp/ancore_engine";

                boolean ok = ShizukuHelper.startWorker(bin, 0, sensX, sensY,
                        sensArea, tactix ? 1 : 0, strength, responsiveness);
                workerRunning = ok;
                if (ok) {
                    String[] areas = {"LEFT", "ALL", "RIGHT"};
                    if (statusTv != null) statusTv.setText("Engaged  X="
                            + String.format(Locale.US, "%.2f", sensX)
                            + " Y=" + String.format(Locale.US, "%.2f", sensY)
                            + "  " + areas[sensArea]);
                } else {
                    if (switchActive != null) switchActive.setChecked(false, true);
                    if (statusTv != null) statusTv.setText("Start failed — check Shizuku / binary");
                }
            } catch (Throwable t) {
                workerRunning = false;
                if (switchActive != null) switchActive.setChecked(false, true);
                if (statusTv != null) statusTv.setText("Error: " + t.getMessage());
                Log.e(TAG, "doStart", t);
            }
        });
    }

    private void doStop() {
        stopMacro();
        try { ShizukuHelper.stopWorker(); }
        catch (Throwable t) { Log.e(TAG, "stopWorker", t); }
        workerRunning = false;
        if (switchActive != null && switchActive.isChecked()) switchActive.setChecked(false, true);
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

    private void refreshAreaUi() {
        if (areaLeft == null || areaAll == null || areaRight == null) return;
        LinearLayout[] views = {areaLeft, areaAll, areaRight};
        TextView[] labels = {areaLeftText, areaAllText, areaRightText};
        for (int i = 0; i < views.length; i++) {
            if (views[i] instanceof HexagonLinearLayout) {
                HexagonLinearLayout h = (HexagonLinearLayout) views[i];
                h.setBackgroundColor(i == sensArea ? 0xFFE51010 : 0xFF262323);
                h.setStrokeColor(i == sensArea ? 0xFFE51010 : 0xFF363232);
                h.setStrokeWidth(1f);
                h.setCornerCut(10f);
            }
            if (labels[i] != null) {
                labels[i].setTextColor(i == sensArea ? 0xFFFFFFFF : 0xFFB0B3BA);
            }
        }
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
    private int dp(int v) {
        float d = getResources().getDisplayMetrics().density;
        return (int) (v * d + 0.5f);
    }

    public static void launch(Context ctx) {
        Intent i = new Intent(ctx, OverlayService.class).setAction(ACTION_SHOW);
        if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i);
        else ctx.startService(i);
    }
}
