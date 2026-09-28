package com.ancore;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

public class BootstrapActivity extends Activity {
    private static final String TAG = "ancore_boot";
    private static final int REQ_OVERLAY = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.i(TAG, "Bootstrap starting — init Shizuku");
        try {
            ShizukuHelper.init();
        } catch (Throwable t) {
            Log.e(TAG, "ShizukuHelper.init failed", t);
            Toast.makeText(this, "Shizuku init error: " + t.getMessage(), Toast.LENGTH_LONG).show();
        }

        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Allow display over other apps", Toast.LENGTH_LONG).show();
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(i, REQ_OVERLAY);
            return;
        }

        launchOverlayAndFinish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_OVERLAY) {
            if (Settings.canDrawOverlays(this)) {
                launchOverlayAndFinish();
            } else {
                Toast.makeText(this, "Overlay permission required", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    private void launchOverlayAndFinish() {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                OverlayService.launch(this);
                Toast.makeText(this, "Floating panel started", Toast.LENGTH_SHORT).show();
            } catch (Throwable t) {
                Log.e(TAG, "OverlayService launch failed", t);
                Toast.makeText(this, "Launch failed: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
            finish();
        }, 400);
    }
}
