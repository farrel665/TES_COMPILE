package com.ancore;

import android.os.RemoteException;

public interface IUserService {

    String DESCRIPTOR = "com.ancore.IUserService";

    boolean startWorker(String srcPath, int presetIdx,
                        float sensX, float sensY,
                        int area, int tactix) throws RemoteException;

    void stopWorker() throws RemoteException;

    String ping() throws RemoteException;
}