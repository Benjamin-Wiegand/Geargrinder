package io.benwiegand.projection.geargrinder.projection;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
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

import io.benwiegand.projection.geargrinder.PrivdService;
import io.benwiegand.projection.geargrinder.R;
import io.benwiegand.projection.libprivd.data.ActivityLaunchParams;
import io.benwiegand.projection.libprivd.data.SerializableMotionEvent;

public class VirtualActivity implements SurfaceHolder.Callback {
    private static final String TAG = VirtualActivity.class.getSimpleName();

    private static final long SPLASH_SHOW_DURATION = 3000;
    private static final long SPLASH_ANIMATION_DURATION = 300;

    private final PrivdService.ServiceBinder privd;
    private final VirtualActivityListener listener;
    private final VirtualDisplay virtualDisplay;
    private final int density;
    private int width;
    private int height;

    private final View rootView;
    private final SurfaceView surfaceView;

    public interface VirtualActivityListener {
        void onVirtualActivityLaunched(VirtualActivity virtualActivity);
        void onVirtualActivityLaunchFailure(VirtualActivity virtualActivity);
        void onVirtualActivityCloseButton(VirtualActivity virtualActivity);
    }

    @SuppressLint("ClickableViewAccessibility")
    public VirtualActivity(PrivdService.ServiceBinder privd, ComponentName componentName, ViewGroup parent, VirtualActivityListener listener) throws IOException, PackageManager.NameNotFoundException {
        this.privd = privd;
        this.listener = listener;
        density = parent.getResources().getDisplayMetrics().densityDpi;
        Context context = parent.getContext();
        DisplayManager dm = context.getSystemService(DisplayManager.class);
        PackageManager pm = context.getPackageManager();
        ActivityInfo activityInfo = pm.getActivityInfo(componentName, 0);
        LayoutInflater inflater = LayoutInflater.from(context);

        // display
        virtualDisplay = dm.createVirtualDisplay(
                "Geargrinder virtual activity",
                800, 480, density,  // TODO
                null, DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY | DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION
        );

        // view
        rootView = inflater.inflate(R.layout.layout_virtual_activity, parent, false);
        surfaceView = rootView.findViewById(R.id.virtual_activity_surface);
        surfaceView.getHolder().addCallback(this);

        TextView titleView = rootView.findViewById(R.id.virtual_activity_title);
        titleView.setText(pm.getApplicationLabel(activityInfo.applicationInfo));

        ImageView iconView = rootView.findViewById(R.id.virtual_activity_icon);
        iconView.setImageDrawable(pm.getApplicationIcon(activityInfo.applicationInfo));

        rootView.findViewById(R.id.virtual_activity_close_button)
                .setOnClickListener(v -> listener.onVirtualActivityCloseButton(this));

        // touch
        surfaceView.getViewTreeObserver().addOnGlobalLayoutListener(() ->
                onLayoutUpdated(surfaceView.getWidth(), surfaceView.getHeight()));
        surfaceView.setOnTouchListener(this::onMotionEvent);
        surfaceView.setOnGenericMotionListener(this::onMotionEvent);

        // launch
        privd.launchActivity(new ActivityLaunchParams(componentName, getDisplayId()))
                .doOnResult(r -> {
                    Log.d(TAG, "launch result: " + r);
                    Log.v(TAG, "successfully launched virtual activity component: " + componentName);
                    listener.onVirtualActivityLaunched(this);
                })
                .doOnError(t -> {
                    Log.e(TAG, "failed to launch virtual activity component: " + componentName, t);
                    listener.onVirtualActivityLaunchFailure(this);
                })
                .callMeWhenDone();

    }

    public void destroy() {
        virtualDisplay.release();
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
        return virtualDisplay.getDisplay().getDisplayId();
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
        privd.injectMotionEvent(SerializableMotionEvent.fromMotionEvent(event, getDisplayId()))
                .doOnResult(r -> {
                    if (r) return;
                    Log.w(TAG, "motion event result = false");
                })
                .doOnError(t -> Log.e(TAG, "failed to inject motion event", t))
                .callMeWhenDone();
        return true;
    }
}
