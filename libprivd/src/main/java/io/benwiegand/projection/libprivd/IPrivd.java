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

    static IPrivd fromBinder(IBinder binder) {
        return (IPrivd) ReflectingBinder.proxyInterface(binder, IPrivd.class);
    }

    abstract class Stub extends ReflectingBinder implements IPrivd {
        public Stub() {
            super(IPrivd.class);
        }
    }
}
