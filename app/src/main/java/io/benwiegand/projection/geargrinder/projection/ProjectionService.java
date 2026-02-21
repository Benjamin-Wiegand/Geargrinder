package io.benwiegand.projection.geargrinder.projection;

import static android.content.Context.BIND_AUTO_CREATE;
import static android.content.Context.BIND_IMPORTANT;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.os.IBinder;
import android.util.Log;
import android.view.InputEvent;
import android.view.Surface;

import java.util.Optional;

import io.benwiegand.projection.geargrinder.AccessibilityInputService;
import io.benwiegand.projection.geargrinder.PrivdService;
import io.benwiegand.projection.geargrinder.ProjectionActivity;
import io.benwiegand.projection.geargrinder.callback.IPCConnectionListener;
import io.benwiegand.projection.geargrinder.channel.InputChannel;
import io.benwiegand.projection.geargrinder.coordinate.CoordinateTranslator;
import io.benwiegand.projection.geargrinder.coordinate.InputEventConverter;
import io.benwiegand.projection.geargrinder.makeshiftbind.MakeshiftServiceConnection;
import io.benwiegand.projection.geargrinder.message.MessageBroker;
import io.benwiegand.projection.geargrinder.proto.data.readable.av.preset.VideoPreset;
import io.benwiegand.projection.geargrinder.proto.data.readable.input.InputChannelMeta;
import io.benwiegand.projection.geargrinder.proto.data.readable.input.event.TouchEvent;
import io.benwiegand.projection.libprivd.IPrivd;

public class ProjectionService implements InputEventConverter.ConvertedInputEventListener, IPCConnectionListener {
    private static final String TAG = ProjectionService.class.getSimpleName();

    private final VirtualDisplay virtualDisplay;
    private final InputEventConverter inputEventConverter;

    private VideoPreset videoPreset = VideoPreset.getDefault();

    private AccessibilityInputService.ServiceBinder accessibilityInputServiceBinder = null;
    private ProjectionActivity.ActivityBinder projectionActivityBinder = null;
    private PrivdService.ServiceBinder privdServiceBinder = null;
    private IPrivd privd = null;

    private final Context context;
    private final MessageBroker mb;

    private final CoordinateTranslator<TouchEvent.PointerLocation> projectionTouchCoordinateTranslator = CoordinateTranslator.createTouchEvent(
            x -> x + videoPreset.marginHorizontal() / 2,
            y -> y + videoPreset.marginVertical() / 2
    );

    public ProjectionService(Context context, MessageBroker mb) {
        this.context = context;
        this.mb = mb;
        DisplayManager dm = context.getSystemService(DisplayManager.class);
        virtualDisplay = dm.createVirtualDisplay(
                "Geargrinder projection",
                videoPreset.width(),
                videoPreset.height(),
                videoPreset.density(),
                null, DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY | DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION
        );

        inputEventConverter = new InputEventConverter(InputChannelMeta.getDefault(), this, virtualDisplay.getDisplay().getDisplayId(), videoPreset.width(), videoPreset.height());

        MakeshiftServiceConnection.bindService(context, new ComponentName(context, AccessibilityInputService.class), serviceConnection);
        MakeshiftServiceConnection.bindService(context, new ComponentName(context, ProjectionActivity.class), serviceConnection);
        context.bindService(new Intent(context, PrivdService.class), serviceConnection, BIND_AUTO_CREATE | BIND_IMPORTANT);
    }

    public void destroy() {
        context.unbindService(serviceConnection);
        serviceConnection.destroy();
        virtualDisplay.release();
    }

    public void setOutput(Surface surface, VideoPreset videoPreset) {
        Log.i(TAG, "attaching new output");
        if (!this.videoPreset.equals(videoPreset)) {
            Log.d(TAG, "resizing to " + videoPreset.width() + " x " + videoPreset.height() + ", dpi = " + videoPreset.density());
            virtualDisplay.resize(videoPreset.width(), videoPreset.height(), videoPreset.density());
            inputEventConverter.setTargetDisplaySize(videoPreset.width(), videoPreset.height());
            this.videoPreset = videoPreset;
        }

        virtualDisplay.setSurface(surface);

        getProjectionBinder()
                .ifPresent(binder -> binder.setMargins(videoPreset.marginHorizontal(), videoPreset.marginVertical()));
    }

    public void setInput(InputChannel inputChannel) {
        inputEventConverter.setInputMeta(inputChannel.getMetadata());
        inputChannel.setInputEventListener((event, translator) ->
                inputEventConverter.onTouchEvent(event, translator.chain(projectionTouchCoordinateTranslator)));
    }

    @Override
    public void onInputEvent(InputEvent event, int displayId, boolean displayIdSet) {
        try {
            boolean result = displayIdSet ? privd.injectInputEvent(event) : privd.injectInputEvent(event, displayId);
            if (!result) Log.w(TAG, "motion event result is false");
        } catch (Throwable t) {
            Log.e(TAG, "failed to inject motion event", t);
        }
    }

    private void onProjectionActivityConnected(ProjectionActivity.ActivityBinder binder) {
        binder.setMargins(videoPreset.marginHorizontal(), videoPreset.marginVertical());
    }

    @Override
    public void onPrivdConnected(IPrivd privd) {
        this.privd = privd;

        Log.d(TAG, "launching projection activity");
        try {
            privd.launchActivity(
                    new ComponentName(context, ProjectionActivity.class),
                    virtualDisplay.getDisplay().getDisplayId()
            );
        } catch (Throwable t) {
            Log.wtf(TAG, "failed to launch projection activity", t);
            mb.closeConnection();
        }
    }

    @Override
    public void onPrivdDisconnected() {
        if (!mb.isAlive()) return;

        Log.wtf(TAG, "privd connection lost, bailing");
        mb.closeConnection();
    }

    @Override
    public void onPrivdLaunchFailure(Throwable t) {
        // TODO: do something about this
        onPrivdDisconnected();
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
                case PrivdService.ServiceBinder binder -> {
                    privdServiceBinder = binder;
                    privdServiceBinder.requestDaemon(ProjectionService.this);
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
            } else if (new ComponentName(context, PrivdService.class).equals(name)) {
                privdServiceBinder = null;
            } else {
                Log.wtf(TAG, "unhandled component: " + name, new RuntimeException());
            }
        }
    };
}
