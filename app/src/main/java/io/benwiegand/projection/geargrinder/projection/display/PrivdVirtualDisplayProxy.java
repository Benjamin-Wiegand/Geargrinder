package io.benwiegand.projection.geargrinder.projection.display;

import android.os.RemoteException;
import android.util.Log;
import android.view.Surface;

import io.benwiegand.projection.libprivd.IPrivd;

public class PrivdVirtualDisplayProxy implements VirtualDisplayController {
    private static final String TAG = PrivdVirtualDisplayProxy.class.getSimpleName();

    // some useful hidden flags that can be used to create virtual displays, they can be used with privd but not locally
    // for documentation on these, look in DisplayManager
    public static final int FLAG_CAN_SHOW_WITH_INSECURE_KEYGUARD = 1 << 5;
    public static final int FLAG_SUPPORTS_TOUCH = 1 << 6;
    public static final int FLAG_DESTROY_CONTENT_ON_REMOVAL = 1 << 8;
    public static final int FLAG_SHOULD_SHOW_SYSTEM_DECORATIONS = 1 << 9;
    public static final int FLAG_TRUSTED = 1 << 10;
    public static final int FLAG_OWN_DISPLAY_GROUP = 1 << 11;
    public static final int FLAG_ALWAYS_UNLOCKED = 1 << 12;
    public static final int FLAG_TOUCH_FEEDBACK_DISABLED = 1 << 13;
    public static final int FLAG_OWN_FOCUS = 1 << 14;
    public static final int FLAG_STEAL_TOP_FOCUS_DISABLED = 1 << 16;

    private final IPrivd privd;

    private final int displayId;

    private final String name;
    private int width;
    private int height;
    private int densityDpi;
    private Surface surface;
    private final int flags;

    public PrivdVirtualDisplayProxy(IPrivd privd, String name, int width, int height, int densityDpi, Surface surface, int flags) throws RemoteException {
        this.privd = privd;
        this.name = name;
        this.width = width;
        this.height = height;
        this.densityDpi = densityDpi;
        this.surface = surface;
        this.flags = flags;

        displayId = privd.createVirtualDisplay(name, width, height, densityDpi, surface, flags);
    }

    @Override
    public void release() {
        try {
            privd.releaseVirtualDisplay(displayId);
        } catch (RemoteException e) {
            Log.w(TAG, "failed to release virtual display", e);
            // don't throw, privd is probably dead and the display is released anyway
        }
    }

    @Override
    public void resize(int width, int height, int densityDpi) {
        try {
            privd.virtualDisplayResize(displayId, width, height, densityDpi);
        } catch (RemoteException e) {
            throw new RuntimeException("failed to resize virtual display", e);
        }

        this.width = width;
        this.height = height;
        this.densityDpi = densityDpi;
    }

    @Override
    public void setSurface(Surface surface) {
        try {
            privd.virtualDisplaySetSurface(displayId, surface);
        } catch (RemoteException e) {
            throw new RuntimeException("failed to set virtual display surface", e);
        }

        this.surface = surface;
    }

    @Override
    public Surface getSurface() {
        return surface;
    }

    @Override
    public int getDisplayId() {
        return displayId;
    }

    @Override
    public String toString() {
        return "PrivdVirtualDisplayProxy{" +
                "privd=" + privd +
                ", displayId=" + displayId +
                ", name='" + name + '\'' +
                ", width=" + width +
                ", height=" + height +
                ", densityDpi=" + densityDpi +
                ", surface=" + surface +
                ", flags=" + flags +
                '}';
    }
}
