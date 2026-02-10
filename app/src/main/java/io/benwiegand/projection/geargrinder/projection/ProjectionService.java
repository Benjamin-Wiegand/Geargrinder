package io.benwiegand.projection.geargrinder.projection;

import android.content.ComponentName;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.os.IBinder;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;
import java.util.Optional;

import io.benwiegand.projection.geargrinder.AccessibilityInputService;
import io.benwiegand.projection.geargrinder.ProjectionActivity;
import io.benwiegand.projection.geargrinder.callback.InputEventListener;
import io.benwiegand.projection.geargrinder.channel.InputChannel;
import io.benwiegand.projection.geargrinder.coordinate.CoordinateTranslator;
import io.benwiegand.projection.geargrinder.makeshiftbind.MakeshiftServiceConnection;
import io.benwiegand.projection.geargrinder.proto.data.readable.av.preset.VideoPreset;
import io.benwiegand.projection.geargrinder.proto.data.readable.input.event.TouchEvent;

public class ProjectionService implements InputEventListener {
    private static final String TAG = ProjectionService.class.getSimpleName();

    private final VirtualDisplay virtualDisplay;

    private VideoPreset videoPreset = VideoPreset.getDefault();

    private AccessibilityInputService.ServiceBinder accessibilityInputServiceBinder = null;
    private ProjectionActivity.ActivityBinder projectionActivityBinder = null;

    private final Context context;

    private final CoordinateTranslator<TouchEvent.PointerLocation> projectionTouchCoordinateTranslator = CoordinateTranslator.create(
            x -> x + videoPreset.marginHorizontal() / 2,
            y -> y + videoPreset.marginVertical() / 2,
            TouchEvent.PointerLocation.class
    );

    public ProjectionService(Context context) {
        this.context = context;
        DisplayManager dm = context.getSystemService(DisplayManager.class);
        virtualDisplay = dm.createVirtualDisplay(
                "Geargrinder projection",
                videoPreset.width(),
                videoPreset.height(),
                videoPreset.density(),
                null, DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY | DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION
        );

        // TODO
        try {
            new ProcessBuilder("su", "-c", "am start-activity --display " + virtualDisplay.getDisplay().getDisplayId() + " io.benwiegand.projection.geargrinder/.ProjectionActivity").start();
        } catch (IOException e) {
            Log.e(TAG, "failed to launch projection activity", e);
        }

        MakeshiftServiceConnection.bindService(context, new ComponentName(context, AccessibilityInputService.class), serviceConnection);
        MakeshiftServiceConnection.bindService(context, new ComponentName(context, ProjectionActivity.class), serviceConnection);
    }

    public void destroy() {
        serviceConnection.destroy();
        virtualDisplay.release();
    }

    public void setOutput(Surface surface, VideoPreset videoPreset) {
        Log.i(TAG, "attaching new output");
        if (!this.videoPreset.equals(videoPreset)) {
            Log.d(TAG, "resizing to " + videoPreset.width() + " x " + videoPreset.height() + ", dpi = " + videoPreset.density());
            virtualDisplay.resize(videoPreset.width(), videoPreset.height(), videoPreset.density());
            this.videoPreset = videoPreset;
        }

        virtualDisplay.setSurface(surface);

        getProjectionBinder()
                .ifPresent(binder -> binder.setMargins(videoPreset.marginHorizontal(), videoPreset.marginVertical()));
    }

    public void setInput(InputChannel inputChannel) {
        inputChannel.setInputEventListener(this);
    }

    @Override
    public void onTouchEvent(TouchEvent event, CoordinateTranslator<TouchEvent.PointerLocation> translator) {
        getProjectionBinder()
                .map(ProjectionActivity.ActivityBinder::getInputEventListener)
                .ifPresent(l -> l.onTouchEvent(event, translator.chain(projectionTouchCoordinateTranslator)));
    }

    private void onProjectionActivityConnected(ProjectionActivity.ActivityBinder binder) {
        binder.setMargins(videoPreset.marginHorizontal(), videoPreset.marginVertical());
    }


    private Optional<AccessibilityInputService.ServiceBinder> getAccessibilityBinder() {
        return Optional.ofNullable(accessibilityInputServiceBinder);
    }

    private Optional<ProjectionActivity.ActivityBinder> getProjectionBinder() {
        return Optional.ofNullable(projectionActivityBinder);
    }

    private final MakeshiftServiceConnection serviceConnection = new MakeshiftServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.i(TAG, "connected: " + name.getShortClassName());
            switch (service) {
                case AccessibilityInputService.ServiceBinder binder -> accessibilityInputServiceBinder = binder;
                case ProjectionActivity.ActivityBinder binder -> {
                    projectionActivityBinder = binder;
                    onProjectionActivityConnected(binder);
                }
                default -> Log.wtf(TAG, "unhandled binder type", new RuntimeException());
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.i(TAG, "disconnected: " + name.getShortClassName());
            if (new ComponentName(context, AccessibilityInputService.class).equals(name)) {
                accessibilityInputServiceBinder = null;
            } else if (new ComponentName(context, ProjectionActivity.class).equals(name)) {
                projectionActivityBinder = null;
            } else {
                Log.wtf(TAG, "unhandled component: " + name, new RuntimeException());
            }
        }
    };
}
