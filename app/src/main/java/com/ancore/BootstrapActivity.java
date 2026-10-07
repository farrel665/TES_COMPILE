package com.ancore;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

/**
 * Normal Activity UI.
 * No floating window, no overlay permission and no bubble.
 */
public class BootstrapActivity extends Activity {
    private final Handler main = new Handler(Looper.getMainLooper());

    private GamingSwitch switchActive;
    private GamingSeekBar seekSensitivity;
    private TextView valueSensitivity;
    private TextView statusText;
    private TextView valueResponsiveness;
    private TextView valueSmoothness;

    private float sensitivity = 1.00f;
    private final float responsiveness = 100.0f;

    /*
     * Fixed native smoothing chosen to be light enough to avoid a heavy screen
     * while removing only tiny event-to-event jitter.
     */
    private final float smoothness = 3.0f;

    private boolean workerRunning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bindViews();
        bindShizuku();
        bindSensitivity();
        updateLabels();
    }

    private void bindViews() {
        switchActive = findViewById(R.id.switch_active);
        seekSensitivity = findViewById(R.id.seek_sensitivity);
        valueSensitivity = findViewById(R.id.val_sensitivity);
        statusText = findViewById(R.id.status_text);
        valueResponsiveness = findViewById(R.id.val_responsiveness);
        valueSmoothness = findViewById(R.id.val_smoothness);

        if (switchActive != null) {
            switchActive.setChecked(false, false);
            switchActive.setOnCheckedChangeListener((sw, checked) -> {
                if (checked) {
                    doStart();
                } else {
                    doStop();
                }
            });
        }
    }

    private void bindShizuku() {
        try {
            ShizukuHelper.init();
            setStatus("Ready");
            main.postDelayed(() -> {
                if (ShizukuHelper.isReady()) {
                    setStatus("Shizuku ready");
                } else {
                    setStatus("Waiting for Shizuku");
                }
            }, 600);
        } catch (Throwable t) {
            setStatus("Shizuku init failed");
            Toast.makeText(this, "Shizuku: " + t.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void bindSensitivity() {
        if (seekSensitivity == null) return;

        // 0..400 => 1.00x..5.00x
        seekSensitivity.setMin(0);
        seekSensitivity.setMax(400);
        seekSensitivity.setProgress(0);

        seekSensitivity.setOnProgressChangeListener((bar, progress, fromUser) -> {
            sensitivity = 1.0f + Math.max(0, Math.min(400, progress)) / 100.0f;
            updateLabels();
            if (fromUser && workerRunning) {
                restartWorker();
            }
        });
    }

    private void updateLabels() {
        if (valueSensitivity != null) {
            valueSensitivity.setText(String.format(Locale.US, "%.2f×", sensitivity));
        }
        if (valueResponsiveness != null) {
            valueResponsiveness.setText("100%");
        }
        if (valueSmoothness != null) {
            valueSmoothness.setText("Optimal");
        }
    }

    private void doStart() {
        if (workerRunning) return;

        setStatus("Starting…");

        main.post(() -> {
            try {
                if (!ShizukuHelper.isReady()) {
                    ShizukuHelper.init();
                    main.postDelayed(() -> {
                        if (switchActive != null && switchActive.isChecked()) {
                            if (ShizukuHelper.isReady()) {
                                doStart();
                            } else {
                                workerRunning = false;
                                switchActive.setChecked(false, true);
                                setStatus("Shizuku not connected");
                            }
                        }
                    }, 500);
                    return;
                }

                String bin = getApplicationInfo().nativeLibraryDir
                        + "/libancoreengine.so";

                /*
                 * One sensitivity value controls both axes. This removes the
                 * old X/Y inversion problem completely.
                 */
                boolean ok = ShizukuHelper.startWorker(
                        bin,
                        0,
                        sensitivity,
                        sensitivity,
                        1,      // global area
                        0,      // TactiX off; no extra deadzone
                        smoothness,
                        responsiveness
                );

                workerRunning = ok;

                if (ok) {
                    setStatus("Sensitivity active");
                } else {
                    switchActive.setChecked(false, true);
                    setStatus("Start failed — input access unavailable");
                }
            } catch (Throwable t) {
                workerRunning = false;
                switchActive.setChecked(false, true);
                setStatus("Error: " + t.getMessage());
            }
        });
    }

    private void restartWorker() {
        if (!workerRunning) return;
        try {
            ShizukuHelper.stopWorker();
        } catch (Throwable ignored) {
        }
        workerRunning = false;
        main.postDelayed(this::doStart, 100);
    }

    private void doStop() {
        try {
            ShizukuHelper.stopWorker();
        } catch (Throwable ignored) {
        }

        workerRunning = false;
        setStatus("Ready • normal touch");
    }

    private void setStatus(String text) {
        if (statusText != null) statusText.setText(text);
    }

    @Override
    protected void onDestroy() {
        doStop();
        super.onDestroy();
    }
}
