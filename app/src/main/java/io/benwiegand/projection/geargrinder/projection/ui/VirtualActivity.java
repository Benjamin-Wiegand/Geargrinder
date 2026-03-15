package io.benwiegand.projection.geargrinder.projection.ui;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.io.IOException;

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

    private static final long SPLASH_SHOW_DURATION = 3000;
    private static final long SPLASH_ANIMATION_DURATION = 300;

    private final IPrivd privd;
    private final AppRecord app;
    private final VirtualActivityListener listener;
    private final VirtualDisplayController virtualDisplay;
    private final int density;
    private int width;
    private int height;

    private final View rootView;
    private final SurfaceView surfaceView;

    public interface VirtualActivityListener {
        void onVirtualActivityCloseButton(VirtualActivity virtualActivity);
    }

    @SuppressLint("ClickableViewAccessibility")
    public VirtualActivity(IPrivd privd, AppRecord app, ViewGroup parent, VirtualActivityListener listener) throws IOException, PackageManager.NameNotFoundException {
        this.privd = privd;
        this.app = app;
        this.listener = listener;
        density = parent.getResources().getDisplayMetrics().densityDpi;
        Context context = parent.getContext();
        PackageManager pm = context.getPackageManager();
        LayoutInflater inflater = LayoutInflater.from(context);

        // display
        VirtualDisplayController virtualDisplay;
        try {
            virtualDisplay = PrivdVirtualDisplayProxy.tryCreateWithFallbackFlags(
                    privd, VIRTUAL_DISPLAY_NAME,
                    800, 480, density,  // TODO
                    null, PRIVD_VIRTUAL_DISPLAY_FLAGS
            );
        } catch (Throwable t) {
            Log.e(TAG, "failed to create virtual display via privd", t);

            // this causes context issues and is not ideal
            Log.w(TAG, "falling back to local virtual display");
            DisplayManager dm = context.getSystemService(DisplayManager.class);
            virtualDisplay = new LocalVirtualDisplayController(
                    dm, VIRTUAL_DISPLAY_NAME,
                    800, 480, density,  // TODO
                    null, LOCAL_VIRTUAL_DISPLAY_FLAGS
            );
        }

        this.virtualDisplay = virtualDisplay;

        // view
        rootView = inflater.inflate(R.layout.layout_virtual_activity, parent, false);
        surfaceView = rootView.findViewById(R.id.virtual_activity_surface);
        surfaceView.getHolder().addCallback(this);

        TextView titleView = rootView.findViewById(R.id.virtual_activity_title);
        titleView.setText(app.label(pm));

        ImageView iconView = rootView.findViewById(R.id.virtual_activity_icon);
        iconView.setImageDrawable(app.icon(pm));

        rootView.findViewById(R.id.virtual_activity_close_button)
                .setOnClickListener(v -> listener.onVirtualActivityCloseButton(this));

        // touch
        surfaceView.getViewTreeObserver().addOnGlobalLayoutListener(() ->
                onLayoutUpdated(surfaceView.getWidth(), surfaceView.getHeight()));
        surfaceView.setOnTouchListener(this::onMotionEvent);
        surfaceView.setOnGenericMotionListener(this::onMotionEvent);

        // launch
        try {
            int result = privd.launchActivity(app.launchComponent(), getDisplayId());
            Log.d(TAG, "launch result " + result + " for " + app.launchComponent().flattenToShortString());
        } catch (Throwable t) {
            destroy();
            throw new RuntimeException("failed to launch activity for virtual activity", t);
        }
    }

    public void destroy() {
        virtualDisplay.release();
    }

    public ComponentName getComponentName() {
        return app.launchComponent();
    }

    public AppRecord getAppRecord() {
        return app;
    }

    private void showSplash(boolean animateIn) {
        View splash = rootView.findViewById(R.id.virtual_activity_splash);
        splash.animate()
                .setStartDelay(0)
                .setDuration(animateIn ? SPLASH_ANIMATION_DURATION : 0)
                .alpha(0.99f)
                .withStartAction(() -> splash.setVisibility(View.VISIBLE))
                .withEndAction(() -> splash.animate()
                        .setStartDelay(SPLASH_SHOW_DURATION)
                        .setDuration(SPLASH_ANIMATION_DURATION)
                        .alpha(0f)
                        .withEndAction(() -> splash.setVisibility(View.GONE)));
    }

    public void showSplash() {
        showSplash(true);
    }

    public View getRootView() {
        return rootView;
    }

    public int getDisplayId() {
        return virtualDisplay.getDisplayId();
    }

    private void onLayoutUpdated(int width, int height) {
        if (this.width != width || this.height != height) {
            this.width = width;
            this.height = height;
            virtualDisplay.resize(width, height, density);
            showSplash(false);
        }
    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
        if (virtualDisplay.getSurface() != holder.getSurface())
            virtualDisplay.setSurface(holder.getSurface());

        onLayoutUpdated(width, height);
    }

    @Override
    public void surfaceCreated(@NonNull SurfaceHolder holder) {
        Log.d(TAG, "surface created");
        showSplash(false);
    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
        Log.d(TAG, "surface destroyed");
        virtualDisplay.setSurface(null);
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
