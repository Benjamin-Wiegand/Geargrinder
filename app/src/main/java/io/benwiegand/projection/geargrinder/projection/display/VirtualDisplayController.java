package io.benwiegand.projection.geargrinder.projection.display;

import android.os.RemoteException;
import android.view.Surface;

public interface VirtualDisplayController {

    void release();

    void resize(int width, int height, int densityDpi);

    void setSurface(Surface surface);

    Surface getSurface();

    int getDisplayId();

}
