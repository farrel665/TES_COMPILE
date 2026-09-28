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
import java.io.FileReader;
import java.io.InputStreamReader;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Berjalan di dalam proses Shizuku (ADB shell UID 2000).
 *
 * Perubahan utama vs versi lama:
 *  - Tidak lagi mengandalkan binary untuk buka /dev/input/* dan /dev/uinput sendiri.
 *  - Shell Shizuku (UID 2000, grup input + uhid) yang buka fd-nya:
 *      exec 3<>/dev/input/eventX    <- O_RDWR, butuh grup input
 *      exec 4>/dev/uinput            <- O_WRONLY, butuh grup uhid
 *  - Lalu fd 3 & 4 dipass ke binary lewat --fd-capture 3 --fd-uinput 4.
 *  - Binary hanya pakai fd yang sudah terbuka → tidak butuh root sama sekali.
 */
public class UserService extends Binder implements IUserService {

    private static final String TAG     = "ancore_usersvc";
    private static final String TMP_BIN = "/data/local/tmp/ancore_engine";
    private static final String TMP_LOG = "/data/local/tmp/ancore_engine.log";

    public UserService() {
        attachInterface(this, DESCRIPTOR);
    }

    @Override public IBinder asBinder() { return this; }

    @Override
    public boolean onTransact(int code, Parcel data, Parcel reply, int flags)
            throws RemoteException {
        data.enforceInterface(DESCRIPTOR);
        switch (code) {
            case 1: {
                String path   = data.readString();
                int    preset = data.readInt();
                float  sensX  = data.readFloat();
                float  sensY  = data.readFloat();
                int    area   = data.readInt();
                int    tactix = data.readInt();
                boolean ok = startWorker(path, preset, sensX, sensY, area, tactix);
                reply.writeNoException();
                reply.writeInt(ok ? 1 : 0);
                return true;
            }
            case 2: {
                stopWorker();
                reply.writeNoException();
                return true;
            }
            case 3: {
                reply.writeNoException();
                reply.writeString(ping());
                return true;
            }
        }
        return super.onTransact(code, data, reply, flags);
    }

    // ─────────────────────────────────────────────────────────────────
    //  startWorker — inti perubahan ada di sini
    // ─────────────────────────────────────────────────────────────────

    @Override
    public boolean startWorker(String srcPath, int presetIdx,
                                float sensX, float sensY,
                                int area, int tactix) {
        Log.i(TAG, "startWorker src=" + srcPath
                + " preset=" + presetIdx
                + " sensX=" + sensX + " sensY=" + sensY
                + " area=" + area + " tactix=" + tactix);
        try {
            // 1. Kill instance lama
            killByName("ancore_engine");
            killByName("input_ancore");

            // 2. Stage binary dari APK ke /data/local/tmp/
            File src    = new File(srcPath);
            File staged = new File(TMP_BIN);
            if (src.exists() && src.canRead()) {
                copyFile(src, staged);
                Log.i(TAG, "staged: " + srcPath + " -> " + TMP_BIN);
            } else if (!staged.exists()) {
                Log.e(TAG, "binary tidak ditemukan: " + srcPath);
                return false;
            } else {
                Log.w(TAG, "pakai binary lama (src tidak ada)");
            }

            // 3. chmod 755
            try {
                Os.chmod(TMP_BIN, 0755);
            } catch (Throwable t) {
                execShell("chmod 755 " + TMP_BIN);
            }

            // 4. Cari touch input device
            String touchDev = findTouchDevice();
            Log.i(TAG, "touch device: " + touchDev);

            // 5. Bersihkan log lama
            execShell("rm -f " + TMP_LOG);

            // 6. Bangun perintah launch
            String cmd;
            if (touchDev != null) {
                /*
                 * SHIZUKU MODE (tanpa root):
                 *
                 * Shell (UID 2000, grup input + uhid) buka fd dulu:
                 *   exec 3<>DEVICE    → fd 3 = capture (O_RDWR)
                 *   4>/dev/uinput     → fd 4 = uinput  (O_WRONLY)
                 *
                 * Lalu binary dapat fd 3 & 4 via --fd-capture / --fd-uinput.
                 * Binary TIDAK perlu buka /dev/* sendiri → tidak perlu root.
                 *
                 * "&&" memastikan kalau exec gagal (izin ditolak),
                 * binary tidak dijalankan dan kita tahu ada masalah.
                 */
                cmd = String.format(Locale.US,
                        "exec 3<>%s 4>/dev/uinput && " +
                        "setsid %s " +
                        "--fd-capture 3 --fd-uinput 4 " +
                        "--preset %d " +
                        "--sens-x %.3f --sens-y %.3f " +
                        "--area %d --tactix %d " +
                        ">> %s 2>&1 &",
                        touchDev, TMP_BIN,
                        presetIdx,
                        sensX, sensY,
                        area, tactix,
                        TMP_LOG);
            } else {
                /*
                 * Fallback: biarkan binary coba buka sendiri.
                 * Ini akan gagal tanpa root, tapi setidaknya
                 * log menunjukkan error yang jelas.
                 */
                Log.w(TAG, "touch device tidak ditemukan, coba direct open (butuh root)");
                cmd = String.format(Locale.US,
                        "setsid %s " +
                        "--preset %d " +
                        "--sens-x %.3f --sens-y %.3f " +
                        "--area %d --tactix %d " +
                        ">> %s 2>&1 &",
                        TMP_BIN,
                        presetIdx,
                        sensX, sensY,
                        area, tactix,
                        TMP_LOG);
            }

            Log.i(TAG, "cmd: " + cmd);
            int rc = execShell(cmd);
            Log.i(TAG, "shell rc=" + rc);

            // 7. Tunggu binary init (buka device + setup uinput)
            try { Thread.sleep(1000); } catch (InterruptedException ignored) {}

            // 8. Cek apakah binary benar-benar hidup
            boolean alive = isProcessAlive("ancore_engine")
                         || isProcessAlive("input_ancore");

            // Kalau belum kelihatan, tunggu sedikit lagi
            if (!alive) {
                try { Thread.sleep(500); } catch (InterruptedException ignored) {}
                alive = isProcessAlive("ancore_engine")
                     || isProcessAlive("input_ancore");
            }

            Log.i(TAG, "binary alive=" + alive);

            // 9. Dump log engine untuk debug
            dumpEngineLog();

            // PENTING: return alive saja, TIDAK rc==0
            // (rc selalu 0 karena shell background &, meski binary langsung mati)
            return alive;

        } catch (Throwable t) {
            Log.e(TAG, "startWorker error", t);
            return false;
        }
    }

    @Override
    public void stopWorker() {
        Log.i(TAG, "stopWorker");
        killByName("ancore_engine");
        killByName("input_ancore");
    }

    @Override
    public String ping() {
        return "pong uid=" + android.os.Process.myUid();
    }

    // ─────────────────────────────────────────────────────────────────
    //  findTouchDevice — cari /dev/input/eventX yang MT touch
    // ─────────────────────────────────────────────────────────────────

    /**
     * Cari multitouch input device. Berjalan sebagai ADB shell (UID 2000)
     * sehingga punya akses baca ke /dev/input/* (grup input).
     *
     * @return path seperti "/dev/input/event2", atau null kalau tidak ketemu
     */
    private static String findTouchDevice() {

        // Metode 1: getevent -p — paling reliable, nama capability eksplisit
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c",
                "for i in $(seq 0 31); do" +
                "  D=/dev/input/event$i;" +
                "  [ -c \"$D\" ] || continue;" +
                "  getevent -p \"$D\" 2>/dev/null | grep -qF 'ABS_MT_POSITION_X'" +
                "    && { echo \"$D\"; break; };" +
                "done"});
            BufferedReader br = new BufferedReader(
                    new InputStreamReader(p.getInputStream()));
            String line = br.readLine();
            p.waitFor(6, TimeUnit.SECONDS);
            if (line != null) {
                String dev = line.trim();
                if (dev.matches("/dev/input/event[0-9]+")) {
                    Log.i(TAG, "findTouchDevice [getevent]: " + dev);
                    return dev;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "findTouchDevice getevent failed", e);
        }

        // Metode 2: /proc/bus/input/devices — parse hex ABS bitmask
        // ABS_MT_POSITION_X = bit 53 (0x35). Di bitmask hex, bit 53
        // ada di word kedua dari kanan. Cara mudah: kalau ABS= punya
        // lebih dari 1 hex word, kemungkinan besar MT device.
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c",
                "awk '" +
                "  /^H: Handlers=/{h=$0}" +
                "  /^B: ABS=/{" +
                "    nf=split($0,a,\" \");" +
                "    if(nf>=3){" +   // minimal ada 2 hex field = kemungkinan MT
                "      match(h,/event[0-9]+/);" +
                "      if(RSTART) print \"/dev/input/\" substr(h,RSTART,RLENGTH)" +
                "    }" +
                "  }' /proc/bus/input/devices 2>/dev/null | head -1"});
            BufferedReader br = new BufferedReader(
                    new InputStreamReader(p.getInputStream()));
            String line = br.readLine();
            p.waitFor(3, TimeUnit.SECONDS);
            if (line != null) {
                String dev = line.trim();
                if (dev.matches("/dev/input/event[0-9]+")) {
                    Log.i(TAG, "findTouchDevice [awk]: " + dev);
                    return dev;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "findTouchDevice awk failed", e);
        }

        // Metode 3: coba buka satu-satu, lihat mana yang bisa exec 3<>
        // (membuktikan shell punya akses sekaligus cari device)
        for (int i = 0; i <= 9; i++) {
            String dev = "/dev/input/event" + i;
            try {
                Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c",
                    "exec 3<>" + dev + " && echo ok"});
                BufferedReader br = new BufferedReader(
                        new InputStreamReader(p.getInputStream()));
                String result = br.readLine();
                p.waitFor(2, TimeUnit.SECONDS);
                if ("ok".equals(result != null ? result.trim() : "")) {
                    // Bisa dibuka, tapi cek dulu apakah MT
                    Process p2 = Runtime.getRuntime().exec(new String[]{"sh", "-c",
                        "getevent -p " + dev + " 2>/dev/null | grep -qF 'ABS_MT' && echo mt"});
                    BufferedReader br2 = new BufferedReader(
                            new InputStreamReader(p2.getInputStream()));
                    String isMt = br2.readLine();
                    p2.waitFor(2, TimeUnit.SECONDS);
                    if ("mt".equals(isMt != null ? isMt.trim() : "")) {
                        Log.i(TAG, "findTouchDevice [probe]: " + dev);
                        return dev;
                    }
                }
            } catch (Exception ignored) {}
        }

        Log.e(TAG, "findTouchDevice: tidak ditemukan!");
        return null;
    }

    // ─────────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────────

    private static void copyFile(File src, File dst) throws Exception {
        try (FileInputStream in  = new FileInputStream(src);
             FileOutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
        Log.i(TAG, "copy " + src + " -> " + dst + " (" + dst.length() + " bytes)");
    }

    private static int execShell(String cmd) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c", cmd});
            boolean done = p.waitFor(8, TimeUnit.SECONDS);
            if (!done) { p.destroyForcibly(); return -1; }
            return p.exitValue();
        } catch (Exception e) {
            Log.e(TAG, "execShell: " + cmd, e);
            return -2;
        }
    }

    private static void killByName(String name) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c",
                "ps -A -o PID,ARGS 2>/dev/null | grep " + name + " | grep -v grep"});
            BufferedReader br = new BufferedReader(
                    new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] parts = line.split("\\s+");
                if (parts.length > 0) {
                    try {
                        int pid = Integer.parseInt(parts[0]);
                        Log.i(TAG, "kill pid=" + pid + " (" + name + ")");
                        android.os.Process.killProcess(pid);
                        execShell("kill -9 " + pid);
                    } catch (NumberFormatException ignored) {}
                }
            }
            p.waitFor(2, TimeUnit.SECONDS);
        } catch (Exception e) {
            Log.w(TAG, "killByName " + name, e);
        }
    }

    private static boolean isProcessAlive(String name) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c",
                "ps -A 2>/dev/null | grep " + name + " | grep -v grep"});
            BufferedReader br = new BufferedReader(
                    new InputStreamReader(p.getInputStream()));
            String line = br.readLine();
            p.waitFor(2, TimeUnit.SECONDS);
            return line != null && !line.trim().isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    /** Dump log binary ke Logcat untuk debug */
    private static void dumpEngineLog() {
        try {
            File logFile = new File(TMP_LOG);
            if (!logFile.exists() || logFile.length() == 0) return;
            BufferedReader br = new BufferedReader(new FileReader(logFile));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
            br.close();
            if (sb.length() > 0) {
                Log.i(TAG, "=== engine log ===\n" + sb + "=== end ===");
            }
        } catch (Throwable ignored) {}
    }
}