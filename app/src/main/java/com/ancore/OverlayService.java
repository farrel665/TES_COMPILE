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
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

/** Small global touch-sensitivity overlay. No macro/trigger subsystem. */
public class OverlayService extends Service {
    private static final String TAG = "ancore_overlay";
    private static final String CH_ID = "ancore_overlay";
    public static final String ACTION_SHOW = "com.ancore.OVERLAY_SHOW";
    public static final String ACTION_HIDE = "com.ancore.OVERLAY_HIDE";
    public static final String ACTION_STOP = "com.ancore.OVERLAY_STOP";

    private WindowManager wm;
    private View root, panel, bubbleView;
    private ViewGroup pageHost;
    private WindowManager.LayoutParams panelLp, bubbleLp;
    private final Handler main = new Handler(Looper.getMainLooper());

    private int sensArea = 1;
    private float sensX = 1.00f, sensY = 1.00f;
    private boolean tactix, workerRunning;
    private float strength = 25f, responsiveness = 70f;

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
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return; }
        inflateOverlay();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_HIDE.equals(action)) hidePanel();
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
            NotificationChannel ch = new NotificationChannel(CH_ID, "Magic Manager", NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
        Intent open = new Intent(this, OverlayService.class).setAction(ACTION_SHOW);
        PendingIntent pi = PendingIntent.getService(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CH_ID) : new Notification.Builder(this);
        startForeground(42, b.setContentTitle("Magic Manager")
                .setContentText("Touch sensitivity control")
                .setSmallIcon(android.R.drawable.ic_menu_manage)
                .setContentIntent(pi).setOngoing(true).build());
    }

    private void inflateOverlay() {
        root = LayoutInflater.from(this).inflate(R.layout.overlay_panel, null);
        panel = root.findViewById(R.id.panel);
        pageHost = root.findViewById(R.id.page_host);
        setupPanelWindow();
        TextView hide = root.findViewById(R.id.btn_hide);
        TextView close = root.findViewById(R.id.btn_close);
        if (hide != null) hide.setOnClickListener(v -> hidePanel());
        if (close != null) close.setOnClickListener(v -> stopWorkerAndSelf());
        showMainPage();
    }

    private void setupPanelWindow() {
        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        panelLp = new WindowManager.LayoutParams(
                dp(300), dp(330), type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        panelLp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        panelLp.x = 0;
        panelLp.y = dp(36);
        try {
            wm.addView(root, panelLp);
        } catch (Throwable t) {
            Toast.makeText(this, "Overlay failed: " + t.getMessage(), Toast.LENGTH_LONG).show();
            stopSelf();
        }
    }

    private void showMainPage() {
        if (pageHost == null) return;
        pageHost.removeAllViews();
        View v = LayoutInflater.from(this).inflate(R.layout.page_main, pageHost, false);
        pageHost.addView(v);
        statusTv = v.findViewById(R.id.status_text);
        View sensitivity = v.findViewById(R.id.btn_sensitivity_manager);
        if (sensitivity != null) sensitivity.setOnClickListener(x -> showSensitivityPage());
    }

    private void showSensitivityPage() {
        if (pageHost == null) return;
        pageHost.removeAllViews();
        View v = LayoutInflater.from(this).inflate(R.layout.page_sensitivity, pageHost, false);
        pageHost.addView(v);
        bindSensitivity(v);
        View back = v.findViewById(R.id.btn_back_sens);
        if (back != null) back.setOnClickListener(x -> showMainPage());
    }

    private void bindSensitivity(View v) {
        valX = v.findViewById(R.id.val_sens_x);
        valY = v.findViewById(R.id.val_sens_y);
        valStrength = v.findViewById(R.id.val_strength);
        valResponsiveness = v.findViewById(R.id.val_responsiveness);
        switchActive = v.findViewById(R.id.switch_active);
        checkTactix = v.findViewById(R.id.check_tactix);
        imgResetX = v.findViewById(R.id.img_reset_x);
        imgResetY = v.findViewById(R.id.img_reset_y);
        seekStrength = v.findViewById(R.id.seek_strength);
        seekResponsiveness = v.findViewById(R.id.seek_responsiveness);
        areaLeft = v.findViewById(R.id.area_left);
        areaAll = v.findViewById(R.id.area_all);
        areaRight = v.findViewById(R.id.area_right);
        areaLeftText = v.findViewById(R.id.area_left_text);
        areaAllText = v.findViewById(R.id.area_all_text);
        areaRightText = v.findViewById(R.id.area_right_text);

        GamingSeekBar sx = v.findViewById(R.id.seek_sens_x);
        GamingSeekBar sy = v.findViewById(R.id.seek_sens_y);
        configureSensitivity(sx, true);
        configureSensitivity(sy, false);
        imgResetX.setOnClickListener(x -> resetX(sx));
        imgResetY.setOnClickListener(x -> resetY(sy));

        checkTactix.setChecked(tactix);
        checkTactix.setOnCheckedChangeListener((b, checked) -> {
            tactix = checked;
            restartIfRunning();
        });

        seekStrength.setMin(0);
        seekStrength.setMax(100);
        seekStrength.setProgress(Math.round(strength));
        seekStrength.setOnProgressChangeListener((b,p,user) -> {
            strength = p;
            setPercent(valStrength, strength);
            if (user) restartIfRunning();
        });

        seekResponsiveness.setMin(0);
        seekResponsiveness.setMax(100);
        seekResponsiveness.setProgress(Math.round(responsiveness));
        seekResponsiveness.setOnProgressChangeListener((b,p,user) -> {
            responsiveness = p;
            setPercent(valResponsiveness, responsiveness);
            if (user) restartIfRunning();
        });
        setPercent(valStrength, strength);
        setPercent(valResponsiveness, responsiveness);

        View.OnClickListener areaListener = x -> {
            sensArea = x == areaLeft ? 0 : x == areaAll ? 1 : 2;
            refreshAreaUi();
            restartIfRunning();
        };
        areaLeft.setOnClickListener(areaListener);
        areaAll.setOnClickListener(areaListener);
        areaRight.setOnClickListener(areaListener);

        switchActive.setChecked(workerRunning, false);
        switchActive.setOnCheckedChangeListener((sw, checked) -> {
            if (checked) doStart();
            else doStop();
        });
        refreshAreaUi();
    }

    private void configureSensitivity(GamingSeekBar bar, boolean horizontal) {
        bar.setMin(0);
        bar.setMax(400);
        bar.setStep(1);
        float current = horizontal ? sensX : sensY;
        bar.setProgress(Math.round((current - 1f) * 100f));
        bar.setButtonSize(22);
        bar.setOnProgressChangeListener((b,p,user) -> {
            float v = 1f + clamp(p, 0, 400) / 100f;
            if (horizontal) {
                sensX = v;
                setSensitivityText(valX, v);
            } else {
                sensY = v;
                setSensitivityText(valY, v);
            }
            if (user) restartIfRunning();
        });
        setSensitivityText(horizontal ? valX : valY, current);
    }

    private void resetX(GamingSeekBar b) {
        sensX = 2f;
        b.setProgress(100);
        setSensitivityText(valX, sensX);
        restartIfRunning();
    }

    private void resetY(GamingSeekBar b) {
        sensY = 2f;
        b.setProgress(100);
        setSensitivityText(valY, sensY);
        restartIfRunning();
    }

    private void setSensitivityText(TextView t, float v) {
        if (t != null) t.setText(String.format(Locale.US, "%.2f×", v));
    }

    private void setPercent(TextView t, float v) {
        if (t != null) t.setText(String.format(Locale.US, "%.0f%%", v));
    }

    private void doStart() {
        if (switchActive != null && !switchActive.isChecked()) return;
        setStatus("Starting…");
        main.post(() -> {
            try {
                if (!ShizukuHelper.isReady()) {
                    ShizukuHelper.init();
                    main.postDelayed(() -> {
                        if (switchActive != null && switchActive.isChecked()) {
                            if (ShizukuHelper.isReady()) doStart();
                            else {
                                workerRunning = false;
                                switchActive.setChecked(false, true);
                                setStatus("Shizuku not connected");
                            }
                        }
                    }, 500);
                    return;
                }
                String bin = getApplicationInfo().nativeLibraryDir + "/libancoreengine.so";
                // X is horizontal, Y is vertical. Keep this order consistent end-to-end.
                boolean ok = ShizukuHelper.startWorker(bin, 0, sensX, sensY,
                        sensArea, tactix ? 1 : 0, strength, responsiveness);
                workerRunning = ok;
                if (ok) setStatus("Sensitivity active");
                else {
                    switchActive.setChecked(false, true);
                    setStatus("Start failed — Shizuku/input access unavailable");
                }
            } catch (Throwable t) {
                workerRunning = false;
                if (switchActive != null) switchActive.setChecked(false, true);
                setStatus("Error: " + t.getMessage());
            }
        });
    }

    private void doStop() {
        try { ShizukuHelper.stopWorker(); } catch (Throwable ignored) {}
        workerRunning = false;
        if (switchActive != null && switchActive.isChecked()) switchActive.setChecked(false, true);
        setStatus("Ready • system touch restored");
    }

    private void restartIfRunning() {
        if (!workerRunning) return;
        doStop();
        main.postDelayed(this::doStart, 120);
    }

    private void refreshAreaUi() {
        if (areaLeft == null) return;
        LinearLayout[] vs = {areaLeft, areaAll, areaRight};
        TextView[] ls = {areaLeftText, areaAllText, areaRightText};
        for (int i = 0; i < 3; i++) {
            if (vs[i] instanceof HexagonLinearLayout) {
                HexagonLinearLayout h = (HexagonLinearLayout) vs[i];
                h.setBackgroundColor(i == sensArea ? 0xFFE51010 : 0xFF262323);
                h.setStrokeColor(i == sensArea ? 0xFFE51010 : 0xFF363232);
                h.setStrokeWidth(1);
                h.setCornerCut(9);
            }
            ls[i].setTextColor(i == sensArea ? 0xFFFFFFFF : 0xFFB0B3BA);
        }
    }

    private void setStatus(String s) { if (statusTv != null) statusTv.setText(s); }

    private void hidePanel() {
        if (panel != null) panel.setVisibility(View.GONE);
        if (root != null && panelLp != null && wm != null) {
            panelLp.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            try { wm.updateViewLayout(root, panelLp); } catch (Throwable ignored) {}
        }
        addBubble();
    }

    private void showPanel() {
        removeBubble();
        if (root != null && panelLp != null && wm != null) {
            panelLp.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            try { wm.updateViewLayout(root, panelLp); } catch (Throwable ignored) {}
        }
        if (panel != null) panel.setVisibility(View.VISIBLE);
        showMainPage();
    }

    private void addBubble() {
        if (wm == null || bubbleView != null) return;
        bubbleView = new TextView(this);
        bubbleView.setText("M");
        bubbleView.setTextColor(Color.WHITE);
        bubbleView.setTextSize(14f);
        bubbleView.setGravity(Gravity.CENTER);
        bubbleView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(0xFF1D1D1D);
        bg.setStroke(dp(2), 0xFFE51010);
        bubbleView.setBackground(bg);
        bubbleView.setOnClickListener(v -> showPanel());

        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        int size = dp(46);
        bubbleLp = new WindowManager.LayoutParams(size, size, type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        bubbleLp.gravity = Gravity.TOP | Gravity.END;
        bubbleLp.x = dp(14);
        bubbleLp.y = dp(70);
        try { wm.addView(bubbleView, bubbleLp); }
        catch (Throwable t) { bubbleView = null; bubbleLp = null; }
    }

    private void removeBubble() {
        if (bubbleView != null && wm != null) {
            try { wm.removeView(bubbleView); } catch (Throwable ignored) {}
            bubbleView = null;
            bubbleLp = null;
        }
    }

    private void removeOverlay() {
        removeBubble();
        if (root != null && wm != null) {
            try { wm.removeView(root); } catch (Throwable ignored) {}
            root = null;
        }
    }

    private void stopWorkerAndSelf() {
        doStop();
        removeOverlay();
        stopForeground(true);
        stopSelf();
    }

    private static float clamp(float v, float min, float max) { return Math.max(min, Math.min(max, v)); }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    public static void launch(Context ctx) {
        Intent i = new Intent(ctx, OverlayService.class).setAction(ACTION_SHOW);
        if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i);
        else ctx.startService(i);
    }
}
