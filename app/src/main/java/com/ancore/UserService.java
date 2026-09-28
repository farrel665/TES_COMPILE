package com.ancore;

import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.system.Os;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

/**
 * Runs inside the Shizuku process (ADB shell uid).
 * Has permission to open /dev/input/*, /dev/uinput and to write /data/local/tmp.
 */
public class UserService extends Binder implements IUserService {
    private static final String TAG = "ancore_usersvc";
    private static final String TMP_BIN = "/data/local/tmp/ancore_engine";
    private static final String TMP_LOG = "/data/local/tmp/ancore_engine.log";

    public UserService() {
        attachInterface(this, DESCRIPTOR);
    }

    @Override
    public IBinder asBinder() {
        return this;
    }

    @Override
    public boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
        data.enforceInterface(DESCRIPTOR);
        switch (code) {
            case 1: { // startWorker(path, preset, sensX, sensY, area, tactix)
                String path = data.readString();
                int preset = data.readInt();
                float sensX = data.readFloat();
                float sensY = data.readFloat();
                int area = data.readInt();
                int tactix = data.readInt();
                boolean ok = startWorker(path, preset, sensX, sensY, area, tactix);
                reply.writeNoException();
                reply.writeInt(ok ? 1 : 0);
                return true;
            }
            case 2: { // stopWorker
                stopWorker();
                reply.writeNoException();
                return true;
            }
            case 3: { // ping
                String r = ping();
                reply.writeNoException();
                reply.writeString(r);
                return true;
            }
        }
        return super.onTransact(code, data, reply, flags);
    }

    @Override
    public boolean startWorker(String srcPath, int presetIdx, float sensX, float sensY,
                               int area, int tactix) {
        Log.i(TAG, "startWorker src=" + srcPath + " preset=" + presetIdx
                + " sensX=" + sensX + " sensY=" + sensY
                + " area=" + area + " tactix=" + tactix);
        try {
            // Kill any previous instance
            killByName("ancore_engine");

            // 2. Stage binary — always refresh from APK so new flags take effect
            File src = new File(srcPath);
            File staged = new File(TMP_BIN);
            if (src.exists() && src.canRead()) {
                copyFile(src, staged);
                Log.i(TAG, "staged from " + srcPath);
            } else if (!staged.exists()) {
                Log.e(TAG, "source missing and no staged binary: " + srcPath);
                return false;
            } else {
                Log.w(TAG, "using existing staged binary (src missing)");
            }

            // chmod 755
            try {
                Os.chmod(TMP_BIN, 0755);
            } catch (Throwable t) {
                // fallback shell
                execShell("chmod 755 " + TMP_BIN);
            }

            // Launch detached
            // setsid + redirect so the process survives the service binder
            String cmd = String.format(
                    java.util.Locale.US,
                    "setsid %s --preset %d --sens-x %.3f --sens-y %.3f --area %d --tactix %d --deadzone 1.5 --flick-speed 18 --flick-boost 1.55 >> %s 2>&1 < /dev/null &",
                    TMP_BIN, presetIdx, sensX, sensY, area, tactix, TMP_LOG);
            int rc = execShell(cmd);
            Log.i(TAG, "execShell rc=" + rc);

            boolean alive = isProcessAlive("ancore_engine");
            Log.i(TAG, "worker alive=" + alive);
            return alive || rc == 0;
        } catch (Throwable t) {
            Log.e(TAG, "startWorker error", t);
            return false;
        }
    }

    @Override
    public void stopWorker() {
        Log.i(TAG, "stopWorker");
        killByName("ancore_engine");
    }

    @Override
    public String ping() {
        return "pong uid=" + android.os.Process.myUid();
    }

    // ---------- helpers ----------

    private static void copyFile(File src, File dst) throws Exception {
        try (FileInputStream in = new FileInputStream(src);
             FileOutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
        Log.i(TAG, "copied " + src + " -> " + dst + " (" + dst.length() + " bytes)");
    }

    private static int execShell(String cmd) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c", cmd});
            boolean finished = p.waitFor(8, TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                return -1;
            }
            return p.exitValue();
        } catch (Exception e) {
            Log.e(TAG, "execShell failed: " + cmd, e);
            return -2;
        }
    }

    private static void killByName(String name) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c",
                    "ps -A -o PID,NAME,ARGS 2>/dev/null | grep " + name + " | grep -v grep"});
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] parts = line.split("\\s+");
                if (parts.length > 0) {
                    try {
                        int pid = Integer.parseInt(parts[0]);
                        Log.i(TAG, "killing pid=" + pid);
                        android.os.Process.killProcess(pid);
                        // also try kill -9 via shell in case of permission
                        execShell("kill -9 " + pid);
                    } catch (NumberFormatException ignored) {}
                }
            }
            p.waitFor(2, TimeUnit.SECONDS);
        } catch (Exception e) {
            Log.w(TAG, "killByName", e);
        }
    }

    private static boolean isProcessAlive(String name) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c",
                    "ps -A 2>/dev/null | grep " + name + " | grep -v grep"});
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = br.readLine();
            p.waitFor(2, TimeUnit.SECONDS);
            return line != null && !line.trim().isEmpty();
        } catch (Exception e) {
            return false;
        }
    }
}
