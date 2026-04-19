package io.benwiegand.projection.geargrinder.projection.ui;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import io.benwiegand.projection.geargrinder.R;
import io.benwiegand.projection.geargrinder.pm.AppRecord;
import io.benwiegand.projection.geargrinder.projection.display.LocalVirtualDisplayController;
import io.benwiegand.projection.geargrinder.projection.display.PrivdVirtualDisplayProxy;
import io.benwiegand.projection.geargrinder.projection.display.VirtualDisplayController;
import io.benwiegand.projection.libprivd.IPrivd;

public class VirtualActivity implements SurfaceHolder.Callback {
    private static final String TAG = VirtualActivity.class.getSimpleName();

    private static final String VIRTUAL_DISPLAY_NAME = "Geargrinder virtual activity";

    // uses system/protected flags to make it work correctly
    private static final int PRIVD_VIRTUAL_DISPLAY_BASE_FLAGS = DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
            | PrivdVirtualDisplayProxy.FLAG_CAN_SHOW_WITH_INSECURE_KEYGUARD
            | PrivdVirtualDisplayProxy.FLAG_SUPPORTS_TOUCH
            | PrivdVirtualDisplayProxy.FLAG_TRUSTED
            | PrivdVirtualDisplayProxy.FLAG_OWN_DISPLAY_GROUP
            | PrivdVirtualDisplayProxy.FLAG_ALWAYS_UNLOCKED
            | PrivdVirtualDisplayProxy.FLAG_OWN_FOCUS;

    // sets of flags to try since sometimes not all of them work
    private static final int[] PRIVD_VIRTUAL_DISPLAY_FLAGS = new int[] {
            PRIVD_VIRTUAL_DISPLAY_BASE_FLAGS | DisplayManager.VIRTUAL_DISPLAY_FLAG_SECURE,
            PRIVD_VIRTUAL_DISPLAY_BASE_FLAGS,
    };

    // this is all that really can be done without elevated privileges.
    // apps that launch new activities will have those appear on the main display.
    // this also make some apps completely unusable since they use their launcher activity to start the actual main activity.
    private static final int LOCAL_VIRTUAL_DISPLAY_FLAGS = DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
            | DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION;

    private static final long LOADING_SPLASH_MAX_SHOW_DURATION = 500;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private final IPrivd privd;
    private final AppRecord app;
    private VirtualDisplayController virtualDisplay = null;
    private final int density;
    private int width;
    private int height;
    private boolean localDisplayFallback = false;

    private long splashShownAt = 0;
    private boolean validFrame = false;
    private boolean launched = false;

    private final View rootView;
    private final SurfaceView surfaceView;
    private final VirtualActivitySplash splash;

    @SuppressLint("ClickableViewAccessibility")
    public VirtualActivity(IPrivd privd, AppRecord app, ViewGroup parent) {
        this.privd = privd;
        this.app = app;
        width = 800;
        height = 600;
        density = parent.getResources().getDisplayMetrics().densityDpi;
        Context context = parent.getContext();
        LayoutInflater inflater = LayoutInflater.from(context);

        // view
        rootView = inflater.inflate(R.layout.layout_virtual_activity, parent, false);
        surfaceView = rootView.findViewById(R.id.virtual_activity_surface);
        surfaceView.getHolder().addCallback(this);

        splash = new VirtualActivitySplash(rootView.findViewById(R.id.virtual_activity_splash), this);

        // touch
        surfaceView.getViewTreeObserver().addOnGlobalLayoutListener(() ->
                onLayoutUpdated(surfaceView.getWidth(), surfaceView.getHeight()));
        surfaceView.setOnTouchListener(this::onMotionEvent);
        surfaceView.setOnGenericMotionListener(this::onMotionEvent);

        // frame
        surfaceView.getViewTreeObserver().addOnDrawListener(this::onFrame);

        tryLaunch();
    }

    public Context getContext() {
        return rootView.getContext();
    }

    public void launch() throws RemoteException {
        if (launched) Log.i(TAG, "re-launching");
        else Log.i(TAG, "trying launch");

        try {
            // display
            if (virtualDisplay != null && localDisplayFallback) {
                Log.i(TAG, "releasing local virtual display for re-launch");
                virtualDisplay.release();
                virtualDisplay = null;
                launched = false;
            }

            if (virtualDisplay == null) {
                VirtualDisplayController virtualDisplay;
                try {
                    virtualDisplay = PrivdVirtualDisplayProxy.tryCreateWithFallbackFlags(
                            privd, VIRTUAL_DISPLAY_NAME,
                            width, height, density,
                            null, PRIVD_VIRTUAL_DISPLAY_FLAGS
                    );

                    localDisplayFallback = false;
                } catch (Throwable t) {
                    Log.e(TAG, "failed to create virtual display via privd", t);

                    // this causes context issues and is not ideal
                    Log.w(TAG, "falling back to local virtual display");
                    DisplayManager dm = getContext().getSystemService(DisplayManager.class);
                    virtualDisplay = new LocalVirtualDisplayController(
                            dm, VIRTUAL_DISPLAY_NAME,
                            width, height, density,
                            null, LOCAL_VIRTUAL_DISPLAY_FLAGS
                    );

                    localDisplayFallback = true;
                }

                this.virtualDisplay = virtualDisplay;
            }

            invalidateFrame();

            // launch
            int result = privd.launchActivity(app.launchComponent(), getDisplayId());
            Log.d(TAG, "launch result " + result + " for " + app.launchComponent().flattenToShortString());

        } catch (Throwable t) {
            Log.e(TAG, "failed to launch virtual activity", t);
            // TODO: splash with retry button
            throw t;
        }
    }

    private boolean tryLaunch() {
        try {
            launch();
            return true;
        } catch (Throwable ignored) { }
        return false;
    }

    public boolean isLaunched() {
        return launched;
    }

    public void destroy() {
        if (virtualDisplay != null)
            virtualDisplay.release();
    }

    public ComponentName getComponentName() {
        return app.launchComponent();
    }

    public AppRecord getAppRecord() {
        return app;
    }

    private void updateSplashVisibility() {
        if (!splash.isVisible()) return;

        boolean timeout = splashShownAt + LOADING_SPLASH_MAX_SHOW_DURATION <= SystemClock.elapsedRealtime();
        if (!validFrame && !timeout) return;

        Log.d(TAG, "hiding splash: validFrame=" + validFrame + ", timeout=" + timeout);
        splash.hide();
    }

    private void showSplash() {
        splashShownAt = SystemClock.elapsedRealtime();
        handler.postDelayed(this::updateSplashVisibility, LOADING_SPLASH_MAX_SHOW_DURATION);
        if (splash.isVisible()) return;

        Log.d(TAG, "showing splash: validFrame=" + validFrame + ", launched=" + launched);
        splash.show(false);
    }

    private void invalidateFrame() {
        validFrame = false;
        showSplash();
    }

    public View getRootView() {
        return rootView;
    }

    public int getDisplayId() {
        if (virtualDisplay == null) return -1;
        return virtualDisplay.getDisplayId();
    }

    private void onFrame() {
        if (surfaceView.getWidth() == 0 || surfaceView.getHeight() == 0) return;

        if (!validFrame) {
            Log.d(TAG, "got initial frame");
            launched = true;
            validFrame = true;
            // TODO: find first activity frame more accurately. this only sees the first virtual display frame
            //updateSplashVisibility();
        }
    }

    private void onLayoutUpdated(int width, int height) {
        if (width <= 0 || height <= 0) {
            Log.w(TAG, "ignoring layout update due to unsupported size: " + width + "x" + height);
            return;
        }

        if (this.width != width || this.height != height) {
            this.width = width;
            this.height = height;

            if (virtualDisplay != null) {
                Log.d(TAG, "resizing display to " + width + "x" + height);
                virtualDisplay.resize(width, height, density);
                invalidateFrame();
            }
        }
    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
        if (virtualDisplay != null && virtualDisplay.getSurface() != holder.getSurface()) {
            Log.d(TAG, "setting new surface");
            virtualDisplay.setSurface(holder.getSurface());
            invalidateFrame();
        }

        onLayoutUpdated(width, height);
    }

    @Override
    public void surfaceCreated(@NonNull SurfaceHolder holder) {
        Log.d(TAG, "surface created");
        invalidateFrame();
    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
        Log.d(TAG, "surface destroyed");
        if (virtualDisplay != null) {
            virtualDisplay.setSurface(null);
            invalidateFrame();
        }
    }

    private boolean onMotionEvent(View view, MotionEvent event) {
        try {
            return privd.injectInputEventWithDisplayId(event, getDisplayId());
        } catch (Throwable t) {
            Log.e(TAG, "failed to inject motion event", t);
            return false;
        }
    }
}
