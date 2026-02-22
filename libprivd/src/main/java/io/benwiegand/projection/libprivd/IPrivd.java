package io.benwiegand.projection.libprivd;

import android.content.ComponentName;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;
import android.view.InputEvent;
import android.view.Surface;

import io.benwiegand.projection.libprivd.ipc.ReflectingBinder;

public interface IPrivd extends IInterface {

    void ping() throws RemoteException;

    boolean injectInputEvent(InputEvent event) throws RemoteException;

    boolean injectInputEvent(InputEvent event, int displayId) throws RemoteException;

    int launchActivity(ComponentName component, int displayId) throws RemoteException;

    int createVirtualDisplay(String name, int width, int height, int densityDpi, Surface surface, int flags) throws RemoteException;

    void releaseVirtualDisplay(int displayId) throws RemoteException;

    void virtualDisplayResize(int displayId, int width, int height, int densityDpi) throws RemoteException;

    void virtualDisplaySetSurface(int displayId, Surface surface) throws RemoteException;

    static IPrivd fromBinder(IBinder binder) {
        return (IPrivd) ReflectingBinder.proxyInterface(binder, IPrivd.class);
    }

    abstract class Stub extends ReflectingBinder implements IPrivd {
        public Stub() {
            super(IPrivd.class);
        }
    }
}
