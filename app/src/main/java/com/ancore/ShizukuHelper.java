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
    private static boolean sBound = false;
    private static final Object LOCK = new Object();

    public interface OnReadyCallback {
        void onReady(boolean success);
    }

    // List callback disimpan di luar lock supaya dispatch tidak deadlock
    private static final List<OnReadyCallback> sPendingCallbacks = new ArrayList<>();

    private static void dispatchReady(boolean success) {
        List<OnReadyCallback> toCall;
        synchronized (LOCK) {
            toCall = new ArrayList<>(sPendingCallbacks);
            sPendingCallbacks.clear();
        }
        // Panggil callback DI LUAR synchronized — cegah deadlock
        for (OnReadyCallback cb : toCall) {
            try { cb.onReady(success); } catch (Throwable ignored) {}
        }
    }

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
            dispatchReady(true); // di luar LOCK — aman
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
                    new ComponentName("com.ancore", "com.ancore.UserService"))
                    .daemon(false)
                    .processNameSuffix("ancore_svc")
                    .debuggable(false)
                    .version(1);

    public static void init(OnReadyCallback callback) {
        synchronized (LOCK) {
            if (sBound && sService != null) {
                // Sudah ready, langsung callback di luar lock
                if (callback != null) {
                    try { callback.onReady(true); } catch (Throwable ignored) {}
                }
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

    private static class UserServiceProxy implements IUserService {
        private final IBinder remote;

        UserServiceProxy(IBinder remote) {
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