package com.ancore;

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.util.Log;

import rikka.shizuku.Shizuku;

/** Shizuku-only UserService bridge. No root shell is requested by this class. */
public final class ShizukuHelper {
    private static final String TAG = "ancore_shizuku";
    private static final int REQUEST_CODE = 1001;
    private static IUserService sService;
    private static boolean sBound;
    private static final Object LOCK = new Object();

    private static final ServiceConnection CONN = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder service) {
            Log.i(TAG, "UserService connected");
            synchronized (LOCK) {
                sService = new UserServiceProxy(service);
                sBound = true;
            }
            try { Log.i(TAG, "ping -> " + sService.ping()); }
            catch (Throwable t) { Log.w(TAG, "ping failed", t); }
        }

        @Override public void onServiceDisconnected(ComponentName name) {
            Log.w(TAG, "UserService disconnected");
            synchronized (LOCK) {
                sService = null;
                sBound = false;
            }
        }
    };

    private static final Shizuku.UserServiceArgs ARGS =
            new Shizuku.UserServiceArgs(
                    new ComponentName("com.ancore", UserService.class.getName()))
                    .daemon(false)
                    .processNameSuffix("ancore_svc")
                    .debuggable(false)
                    .version(2);

    private ShizukuHelper() {}

    public static void init() {
        if (!Shizuku.pingBinder()) {
            Log.w(TAG, "Shizuku not running");
            return;
        }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Shizuku.requestPermission(REQUEST_CODE);
            Shizuku.addRequestPermissionResultListener((requestCode, grantResult) -> {
                if (requestCode == REQUEST_CODE &&
                        grantResult == PackageManager.PERMISSION_GRANTED) {
                    bindService();
                }
            });
        } else {
            bindService();
        }
    }

    private static void bindService() {
        synchronized (LOCK) {
            if (sBound && sService != null) return;
        }
        try {
            Shizuku.bindUserService(ARGS, CONN);
            Log.i(TAG, "bindUserService requested");
        } catch (Throwable t) {
            Log.e(TAG, "bindUserService failed", t);
        }
    }

    public static boolean startWorker(String binaryPath, int presetIdx,
                                      float sensX, float sensY, int area, int tactix,
                                      float strength, float responsiveness) {
        synchronized (LOCK) {
            if (sService == null) return false;
            try {
                return sService.startWorker(binaryPath, presetIdx, sensX, sensY,
                        area, tactix, strength, responsiveness);
            } catch (RemoteException e) {
                Log.e(TAG, "startWorker RemoteException", e);
                return false;
            }
        }
    }

    public static boolean startWorker(String binaryPath, int presetIdx,
                                      float sensX, float sensY, int area, int tactix) {
        return startWorker(binaryPath, presetIdx, sensX, sensY, area, tactix, 0f, 0f);
    }

    public static boolean startWorker(String binaryPath, int presetIdx,
                                      float sensX, float sensY) {
        return startWorker(binaryPath, presetIdx, sensX, sensY, 1, 0, 0f, 0f);
    }

    public static boolean startWorker(String binaryPath, int presetIdx) {
        return startWorker(binaryPath, presetIdx, 2.0f, 2.0f, 1, 0, 0f, 0f);
    }

    public static void stopWorker() {
        synchronized (LOCK) {
            if (sService != null) {
                try { sService.stopWorker(); }
                catch (RemoteException e) { Log.e(TAG, "stopWorker", e); }
            }
        }
    }

    public static boolean isReady() { return sBound && sService != null; }

    private static final class UserServiceProxy implements IUserService {
        private final IBinder remote;
        UserServiceProxy(IBinder remote) { this.remote = remote; }
        @Override public IBinder asBinder() { return remote; }

        @Override
        public boolean startWorker(String srcPath, int presetIdx, float sensX, float sensY,
                                   int area, int tactix, float strength,
                                   float responsiveness) throws RemoteException {
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken(DESCRIPTOR);
                data.writeString(srcPath);
                data.writeInt(presetIdx);
                data.writeFloat(sensX);
                data.writeFloat(sensY);
                data.writeInt(area);
                data.writeInt(tactix);
                data.writeFloat(strength);
                data.writeFloat(responsiveness);
                remote.transact(1, data, reply, 0);
                reply.readException();
                return reply.readInt() != 0;
            } finally {
                data.recycle();
                reply.recycle();
            }
        }

        @Override public void stopWorker() throws RemoteException {
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken(DESCRIPTOR);
                remote.transact(2, data, reply, 0);
                reply.readException();
            } finally { data.recycle(); reply.recycle(); }
        }

        @Override public String ping() throws RemoteException {
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken(DESCRIPTOR);
                remote.transact(3, data, reply, 0);
                reply.readException();
                return reply.readString();
            } finally { data.recycle(); reply.recycle(); }
        }
    }
}
