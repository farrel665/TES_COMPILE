package com.ancore;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

/** Fixed left-side panel + separate touch-through macro trigger window. */
public class OverlayService extends Service implements TriggerView.Listener {
    private static final String TAG = "ancore_overlay";
    private static final String CH_ID = "ancore_overlay";
    private static final String PREF = "macro_trigger";
    public static final String ACTION_SHOW = "com.ancore.OVERLAY_SHOW";
    public static final String ACTION_HIDE = "com.ancore.OVERLAY_HIDE";
    public static final String ACTION_STOP = "com.ancore.OVERLAY_STOP";

    private WindowManager wm;
    private View root, panel, bubble;
    private ViewGroup pageHost;
    private WindowManager.LayoutParams bubbleLp;
    private WindowManager.LayoutParams panelLp;
    private TriggerView triggerView;
    private WindowManager.LayoutParams triggerLp;
    private final Handler main = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;

    private int sensArea = 1;
    private float sensX = 1.00f, sensY = 1.00f;
    private boolean tactix, workerRunning, macroRunning;
    private int macroIntervalMs = 70, macroDurationMs = 70;
    private int triggerSizeDp = 100;
    private float triggerOpacity = .90f;
    private float strength, responsiveness;
    private boolean macroPageOpen;
    private boolean triggerEnabled;
    private float dragStartLpX, dragStartLpY;

    private TextView statusTv, valX, valY, valStrength, valResponsiveness;
    private TextView valMacroInterval, valMacroDuration, triggerPos;
    private GamingSeekBar seekMacroInterval, seekMacroDuration;
    private LinearLayout areaLeft, areaAll, areaRight;
    private TextView areaLeftText, areaAllText, areaRightText;
    private GamingSwitch switchActive;
    private GamingSeekBar seekStrength, seekResponsiveness;
    private OctagonCheckBox checkTactix;
    private ImageView imgResetX, imgResetY;

    @Override public void onCreate() {
        super.onCreate();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        prefs = getSharedPreferences(PREF, MODE_PRIVATE);
        triggerSizeDp = prefs.getInt("size", 100);
        triggerOpacity = prefs.getFloat("opacity", .90f);
        startForegroundNotif();
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return; }
        inflateOverlay();
        // Trigger starts OFF. It is created only after the user enables the switch.
        triggerEnabled = false;
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
        removeTrigger();
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
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CH_ID) : new Notification.Builder(this);
        startForeground(42, b.setContentTitle("Magic Manager").setContentText("Touch control panel")
                .setSmallIcon(android.R.drawable.ic_menu_manage).setContentIntent(pi).setOngoing(true).build());
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
        int type = Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
        panelLp = new WindowManager.LayoutParams(dp(300),
                dp(330), type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED, PixelFormat.TRANSLUCENT);
        panelLp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        panelLp.x = 0; panelLp.y = dp(36);
        try { wm.addView(root, panelLp); } catch (Throwable t) { Toast.makeText(this, "Overlay failed: " + t.getMessage(), Toast.LENGTH_LONG).show(); stopSelf(); }
    }

    private void addTriggerWindow() {
        if (triggerView != null || !triggerEnabled) return;
        triggerView = new TriggerView(this);
        triggerView.setListener(this);
        triggerView.setOpacity(triggerOpacity);
        int type = Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
        triggerLp = new WindowManager.LayoutParams(dp(triggerSizeDp), dp(triggerSizeDp), type, flags, PixelFormat.TRANSLUCENT);
        triggerLp.gravity = Gravity.TOP | Gravity.START;
        DisplayMetrics dm = getResources().getDisplayMetrics();
        triggerLp.x = clamp(prefs.getInt("x", dm.widthPixels / 2 - dp(triggerSizeDp) / 2), 0, Math.max(0, dm.widthPixels - dp(triggerSizeDp)));
        triggerLp.y = clamp(prefs.getInt("y", dm.heightPixels / 2 - dp(triggerSizeDp) / 2), 0, Math.max(0, dm.heightPixels - dp(triggerSizeDp)));
        try { wm.addView(triggerView, triggerLp); } catch (Throwable t) { Log.e(TAG, "trigger add failed", t); }
    }

    private void showMainPage() {
        macroPageOpen = false;
        if (pageHost == null) return;
        pageHost.removeAllViews();
        View v = LayoutInflater.from(this).inflate(R.layout.page_main, pageHost, false);
        pageHost.addView(v);
        statusTv = v.findViewById(R.id.status_text);
        v.findViewById(R.id.btn_sensitivity_manager).setOnClickListener(x -> showSensitivityPage());
        v.findViewById(R.id.btn_macro_manager).setOnClickListener(x -> showMacroPage());
        updateTriggerMoveState();
    }

    private void showSensitivityPage() {
        macroPageOpen = false;
        pageHost.removeAllViews();
        View v = LayoutInflater.from(this).inflate(R.layout.page_sensitivity, pageHost, false);
        pageHost.addView(v);
        bindSensitivity(v);
        v.findViewById(R.id.btn_back_sens).setOnClickListener(x -> showMainPage());
        updateTriggerMoveState();
    }

    private void bindSensitivity(View v) {
        valX = v.findViewById(R.id.val_sens_x); valY = v.findViewById(R.id.val_sens_y);
        valStrength = v.findViewById(R.id.val_strength); valResponsiveness = v.findViewById(R.id.val_responsiveness);
        switchActive = v.findViewById(R.id.switch_active); checkTactix = v.findViewById(R.id.check_tactix);
        imgResetX = v.findViewById(R.id.img_reset_x); imgResetY = v.findViewById(R.id.img_reset_y);
        seekStrength = v.findViewById(R.id.seek_strength); seekResponsiveness = v.findViewById(R.id.seek_responsiveness);
        areaLeft = v.findViewById(R.id.area_left); areaAll = v.findViewById(R.id.area_all); areaRight = v.findViewById(R.id.area_right);
        areaLeftText = v.findViewById(R.id.area_left_text); areaAllText = v.findViewById(R.id.area_all_text); areaRightText = v.findViewById(R.id.area_right_text);
        GamingSeekBar sx = v.findViewById(R.id.seek_sens_x), sy = v.findViewById(R.id.seek_sens_y);
        configureSensitivity(sx, true); configureSensitivity(sy, false);
        imgResetX.setOnClickListener(x -> resetX(sx)); imgResetY.setOnClickListener(x -> resetY(sy));
        checkTactix.setChecked(tactix);
        checkTactix.setOnCheckedChangeListener((b, checked) -> { tactix = checked; restartIfRunning(); });
        seekStrength.setMin(0); seekStrength.setMax(100); seekStrength.setProgress(Math.round(strength));
        seekStrength.setOnProgressChangeListener((b,p,user) -> { strength=p; setPercent(valStrength,strength); if(user) restartIfRunning(); });
        seekResponsiveness.setMin(0); seekResponsiveness.setMax(100); seekResponsiveness.setProgress(Math.round(responsiveness));
        seekResponsiveness.setOnProgressChangeListener((b,p,user) -> { responsiveness=p; setPercent(valResponsiveness,responsiveness); if(user) restartIfRunning(); });
        setPercent(valStrength,strength); setPercent(valResponsiveness,responsiveness);
        View.OnClickListener areaListener = x -> { sensArea = x == areaLeft ? 0 : x == areaAll ? 1 : 2; refreshAreaUi(); restartIfRunning(); };
        areaLeft.setOnClickListener(areaListener); areaAll.setOnClickListener(areaListener); areaRight.setOnClickListener(areaListener);
        switchActive.setChecked(workerRunning, false);
        switchActive.setOnCheckedChangeListener((sw, checked) -> { if(checked) doStart(); else doStop(); });
        refreshAreaUi();
    }

    private void showMacroPage() {
        macroPageOpen = true;
        pageHost.removeAllViews();
        View v = LayoutInflater.from(this).inflate(R.layout.page_macro, pageHost, false);
        pageHost.addView(v);

        seekMacroInterval = v.findViewById(R.id.seek_macro_interval);
        seekMacroDuration = v.findViewById(R.id.seek_macro_duration);
        valMacroInterval = v.findViewById(R.id.val_macro_interval);
        valMacroDuration = v.findViewById(R.id.val_macro_duration);
        triggerPos = v.findViewById(R.id.trigger_pos);

        GamingSwitch switchTrigger = v.findViewById(R.id.switch_trigger);
        switchTrigger.setChecked(triggerEnabled, false);
        switchTrigger.setOnCheckedChangeListener((sw, checked) -> {
            triggerEnabled = checked;
            if (checked) {
                addTriggerWindow();
            } else {
                stopMacro();
                removeTrigger();
            }
            updateTriggerPositionText();
        });

        v.findViewById(R.id.btn_back_macro).setOnClickListener(x -> showMainPage());
        setupMacroSeekBars(v);
        updateTriggerPositionText();
    }

    private void setupMacroSeekBars(View v) {
        seekMacroInterval.setMin(20);
        seekMacroInterval.setMax(500);
        seekMacroInterval.setStep(10);
        seekMacroInterval.setProgress(macroIntervalMs);
        seekMacroInterval.setButtonSize(22);
        seekMacroInterval.setOnProgressChangeListener((b,p,user) -> {
            macroIntervalMs = clamp(p,20,500);
            valMacroInterval.setText(macroIntervalMs + " ms");
        });

        seekMacroDuration.setMin(0);
        seekMacroDuration.setMax(500);
        seekMacroDuration.setStep(10);
        seekMacroDuration.setProgress(macroDurationMs);
        seekMacroDuration.setButtonSize(22);
        seekMacroDuration.setOnProgressChangeListener((b,p,user) -> {
            macroDurationMs = clamp(p,0,500);
            valMacroDuration.setText(macroDurationMs + " ms");
        });

        GamingSeekBar size = v.findViewById(R.id.seek_trigger_size);
        TextView valSize = v.findViewById(R.id.val_trigger_size);
        size.setMin(48); size.setMax(200); size.setStep(4);
        size.setProgress(triggerSizeDp); size.setButtonSize(22);
        size.setOnProgressChangeListener((b,p,user) -> {
            triggerSizeDp = clamp(p,48,200);
            valSize.setText(triggerSizeDp + "dp");
            updateTriggerSize();
        });

        GamingSeekBar opacity = v.findViewById(R.id.seek_trigger_opacity);
        TextView valOpacity = v.findViewById(R.id.val_trigger_opacity);
        opacity.setMin(20); opacity.setMax(100); opacity.setStep(5);
        opacity.setProgress(Math.round(triggerOpacity * 100f)); opacity.setButtonSize(22);
        opacity.setOnProgressChangeListener((b,p,user) -> {
            triggerOpacity = clamp(p,20,100) / 100f;
            valOpacity.setText(Math.round(triggerOpacity * 100f) + "%");
            if (triggerView != null) triggerView.setOpacity(triggerOpacity);
            prefs.edit().putFloat("opacity", triggerOpacity).apply();
        });

        valMacroInterval.setText(macroIntervalMs + " ms");
        valMacroDuration.setText(macroDurationMs + " ms");
        valSize.setText(triggerSizeDp + "dp");
        valOpacity.setText(Math.round(triggerOpacity * 100f) + "%");
    }

    @Override public void onHoldStart() {
        if (triggerEnabled) startMacroAtTrigger();
    }

    @Override public void onHoldStop() {
        if (triggerEnabled) stopMacro();
    }

    @Override public void onTap() {
        if (!triggerEnabled) return;
        if (macroRunning) stopMacro();
        else startMacroAtTrigger();
    }

    @Override public boolean isMoveEnabled() { return macroPageOpen; }

    @Override public void onMove(float dx, float dy) {
        if (!triggerEnabled || !macroPageOpen || triggerLp == null) return;
        triggerLp.x += Math.round(dx); triggerLp.y += Math.round(dy);
        DisplayMetrics dm = getResources().getDisplayMetrics(); int s = dp(triggerSizeDp);
        triggerLp.x = clamp(triggerLp.x, 0, Math.max(0, dm.widthPixels-s)); triggerLp.y = clamp(triggerLp.y, 0, Math.max(0, dm.heightPixels-s));
        try { wm.updateViewLayout(triggerView, triggerLp); } catch (Throwable ignored) {}
        prefs.edit().putInt("x",triggerLp.x).putInt("y",triggerLp.y).apply(); updateTriggerPositionText();
    }

    private void startMacroAtTrigger() {
        if (macroRunning || triggerLp == null) return;
        if (!ShizukuHelper.isReady()) { ShizukuHelper.init(); setStatus("Waiting for Shizuku…"); return; }
        int s = dp(triggerSizeDp); int x = triggerLp.x + s/2, y = triggerLp.y + s/2;
        boolean ok = ShizukuHelper.startMacro(x, y, macroIntervalMs, macroDurationMs);
        macroRunning = ok;
        setStatus(ok ? "Macro running • " + macroIntervalMs + " ms" : "Macro start failed — check Shizuku");
    }

    private void stopMacro() {
        if (!macroRunning) return;
        try { ShizukuHelper.stopMacro(); } catch(Throwable t) { Log.w(TAG,"stopMacro",t); }
        macroRunning=false;
        if(!workerRunning) setStatus("Ready");
    }

    private void configureSensitivity(GamingSeekBar bar, boolean horizontal) {
        bar.setMin(0); bar.setMax(400); bar.setStep(1); bar.setProgress(Math.round(((horizontal?sensX:sensY)-1f)*100f));
        bar.setOnProgressChangeListener((b,p,user)->{ float v=1f+clamp(p,0,400)/100f; if(horizontal){sensX=v;setSensitivityText(valX,v);}else{sensY=v;setSensitivityText(valY,v);} if(user)restartIfRunning();});
        setSensitivityText(horizontal?valX:valY,horizontal?sensX:sensY);
    }
    private void resetX(GamingSeekBar b){sensX=2f;b.setProgress(100);setSensitivityText(valX,sensX);restartIfRunning();}
    private void resetY(GamingSeekBar b){sensY=2f;b.setProgress(100);setSensitivityText(valY,sensY);restartIfRunning();}
    private void setSensitivityText(TextView t,float v){if(t!=null)t.setText(String.format(Locale.US,"%.2f×",v));}
    private void setPercent(TextView t,float v){if(t!=null)t.setText(String.format(Locale.US,"%.0f%%",v));}

    private void doStart(){ if(switchActive!=null&&!switchActive.isChecked())return; setStatus("Starting…"); main.post(()->{try{if(!ShizukuHelper.isReady()){ShizukuHelper.init();main.postDelayed(()->{if(switchActive!=null&&switchActive.isChecked()){if(ShizukuHelper.isReady())doStart();else{switchActive.setChecked(false,true);setStatus("Shizuku not connected");}}},500);return;}String bin=getApplicationInfo().nativeLibraryDir+"/libancoreengine.so";boolean ok=ShizukuHelper.startWorker(bin,0,sensY,sensX,sensArea,tactix?1:0,strength,responsiveness);workerRunning=ok;if(ok)setStatus("Engaged");else{switchActive.setChecked(false,true);setStatus("Start failed — check Shizuku / input permission");}}catch(Throwable t){workerRunning=false;if(switchActive!=null)switchActive.setChecked(false,true);setStatus("Error: "+t.getMessage());}}); }
    private void doStop(){stopMacro();try{ShizukuHelper.stopWorker();}catch(Throwable ignored){}workerRunning=false;if(switchActive!=null&&switchActive.isChecked())switchActive.setChecked(false,true);setStatus("Ready");}
    private void restartIfRunning(){if(workerRunning)doStart();}

    private void refreshAreaUi(){if(areaLeft==null)return;LinearLayout[] vs={areaLeft,areaAll,areaRight};TextView[] ls={areaLeftText,areaAllText,areaRightText};for(int i=0;i<3;i++){if(vs[i] instanceof HexagonLinearLayout){HexagonLinearLayout h=(HexagonLinearLayout)vs[i];h.setBackgroundColor(i==sensArea?0xFFE51010:0xFF262323);h.setStrokeColor(i==sensArea?0xFFE51010:0xFF363232);h.setStrokeWidth(1);h.setCornerCut(9);}ls[i].setTextColor(i==sensArea?0xFFFFFFFF:0xFFB0B3BA);}}
    private void setStatus(String s){if(statusTv!=null)statusTv.setText(s);}

    private void updateTriggerMoveState(){ if(triggerView!=null) triggerView.setOpacity(triggerOpacity); }
    private void updateTriggerSize(){ if(triggerView==null||triggerLp==null)return;int old=triggerLp.width;int n=dp(triggerSizeDp);int cx=triggerLp.x+old/2,cy=triggerLp.y+old/2;triggerLp.width=n;triggerLp.height=n;triggerLp.x=Math.max(0,cx-n/2);triggerLp.y=Math.max(0,cy-n/2);triggerView.setDiameter(triggerSizeDp);try{wm.updateViewLayout(triggerView,triggerLp);}catch(Throwable ignored){}prefs.edit().putInt("size",triggerSizeDp).putInt("x",triggerLp.x).putInt("y",triggerLp.y).apply();}
    private void updateTriggerPositionText(){if(triggerPos!=null) triggerPos.setText(triggerLp==null ? "OFF" : triggerLp.x+", "+triggerLp.y);}

    private void hidePanel(){
        macroPageOpen = false;
        stopMacro();
        if(panel!=null) panel.setVisibility(View.GONE);
    }

    private void showPanel(){
        if(panel!=null) panel.setVisibility(View.VISIBLE);
        showMainPage();
    }



    private void removeOverlay(){
        if(root!=null&&wm!=null){try{wm.removeView(root);}catch(Throwable ignored){}root=null;}
    }
    private void removeTrigger(){if(triggerView!=null&&wm!=null){try{wm.removeView(triggerView);}catch(Throwable ignored){}triggerView=null;}}
    private void stopWorkerAndSelf(){doStop();removeTrigger();removeOverlay();stopForeground(true);stopSelf();}

    private static int clamp(int v,int min,int max){return Math.max(min,Math.min(max,v));}
    private static float clamp(float v,float min,float max){return Math.max(min,Math.min(max,v));}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}

    public static void launch(Context ctx){Intent i=new Intent(ctx,OverlayService.class).setAction(ACTION_SHOW);if(Build.VERSION.SDK_INT>=26)ctx.startForegroundService(i);else ctx.startService(i);}
}
