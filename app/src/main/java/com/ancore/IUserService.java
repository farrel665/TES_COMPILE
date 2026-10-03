package com.ancore;

import android.os.IInterface;
import android.os.RemoteException;

public interface IUserService extends IInterface {
    String DESCRIPTOR = "com.ancore.IUserService";

    boolean startWorker(String srcPath, int presetIdx, float sensX, float sensY,
                        int area, int tactix, float strength,
                        float responsiveness) throws RemoteException;

    void stopWorker() throws RemoteException;
    String ping() throws RemoteException;
}
