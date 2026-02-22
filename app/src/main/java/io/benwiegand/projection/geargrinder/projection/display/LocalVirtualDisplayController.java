package io.benwiegand.projection.geargrinder.projection.display;

import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.view.Surface;

public class LocalVirtualDisplayController implements VirtualDisplayController {

    private final VirtualDisplay virtualDisplay;

    public LocalVirtualDisplayController(DisplayManager dm, String name, int width, int height, int densityDpi, Surface surface, int flags) {
        virtualDisplay = dm.createVirtualDisplay(name, width, height, densityDpi, surface, flags);
        if (virtualDisplay == null) throw new RuntimeException("createVirtualDisplay() returned null");
    }

    @Override
    public void release() {
        virtualDisplay.release();
    }

    @Override
    public void resize(int width, int height, int densityDpi) {
        virtualDisplay.resize(width, height, densityDpi);
    }

    @Override
    public void setSurface(Surface surface) {
        virtualDisplay.setSurface(surface);
    }

    @Override
    public Surface getSurface() {
        return virtualDisplay.getSurface();
    }

    @Override
    public int getDisplayId() {
        return virtualDisplay.getDisplay().getDisplayId();
    }

    @Override
    public String toString() {
        return "LocalVirtualDisplayController{" +
                "virtualDisplay=" + virtualDisplay +
                '}';
    }
}
