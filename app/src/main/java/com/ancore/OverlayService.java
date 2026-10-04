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

/**
 * Small global touch-sensitivity overlay.
 * No macro/trigger subsystem.
 */
public class OverlayService extends Service {

    private static final String TAG = "ancore_overlay";
    private static final String CH_ID = "ancore_overlay";

    public static final String ACTION_SHOW =
            "com.ancore.OVERLAY_SHOW";

    public static final String ACTION_HIDE =
            "com.ancore.OVERLAY_HIDE";

    public static final String ACTION_STOP =
            "com.ancore.OVERLAY_STOP";

    private WindowManager wm;

    /*
     * IMPORTANT:
     * bubbleView is a TextView because addBubble()
     * uses setText(), setTextColor(), setTextSize(),
     * setGravity() and setTypeface().
     */
    private View root;
    private View panel;
    private TextView bubbleView;

    private ViewGroup pageHost;

    private WindowManager.LayoutParams panelLp;
    private WindowManager.LayoutParams bubbleLp;

    private final Handler main =
            new Handler(Looper.getMainLooper());

    private int sensArea = 1;

    private float sensX = 1.00f;
    private float sensY = 1.00f;

    private boolean tactix = false;
    private boolean workerRunning = false;

    private float strength = 70f;
    private float responsiveness = 70f;
    private float smoothness = 70f;

    private TextView statusTv;
    private TextView valX;
    private TextView valY;
    private TextView valSmoothness;

    private LinearLayout areaLeft;
    private LinearLayout areaAll;
    private LinearLayout areaRight;

    private TextView areaLeftText;
    private TextView areaAllText;
    private TextView areaRightText;

    private GamingSwitch switchActive;

    private GamingSeekBar seekSmoothness;

    private OctagonCheckBox checkTactix;

    private ImageView imgResetX;
    private ImageView imgResetY;

    @Override
    public void onCreate() {
        super.onCreate();

        wm = (WindowManager)
                getSystemService(WINDOW_SERVICE);

        startForegroundNotif();

        if (!Settings.canDrawOverlays(this)) {
            stopSelf();
            return;
        }

        inflateOverlay();
    }

    @Override
    public int onStartCommand(
            Intent intent,
            int flags,
            int startId) {

        String action =
                intent == null ? null : intent.getAction();

        if (ACTION_HIDE.equals(action)) {

            hidePanel();

        } else if (ACTION_STOP.equals(action)) {

            stopWorkerAndSelf();

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

        doStop();
        removeOverlay();

        super.onDestroy();
    }

    private void startForegroundNotif() {

        if (Build.VERSION.SDK_INT >= 26) {

            NotificationChannel ch =
                    new NotificationChannel(
                            CH_ID,
                            "Magic Manager",
                            NotificationManager.IMPORTANCE_LOW
                    );

            ch.setShowBadge(false);

            NotificationManager nm =
                    getSystemService(NotificationManager.class);

            if (nm != null) {
                nm.createNotificationChannel(ch);
            }
        }

        Intent open =
                new Intent(this, OverlayService.class)
                        .setAction(ACTION_SHOW);

        PendingIntent pi =
                PendingIntent.getService(
                        this,
                        0,
                        open,
                        PendingIntent.FLAG_UPDATE_CURRENT
                                | PendingIntent.FLAG_IMMUTABLE
                );

        Notification.Builder builder =
                Build.VERSION.SDK_INT >= 26
                        ? new Notification.Builder(this, CH_ID)
                        : new Notification.Builder(this);

        Notification notification =
                builder
                        .setContentTitle("Magic Manager")
                        .setContentText(
                                "Touch sensitivity control"
                        )
                        .setSmallIcon(
                                android.R.drawable.ic_menu_manage
                        )
                        .setContentIntent(pi)
                        .setOngoing(true)
                        .build();

        startForeground(42, notification);
    }

    private void inflateOverlay() {

        root = LayoutInflater
                .from(this)
                .inflate(
                        R.layout.overlay_panel,
                        null
                );

        panel =
                root.findViewById(R.id.panel);

        pageHost =
                root.findViewById(R.id.page_host);

        setupPanelWindow();

        TextView hide =
                root.findViewById(R.id.btn_hide);

        TextView close =
                root.findViewById(R.id.btn_close);

        if (hide != null) {
            hide.setOnClickListener(
                    v -> hidePanel()
            );
        }

        if (close != null) {
            close.setOnClickListener(
                    v -> stopWorkerAndSelf()
            );
        }

        showMainPage();
    }

    private void setupPanelWindow() {

        int type =
                Build.VERSION.SDK_INT >= 26
                        ? WindowManager.LayoutParams
                                .TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE;

        panelLp =
                new WindowManager.LayoutParams(
                        dp(300),
                        dp(330),
                        type,

                        WindowManager.LayoutParams
                                .FLAG_NOT_FOCUSABLE
                                | WindowManager.LayoutParams
                                .FLAG_LAYOUT_IN_SCREEN
                                | WindowManager.LayoutParams
                                .FLAG_HARDWARE_ACCELERATED,

                        PixelFormat.TRANSLUCENT
                );

        panelLp.gravity =
                Gravity.TOP | Gravity.CENTER_HORIZONTAL;

        panelLp.x = 0;
        panelLp.y = dp(36);

        try {

            wm.addView(root, panelLp);

        } catch (Throwable t) {

            Toast.makeText(
                    this,
                    "Overlay failed: " + t.getMessage(),
                    Toast.LENGTH_LONG
            ).show();

            stopSelf();
        }
    }

    private void showMainPage() {

        if (pageHost == null) {
            return;
        }

        pageHost.removeAllViews();

        View v =
                LayoutInflater
                        .from(this)
                        .inflate(
                                R.layout.page_main,
                                pageHost,
                                false
                        );

        pageHost.addView(v);

        statusTv =
                v.findViewById(
                        R.id.status_text
                );

        View sensitivity =
                v.findViewById(
                        R.id.btn_sensitivity_manager
                );

        if (sensitivity != null) {

            sensitivity.setOnClickListener(
                    x -> showSensitivityPage()
            );
        }
    }

    private void showSensitivityPage() {

        if (pageHost == null) {
            return;
        }

        pageHost.removeAllViews();

        View v =
                LayoutInflater
                        .from(this)
                        .inflate(
                                R.layout.page_sensitivity,
                                pageHost,
                                false
                        );

        pageHost.addView(v);

        bindSensitivity(v);

        View back =
                v.findViewById(
                        R.id.btn_back_sens
                );

        if (back != null) {

            back.setOnClickListener(
                    x -> showMainPage()
            );
        }
    }

    private void bindSensitivity(View v) {

        valX =
                v.findViewById(
                        R.id.val_sens_x
                );

        valY =
                v.findViewById(
                        R.id.val_sens_y
                );

        valSmoothness =
                v.findViewById(R.id.val_smoothness);

        switchActive =
                v.findViewById(
                        R.id.switch_active
                );

        checkTactix =
                v.findViewById(
                        R.id.check_tactix
                );

        imgResetX =
                v.findViewById(
                        R.id.img_reset_x
                );

        imgResetY =
                v.findViewById(
                        R.id.img_reset_y
                );

        seekSmoothness =
                v.findViewById(R.id.seek_smoothness);

        areaLeft =
                v.findViewById(
                        R.id.area_left
                );

        areaAll =
                v.findViewById(
                        R.id.area_all
                );

        areaRight =
                v.findViewById(
                        R.id.area_right
                );

        areaLeftText =
                v.findViewById(
                        R.id.area_left_text
                );

        areaAllText =
                v.findViewById(
                        R.id.area_all_text
                );

        areaRightText =
                v.findViewById(
                        R.id.area_right_text
                );

        GamingSeekBar sx =
                v.findViewById(
                        R.id.seek_sens_x
                );

        GamingSeekBar sy =
                v.findViewById(
                        R.id.seek_sens_y
                );

        configureSensitivity(sx, true);
        configureSensitivity(sy, false);

        if (imgResetX != null) {

            imgResetX.setOnClickListener(
                    x -> resetX(sx)
            );
        }

        if (imgResetY != null) {

            imgResetY.setOnClickListener(
                    x -> resetY(sy)
            );
        }

        if (checkTactix != null) {

            checkTactix.setChecked(tactix);

            checkTactix.setOnCheckedChangeListener(
                    (b, checked) -> {

                        tactix = checked;
                        restartIfRunning();
                    }
            );
        }

        if (seekSmoothness != null) {

            seekSmoothness.setMin(0);
            seekSmoothness.setMax(100);
            seekSmoothness.setProgress(Math.round(smoothness));
            bindExternalButtons(v, R.id.btn_smooth_minus, R.id.btn_smooth_plus, seekSmoothness);

            seekSmoothness.setOnProgressChangeListener((b, p, user) -> {
                smoothness = p;
                // Keep the legacy IPC fields synchronized so older native builds
                // still receive a consistent pair. Native uses their average.
                strength = smoothness;
                responsiveness = smoothness;
                setPercent(valSmoothness, smoothness);
                if (user) restartIfRunning();
            });
        }

        // External +/- buttons for sensitivity controls. The buttons are separate
        // views from GamingSeekBar and therefore never become part of its touch area.
        bindExternalButtons(v, R.id.btn_sens_x_minus, R.id.btn_sens_x_plus, sx);
        bindExternalButtons(v, R.id.btn_sens_y_minus, R.id.btn_sens_y_plus, sy);
        setPercent(valSmoothness, smoothness);

        View.OnClickListener areaListener =
                x -> {

                    if (x == areaLeft) {
                        sensArea = 0;
                    } else if (x == areaAll) {
                        sensArea = 1;
                    } else {
                        sensArea = 2;
                    }

                    refreshAreaUi();
                    restartIfRunning();
                };

        if (areaLeft != null) {
            areaLeft.setOnClickListener(areaListener);
        }

        if (areaAll != null) {
            areaAll.setOnClickListener(areaListener);
        }

        if (areaRight != null) {
            areaRight.setOnClickListener(areaListener);
        }

        if (switchActive != null) {

            switchActive.setChecked(
                    workerRunning,
                    false
            );

            switchActive.setOnCheckedChangeListener(
                    (sw, checked) -> {

                        if (checked) {
                            doStart();
                        } else {
                            doStop();
                        }
                    }
            );
        }

        refreshAreaUi();
    }

    private void bindExternalButtons(View rootView, int minusId, int plusId, GamingSeekBar bar) {
        if (rootView == null || bar == null) return;
        View minus = rootView.findViewById(minusId);
        View plus = rootView.findViewById(plusId);
        if (minus != null) minus.setOnClickListener(v -> bar.setProgress(bar.getProgress() - 1));
        if (plus != null) plus.setOnClickListener(v -> bar.setProgress(bar.getProgress() + 1));
    }

    private void configureSensitivity(
            GamingSeekBar bar,
            boolean horizontal) {

        if (bar == null) {
            return;
        }

        /*
         * 0..400 maps to:
         *
         * 1.00x .. 5.00x
         */
        bar.setMin(0);
        bar.setMax(400);
        bar.setStep(1);

        float current =
                horizontal ? sensX : sensY;

        bar.setProgress(
                Math.round(
                        (current - 1f) * 100f
                )
        );

        bar.setOnProgressChangeListener(
                (b, p, user) -> {

                    float value =
                            1f
                                    + clamp(
                                            p,
                                            0,
                                            400
                                    ) / 100f;

                    if (horizontal) {

                        sensX = value;

                        setSensitivityText(
                                valX,
                                value
                        );

                    } else {

                        sensY = value;

                        setSensitivityText(
                                valY,
                                value
                        );
                    }

                    if (user) {
                        restartIfRunning();
                    }
                }
        );

        setSensitivityText(
                horizontal
                        ? valX
                        : valY,
                current
        );
    }

    private void resetX(GamingSeekBar bar) {

        if (bar == null) {
            return;
        }

        sensX = 2f;

        bar.setProgress(100);

        setSensitivityText(
                valX,
                sensX
        );

        restartIfRunning();
    }

    private void resetY(GamingSeekBar bar) {

        if (bar == null) {
            return;
        }

        sensY = 2f;

        bar.setProgress(100);

        setSensitivityText(
                valY,
                sensY
        );

        restartIfRunning();
    }

    private void setSensitivityText(
            TextView t,
            float v) {

        if (t != null) {

            t.setText(
                    String.format(
                            Locale.US,
                            "%.2f×",
                            v
                    )
            );
        }
    }

    private void setPercent(
            TextView t,
            float v) {

        if (t != null) {

            t.setText(
                    String.format(
                            Locale.US,
                            "%.0f%%",
                            v
                    )
            );
        }
    }

    private void doStart() {

        if (switchActive != null
                && !switchActive.isChecked()) {

            return;
        }

        setStatus("Starting…");

        main.post(() -> {

            try {

                if (!ShizukuHelper.isReady()) {

                    ShizukuHelper.init();

                    main.postDelayed(
                            () -> {

                                if (switchActive != null
                                        && switchActive.isChecked()) {

                                    if (ShizukuHelper.isReady()) {

                                        doStart();

                                    } else {

                                        workerRunning = false;

                                        switchActive
                                                .setChecked(
                                                        false,
                                                        true
                                                );

                                        setStatus(
                                                "Shizuku not connected"
                                        );
                                    }
                                }

                            },
                            500
                    );

                    return;
                }

                String bin =
                        getApplicationInfo()
                                .nativeLibraryDir
                                + "/libancoreengine.so";

                /*
                 * X = horizontal
                 * Y = vertical
                 *
                 * Keep the order consistent
                 * with ShizukuHelper/UserService/native.
                 */
                boolean ok =
                        ShizukuHelper.startWorker(
                                bin,
                                0,
                                sensY,
                                sensX,
                                sensArea,
                                tactix ? 1 : 0,
                                strength,
                                responsiveness
                        );

                workerRunning = ok;

                if (ok) {

                    setStatus(
                            "Sensitivity active"
                    );

                } else {

                    if (switchActive != null) {

                        switchActive.setChecked(
                                false,
                                true
                        );
                    }

                    setStatus(
                            "Start failed — Shizuku/input access unavailable"
                    );
                }

            } catch (Throwable t) {

                workerRunning = false;

                if (switchActive != null) {

                    switchActive.setChecked(
                            false,
                            true
                    );
                }

                setStatus(
                        "Error: " + t.getMessage()
                );
            }
        });
    }

    private void doStop() {

        try {

            ShizukuHelper.stopWorker();

        } catch (Throwable ignored) {
        }

        workerRunning = false;

        if (switchActive != null
                && switchActive.isChecked()) {

            switchActive.setChecked(
                    false,
                    true
            );
        }

        setStatus(
                "Ready • system touch restored"
        );
    }

    private void restartIfRunning() {

        if (!workerRunning) {
            return;
        }

        doStop();

        main.postDelayed(
                this::doStart,
                120
        );
    }

    private void refreshAreaUi() {

        if (areaLeft == null
                || areaAll == null
                || areaRight == null) {

            return;
        }

        LinearLayout[] views = {
                areaLeft,
                areaAll,
                areaRight
        };

        TextView[] labels = {
                areaLeftText,
                areaAllText,
                areaRightText
        };

        for (int i = 0; i < 3; i++) {

            if (views[i] instanceof HexagonLinearLayout) {

                HexagonLinearLayout h =
                        (HexagonLinearLayout) views[i];

                h.setBackgroundColor(
                        i == sensArea
                                ? 0xFFE51010
                                : 0xFF262323
                );

                h.setStrokeColor(
                        i == sensArea
                                ? 0xFFE51010
                                : 0xFF363232
                );

                h.setStrokeWidth(1);
                h.setCornerCut(9);
            }

            if (labels[i] != null) {

                labels[i].setTextColor(
                        i == sensArea
                                ? 0xFFFFFFFF
                                : 0xFFB0B3BA
                );
            }
        }
    }

    private void setStatus(String text) {

        if (statusTv != null) {
            statusTv.setText(text);
        }
    }

    /**
     * Hide the main panel but keep a small M bubble.
     *
     * FLAG_NOT_TOUCHABLE is applied to the panel window so
     * the hidden panel cannot block touches underneath.
     */
    private void hidePanel() {

        if (panel != null) {
            panel.setVisibility(View.GONE);
        }

        if (root != null
                && panelLp != null
                && wm != null) {

            panelLp.flags |=
                    WindowManager.LayoutParams
                            .FLAG_NOT_TOUCHABLE;

            try {

                wm.updateViewLayout(
                        root,
                        panelLp
                );

            } catch (Throwable ignored) {
            }
        }

        addBubble();
    }

    /**
     * Restore the panel and remove the bubble.
     */
    private void showPanel() {

        removeBubble();

        if (root != null
                && panelLp != null
                && wm != null) {

            panelLp.flags &=
                    ~WindowManager.LayoutParams
                            .FLAG_NOT_TOUCHABLE;

            try {

                wm.updateViewLayout(
                        root,
                        panelLp
                );

            } catch (Throwable ignored) {
            }
        }

        if (panel != null) {
            panel.setVisibility(View.VISIBLE);
        }

        showMainPage();
    }

    /**
     * Creates the M bubble.
     *
     * IMPORTANT:
     * bubbleView is TextView, not View.
     */
    private void addBubble() {

        if (wm == null
                || bubbleView != null) {

            return;
        }

        bubbleView = new TextView(this);

        bubbleView.setText("M");

        bubbleView.setTextColor(
                Color.WHITE
        );

        bubbleView.setTextSize(14f);

        bubbleView.setGravity(
                Gravity.CENTER
        );

        bubbleView.setTypeface(
                android.graphics.Typeface.DEFAULT_BOLD
        );

        GradientDrawable bg =
                new GradientDrawable();

        bg.setShape(
                GradientDrawable.OVAL
        );

        bg.setColor(
                0xFF1D1D1D
        );

        bg.setStroke(
                dp(2),
                0xFFE51010
        );

        bubbleView.setBackground(bg);

        bubbleView.setOnClickListener(v -> {
            showPanel();
            main.post(this::showSensitivityPage);
        });

        int type =
                Build.VERSION.SDK_INT >= 26
                        ? WindowManager.LayoutParams
                                .TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE;

        int size = dp(46);

        bubbleLp =
                new WindowManager.LayoutParams(
                        size,
                        size,
                        type,

                        WindowManager.LayoutParams
                                .FLAG_NOT_FOCUSABLE
                                | WindowManager.LayoutParams
                                .FLAG_LAYOUT_IN_SCREEN
                                | WindowManager.LayoutParams
                                .FLAG_HARDWARE_ACCELERATED,

                        PixelFormat.TRANSLUCENT
                );

        bubbleLp.gravity =
                Gravity.TOP | Gravity.END;

        bubbleLp.x = dp(14);
        bubbleLp.y = dp(70);

        try {

            wm.addView(
                    bubbleView,
                    bubbleLp
            );

        } catch (Throwable t) {

            bubbleView = null;
            bubbleLp = null;
        }
    }

    private void removeBubble() {

        if (bubbleView != null
                && wm != null) {

            try {

                wm.removeView(
                        bubbleView
                );

            } catch (Throwable ignored) {
            }

            bubbleView = null;
            bubbleLp = null;
        }
    }

    private void removeOverlay() {

        removeBubble();

        if (root != null
                && wm != null) {

            try {

                wm.removeView(root);

            } catch (Throwable ignored) {
            }

            root = null;
        }
    }

    private void stopWorkerAndSelf() {

        doStop();

        removeOverlay();

        stopForeground(true);

        stopSelf();
    }

    private static float clamp(
            float v,
            float min,
            float max) {

        return Math.max(
                min,
                Math.min(max, v)
        );
    }

    private int dp(int value) {

        return Math.round(
                value
                        * getResources()
                                .getDisplayMetrics()
                                .density
        );
    }

    public static void launch(Context ctx) {

        Intent i =
                new Intent(
                        ctx,
                        OverlayService.class
                );

        i.setAction(ACTION_SHOW);

        if (Build.VERSION.SDK_INT >= 26) {

            ctx.startForegroundService(i);

        } else {

            ctx.startService(i);
        }
    }
}