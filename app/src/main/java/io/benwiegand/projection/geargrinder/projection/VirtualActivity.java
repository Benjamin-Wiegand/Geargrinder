package io.benwiegand.projection.geargrinder.projection;

import static io.benwiegand.projection.geargrinder.util.UiUtil.getViewBoundsInDisplay;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.io.IOException;

import io.benwiegand.projection.geargrinder.R;
import io.benwiegand.projection.geargrinder.callback.InputEventListener;
import io.benwiegand.projection.geargrinder.coordinate.CoordinateTranslator;
import io.benwiegand.projection.geargrinder.proto.data.readable.input.event.TouchEvent;
import io.benwiegand.projection.geargrinder.shell.RootShell;
import io.benwiegand.projection.geargrinder.util.RootUtil;

public class VirtualActivity implements SurfaceHolder.Callback, InputEventListener {
    private static final String TAG = VirtualActivity.class.getSimpleName();

    private static final long SPLASH_SHOW_DURATION = 3000;
    private static final long SPLASH_ANIMATION_DURATION = 300;

    private final RootShell rootShell;
    private final VirtualDisplay virtualDisplay;
    private final InputEventMuxer inputEventMuxer;
    private final InputEventMuxer.Destination inputDestination;
    private final int density;
    private int width;
    private int height;

    private final View rootView;
    private final SurfaceView surfaceView;

    public VirtualActivity(RootShell rootShell, InputEventMuxer inputEventMuxer, ComponentName componentName, ViewGroup parent) throws IOException, PackageManager.NameNotFoundException {
        this.rootShell = rootShell;
        this.inputEventMuxer = inputEventMuxer;
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

        // TODO
        try {
            // launch
            rootShell.writeLine("am start-activity --display " + virtualDisplay.getDisplay().getDisplayId() + " " + componentName.flattenToShortString());
        } catch (Throwable t) {
            virtualDisplay.release();
            throw t;
        }

        // view
        rootView = inflater.inflate(R.layout.layout_virtual_activity, parent, false);
        surfaceView = rootView.findViewById(R.id.virtual_activity_surface);
        surfaceView.getHolder().addCallback(this);

        TextView titleView = rootView.findViewById(R.id.virtual_activity_title);
        titleView.setText(pm.getApplicationLabel(activityInfo.applicationInfo));

        ImageView iconView = rootView.findViewById(R.id.virtual_activity_icon);
        iconView.setImageDrawable(pm.getApplicationIcon(activityInfo.applicationInfo));

        // touch
        inputDestination = inputEventMuxer.addDestination(this);
        surfaceView.getViewTreeObserver().addOnGlobalLayoutListener(() ->
                onLayoutUpdated(surfaceView.getWidth(), surfaceView.getHeight()));
    }

    public void destroy() {
        inputEventMuxer.removeDestination(inputDestination);
        virtualDisplay.release();
    }

    private void showSplash(boolean animateIn) {
        View splash = rootView.findViewById(R.id.virtual_activity_splash);
        // TODO: disable input
        splash.animate()
                .setStartDelay(0)
                .setDuration(animateIn ? SPLASH_ANIMATION_DURATION : 0)
                .alpha(0.99f)
                .withEndAction(() -> splash.animate()
                        .setStartDelay(SPLASH_SHOW_DURATION)
                        .setDuration(SPLASH_ANIMATION_DURATION)
                        .alpha(0f));
    }

    public void showSplash() {
        showSplash(true);
    }

    public View getRootView() {
        return rootView;
    }

    private void onLayoutUpdated(int width, int height) {
        getViewBoundsInDisplay(surfaceView, inputDestination.getBounds());
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
        inputDestination.setEnabled(true);
        showSplash(false);
    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
        Log.d(TAG, "surface destroyed");
        virtualDisplay.setSurface(null);
        inputDestination.setEnabled(false);
    }

    @Override
    public void onTouchEvent(TouchEvent event, CoordinateTranslator<TouchEvent.PointerLocation> translator) {
        try {
            RootUtil.simulateTouchEventRoot(rootShell, virtualDisplay.getDisplay().getDisplayId(), event, translator);
        } catch (IOException e) {
            Log.e(TAG, "failed to simulate touch", e);
        }
    }
}
