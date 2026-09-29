// app/src/main/java/com/ancore/ShizukuHelper.java
package com.ancore;

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import rikka.shizuku.Shizuku;

public final class ShizukuHelper {
    private static final String TAG = "ancore_shizuku";

    private static IUserService sService;
    private static boolean sBound;
    private static final Object LOCK = new Object();

    // ── Callback setelah bound ────────────────────────────────────────
    public interface OnReadyCallback {
        void onReady(boolean success);
    }

    private static final List<OnReadyCallback> sPendingCallbacks = new ArrayList<>();

    private static void dispatchReady(boolean success) {
        synchronized (LOCK) {
            for (OnReadyCallback cb : sPendingCallbacks) {
                try { cb.onReady(success); } catch (Throwable ignored) {}
            }
            sPendingCallbacks.clear();
        }
    }

    // ── ServiceConnection ─────────────────────────────────────────────
    private static final ServiceConnection CONN = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.i(TAG, "UserService connected");
            synchronized (LOCK) {
                sService = new UserServiceProxy(service);
                sBound   = true;
            }
            try {
                String pong = sService.ping();
                Log.i(TAG, "ping -> " + pong);
            } catch (Throwable t) {
                Log.w(TAG, "ping failed", t);
            }
            dispatchReady(true);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.w(TAG, "UserService disconnected");
            synchronized (LOCK) {
                sService = null;
                sBound   = false;
            }
        }
    };

    private static final Shizuku.UserServiceArgs ARGS =
            new Shizuku.UserServiceArgs(
                    new ComponentName("com.ancore", UserService.class.getName()))
                    .daemon(false)
                    .processNameSuffix("ancore_svc")
                    .debuggable(false)
                    .version(1);

    // ── init ──────────────────────────────────────────────────────────
    /**
     * Inisialisasi Shizuku. Kalau sudah bound, langsung panggil callback.
     * Kalau belum, binding dulu lalu callback dipanggil setelah selesai.
     */
    public static void init(OnReadyCallback callback) {
        synchronized (LOCK) {
            if (callback != null && sBound && sService != null) {
                // Sudah siap, langsung callback
                callback.onReady(true);
                return;
            }
            if (callback != null) {
                sPendingCallbacks.add(callback);
            }
        }

        if (!Shizuku.pingBinder()) {
            Log.w(TAG, "Shizuku not running");
            dispatchReady(false);
            return;
        }

        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "requesting Shizuku permission");
            Shizuku.addRequestPermissionResultListener((requestCode, grantResult) -> {
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    bindService();
                } else {
                    Log.w(TAG, "Shizuku permission denied");
                    dispatchReady(false);
                }
            });
            Shizuku.requestPermission(0);
        } else {
            bindService();
        }
    }

    // Overload tanpa callback (dipanggil dari BootstrapActivity)
    public static void init() {
        init(null);
    }

    private static void bindService() {
        try {
            Shizuku.bindUserService(ARGS, CONN);
            Log.i(TAG, "bindUserService requested");
        } catch (Throwable t) {
            Log.e(TAG, "bindUserService failed", t);
            dispatchReady(false);
        }
    }

    // ── Worker control ────────────────────────────────────────────────
    public static boolean startWorker(String binaryPath, int presetIdx,
                                      float sensX, float sensY,
                                      int area, int tactix) {
        synchronized (LOCK) {
            if (sService == null) return false;
            try {
                return sService.startWorker(binaryPath, presetIdx, sensX, sensY, area, tactix);
            } catch (RemoteException e) {
                Log.e(TAG, "startWorker RemoteException", e);
                return false;
            }
        }
    }

    public static boolean startWorker(String binaryPath, int presetIdx,
                                      float sensX, float sensY) {
        return startWorker(binaryPath, presetIdx, sensX, sensY, 1, 0);
    }

    public static boolean startWorker(String binaryPath, int presetIdx) {
        return startWorker(binaryPath, presetIdx, 2.0f, 2.0f, 1, 0);
    }

    public static void stopWorker() {
        synchronized (LOCK) {
            if (sService != null) {
                try { sService.stopWorker(); }
                catch (RemoteException e) { Log.e(TAG, "stopWorker", e); }
            }
        }
    }

    public static boolean isReady() {
        synchronized (LOCK) {
            return sBound && sService != null;
        }
    }

    // ── Binder Proxy ──────────────────────────────────────────────────
    // Bagian dalam ShizukuHelper.java — ganti class UserServiceProxy
  private static class UserServiceProxy implements IUserService {
    private final android.os.IBinder remote;

    UserServiceProxy(android.os.IBinder remote) {
        this.remote = remote;
    }

    @Override
    public boolean startWorker(String srcPath, int presetIdx,
                               float sensX, float sensY,
                               int area, int tactix) throws RemoteException {
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(IUserService.DESCRIPTOR);
            data.writeString(srcPath);
            data.writeInt(presetIdx);
            data.writeFloat(sensX);
            data.writeFloat(sensY);
            data.writeInt(area);
            data.writeInt(tactix);
            remote.transact(1, data, reply, 0);
            reply.readException();
            return reply.readInt() != 0;
        } finally { data.recycle(); reply.recycle(); }
    }

    @Override
    public void stopWorker() throws RemoteException {
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(IUserService.DESCRIPTOR);
            remote.transact(2, data, reply, 0);
            reply.readException();
        } finally { data.recycle(); reply.recycle(); }
    }

    @Override
    public String ping() throws RemoteException {
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(IUserService.DESCRIPTOR);
            remote.transact(3, data, reply, 0);
            reply.readException();
            return reply.readString();
        } finally { data.recycle(); reply.recycle(); }
    }
  }
}