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
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** Runs inside the Shizuku UserService process. */
public class UserService extends Binder implements IUserService {
    private static final String TAG = "ancore_usersvc";
    private static final String TMP_BIN = "/data/local/tmp/ancore_engine";
    private static final String TMP_LOG = "/data/local/tmp/ancore_engine.log";
    private volatile Process macroProcess;

    public UserService() { attachInterface(this, DESCRIPTOR); }
    @Override public IBinder asBinder() { return this; }

    @Override
    public boolean onTransact(int code, Parcel data, Parcel reply, int flags)
            throws RemoteException {
        data.enforceInterface(DESCRIPTOR);
        switch (code) {
            case 1: {
                String path = data.readString();
                int preset = data.readInt();
                float sensX = data.readFloat();
                float sensY = data.readFloat();
                int area = data.readInt();
                int tactix = data.readInt();
                float strength = data.readFloat();
                float responsiveness = data.readFloat();
                boolean ok = startWorker(path, preset, sensX, sensY, area, tactix,
                        strength, responsiveness);
                reply.writeNoException();
                reply.writeInt(ok ? 1 : 0);
                return true;
            }
            case 2:
                stopWorker();
                reply.writeNoException();
                return true;
            case 3:
                reply.writeNoException();
                reply.writeString(ping());
                return true;
            case 4: {
                int x = data.readInt();
                int y = data.readInt();
                int intervalMs = data.readInt();
                boolean ok = startMacro(x, y, intervalMs);
                reply.writeNoException();
                reply.writeInt(ok ? 1 : 0);
                return true;
            }
            case 5:
                stopMacro();
                reply.writeNoException();
                return true;
            default:
                return super.onTransact(code, data, reply, flags);
        }
    }

    @Override
    public boolean startWorker(String srcPath, int presetIdx, float sensX, float sensY,
                               int area, int tactix, float strength,
                               float responsiveness) {
        Log.i(TAG, "startWorker src=" + srcPath + " preset=" + presetIdx
                + " sensX=" + sensX + " sensY=" + sensY
                + " area=" + area + " tactix=" + tactix
                + " strength=" + strength + " responsiveness=" + responsiveness);
        try {
            killByName("ancore_engine");

            File src = new File(srcPath);
            File staged = new File(TMP_BIN);
            if (src.exists() && src.canRead()) {
                copyFile(src, staged);
            } else if (!staged.exists()) {
                Log.e(TAG, "source missing and no staged binary: " + srcPath);
                return false;
            }

            try { Os.chmod(TMP_BIN, 0755); }
            catch (Throwable t) { execShell("chmod 755 " + shellQuote(TMP_BIN)); }

            // Strength/responsiveness are passed as normalized 0..100 values.
            String cmd = String.format(Locale.US,
                    "setsid %s --preset %d --sens-x %.3f --sens-y %.3f "
                    + "--area %d --tactix %d --strength %.3f --responsiveness %.3f "
                    + "--deadzone 1.5 --flick-speed 16 --flick-boost 1.30 "
                    + ">> %s 2>&1 < /dev/null &",
                    shellQuote(TMP_BIN), presetIdx, sensX, sensY, area, tactix,
                    strength, responsiveness, shellQuote(TMP_LOG));

            int rc = execShell(cmd);
            boolean alive = isProcessAlive("ancore_engine");
            Log.i(TAG, "execShell rc=" + rc + " worker alive=" + alive);
            return alive || rc == 0;
        } catch (Throwable t) {
            Log.e(TAG, "startWorker error", t);
            return false;
        }
    }

    @Override public void stopWorker() {
        Log.i(TAG, "stopWorker");
        killByName("ancore_engine");
    }

    @Override
    public synchronized boolean startMacro(int x, int y, int intervalMs) {
        stopMacro();
        x = Math.max(0, x);
        y = Math.max(0, y);
        intervalMs = Math.max(20, Math.min(intervalMs, 5000));
        String cmd = String.format(Locale.US,
                "while true; do input tap %d %d; sleep %.3f; done",
                x, y, intervalMs / 1000.0);
        try {
            macroProcess = Runtime.getRuntime().exec(new String[]{"sh", "-c", cmd});
            Log.i(TAG, "macro started x=" + x + " y=" + y + " interval=" + intervalMs);
            return true;
        } catch (Throwable t) {
            macroProcess = null;
            Log.e(TAG, "startMacro", t);
            return false;
        }
    }

    @Override
    public synchronized void stopMacro() {
        Process p = macroProcess;
        macroProcess = null;
        if (p != null) {
            try { p.destroy(); } catch (Throwable ignored) {}
            try { if (p.isAlive()) p.destroyForcibly(); } catch (Throwable ignored) {}
        }
    }

    @Override public String ping() {
        return "pong uid=" + android.os.Process.myUid();
    }

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
            if (!finished) { p.destroyForcibly(); return -1; }
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
                        android.os.Process.killProcess(pid);
                        execShell("kill -9 " + pid);
                    } catch (NumberFormatException ignored) { }
                }
            }
            p.waitFor(2, TimeUnit.SECONDS);
        } catch (Exception e) { Log.w(TAG, "killByName", e); }
    }

    private static boolean isProcessAlive(String name) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c",
                    "ps -A 2>/dev/null | grep " + name + " | grep -v grep"});
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = br.readLine();
            p.waitFor(2, TimeUnit.SECONDS);
            return line != null && !line.trim().isEmpty();
        } catch (Exception e) { return false; }
    }

    private static String shellQuote(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }
}
