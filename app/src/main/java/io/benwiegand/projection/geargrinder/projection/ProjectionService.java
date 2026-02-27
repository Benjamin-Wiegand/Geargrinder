package io.benwiegand.projection.geargrinder.projection;

import static android.content.Context.BIND_AUTO_CREATE;
import static android.content.Context.BIND_IMPORTANT;

import android.content.ComponentName;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.util.Log;
import android.view.InputEvent;
import android.view.Surface;

import io.benwiegand.projection.geargrinder.PrivdService;
import io.benwiegand.projection.geargrinder.ProjectionActivity;
import io.benwiegand.projection.geargrinder.callback.IPCConnectionListener;
import io.benwiegand.projection.geargrinder.channel.InputChannel;
import io.benwiegand.projection.geargrinder.coordinate.CoordinateTranslator;
import io.benwiegand.projection.geargrinder.coordinate.InputEventConverter;
import io.benwiegand.projection.geargrinder.message.MessageBroker;
import io.benwiegand.projection.geargrinder.projection.display.LocalVirtualDisplayController;
import io.benwiegand.projection.geargrinder.projection.display.PrivdVirtualDisplayProxy;
import io.benwiegand.projection.geargrinder.projection.display.VirtualDisplayController;
import io.benwiegand.projection.geargrinder.proto.data.readable.av.preset.VideoPreset;
import io.benwiegand.projection.geargrinder.proto.data.readable.input.InputChannelMeta;
import io.benwiegand.projection.geargrinder.proto.data.readable.input.event.TouchEvent;
import io.benwiegand.projection.geargrinder.service.GeargrinderServiceConnector;
import io.benwiegand.projection.libprivd.IPrivd;

public class ProjectionService implements InputEventConverter.ConvertedInputEventListener, IPCConnectionListener, GeargrinderServiceConnector.ConnectionListener {
    private static final String TAG = ProjectionService.class.getSimpleName();

    private static final String VIRTUAL_DISPLAY_NAME = "Geargrinder projection";

    // uses system/protected flags
    private static final int PRIVD_VIRTUAL_DISPLAY_FLAGS = DisplayManager.VIRTUAL_DISPLAY_FLAG_SECURE
            | DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
            | PrivdVirtualDisplayProxy.FLAG_CAN_SHOW_WITH_INSECURE_KEYGUARD
            | PrivdVirtualDisplayProxy.FLAG_SUPPORTS_TOUCH
            | PrivdVirtualDisplayProxy.FLAG_TRUSTED
            | PrivdVirtualDisplayProxy.FLAG_OWN_DISPLAY_GROUP
            | PrivdVirtualDisplayProxy.FLAG_ALWAYS_UNLOCKED
            | PrivdVirtualDisplayProxy.FLAG_OWN_FOCUS;

    // this is all that really can be done
    private static final int LOCAL_VIRTUAL_DISPLAY_FLAGS = DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY | DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION;

    private final Object lock = new Object();

    private final InputEventConverter inputEventConverter;

    private VirtualDisplayController virtualDisplay = null;
    private VideoPreset videoPreset = VideoPreset.getDefault();
    private Surface surface = null;

    public GeargrinderServiceConnector connector;
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

        inputEventConverter = new InputEventConverter(InputChannelMeta.getDefault(), this, 0, videoPreset.width(), videoPreset.height());

        connector = new GeargrinderServiceConnector(TAG, context, this);
        connector.bindAccessibilityService();
        connector.bindProjectionActivity();
        connector.bindPrivdService(BIND_AUTO_CREATE | BIND_IMPORTANT);
    }

    public void destroy() {
        connector.destroy();
        if (virtualDisplay != null)
            virtualDisplay.release();
    }

    public void setOutput(Surface surface, VideoPreset videoPreset) {
        synchronized (lock) {
            Log.i(TAG, "attaching new output");
            if (!this.videoPreset.equals(videoPreset)) {
                Log.d(TAG, "resizing to " + videoPreset.width() + " x " + videoPreset.height() + ", dpi = " + videoPreset.density());

                if (virtualDisplay != null)
                    virtualDisplay.resize(videoPreset.width(), videoPreset.height(), videoPreset.density());

                inputEventConverter.setTargetDisplaySize(videoPreset.width(), videoPreset.height());
                connector.getProjectionBinder()
                        .ifPresent(binder -> binder.setMargins(videoPreset.marginHorizontal(), videoPreset.marginVertical()));

                this.videoPreset = videoPreset;
            }

            if (virtualDisplay != null)
                virtualDisplay.setSurface(surface);

            this.surface = surface;
        }
    }

    public void setInput(InputChannel inputChannel) {
        inputEventConverter.setInputMeta(inputChannel.getMetadata());
        inputChannel.setInputEventListener((event, translator) ->
                inputEventConverter.onTouchEvent(event, translator.chain(projectionTouchCoordinateTranslator)));
    }

    @Override
    public void onInputEvent(InputEvent event, int displayId, boolean displayIdSet) {
        if (virtualDisplay == null) return;
        try {
            boolean result = displayIdSet ? privd.injectInputEvent(event) : privd.injectInputEvent(event, displayId);
            if (!result) Log.w(TAG, "motion event result is false");
        } catch (Throwable t) {
            Log.e(TAG, "failed to inject motion event", t);
        }
    }

    @Override
    public void onProjectionActivityConnected(ProjectionActivity.ActivityBinder binder) {
        binder.setMargins(videoPreset.marginHorizontal(), videoPreset.marginVertical());
    }

    @Override
    public void onPrivdServiceConnected(PrivdService.ServiceBinder binder) {
        binder.requestDaemon(this);
    }

    @Override
    public void onPrivdConnected(IPrivd privd) {
        Log.i(TAG, "starting projection");
        this.privd = privd;

        synchronized (lock) {
            assert virtualDisplay == null;

            try {
                Log.d(TAG, "creating virtual display via privd");
                virtualDisplay = new PrivdVirtualDisplayProxy(
                        privd, VIRTUAL_DISPLAY_NAME,
                        videoPreset.width(), videoPreset.height(), videoPreset.density(),
                        surface, PRIVD_VIRTUAL_DISPLAY_FLAGS
                );
            } catch (Throwable t) {
                Log.e(TAG, "failed to create virtual display via privd", t);

                // this is less consequential for ProjectionService
                Log.w(TAG, "falling back to local virtual display");
                DisplayManager dm = context.getSystemService(DisplayManager.class);
                virtualDisplay = new LocalVirtualDisplayController(
                        dm, VIRTUAL_DISPLAY_NAME,
                        videoPreset.width(), videoPreset.height(), videoPreset.density(),
                        surface, LOCAL_VIRTUAL_DISPLAY_FLAGS
                );
            }

            inputEventConverter.setTargetDisplayId(virtualDisplay.getDisplayId());
        }

        try {
            Log.d(TAG, "launching projection activity");
            privd.launchActivity(
                    new ComponentName(context, ProjectionActivity.class),
                    virtualDisplay.getDisplayId()
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

}
