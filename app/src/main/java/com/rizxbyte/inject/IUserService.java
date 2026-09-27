package com.rizxbyte.inject;

import android.os.IInterface;
import android.os.RemoteException;

public interface IUserService extends IInterface {
    String DESCRIPTOR = "com.rizxbyte.inject.IUserService";

    /**
     * Stage + exec worker.
     * @param presetIdx 0 Linear / 1 Accel / 2 Decel
     * @param area      0 Left / 1 All / 2 Right
     * @param tactix    0 off / 1 on (deadzone + flick boost)
     */
    boolean startWorker(String srcPath, int presetIdx, float sensX, float sensY,
                        int area, int tactix) throws RemoteException;

    void stopWorker() throws RemoteException;

    String ping() throws RemoteException;
}
