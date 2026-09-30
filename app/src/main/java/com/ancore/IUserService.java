package com.ancore;

import android.os.IInterface;
import android.os.RemoteException;

public interface IUserService extends IInterface {
    String DESCRIPTOR = "com.ancore.IUserService";

    /**
     * Starts the native worker through the Shizuku UserService.
     * area: 0 Left / 1 All / 2 Right.
     * tactix: 0 off / 1 on.
     * strength/responsiveness are normalized 0..100 values.
     */
    boolean startWorker(String srcPath, int presetIdx, float sensX, float sensY,
                        int area, int tactix, float strength,
                        float responsiveness) throws RemoteException;

    void stopWorker() throws RemoteException;
    boolean startMacro(int x, int y, int intervalMs, int durationMs) throws RemoteException;
    void stopMacro() throws RemoteException;
    String ping() throws RemoteException;
}
