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
import io.benwiegand.projection.geargrinder.projection.input.CoordinateTranslator;
import io.benwiegand.projection.geargrinder.projection.input.InputEventConverter;
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

    private InputEventConverter inputEventConverter;

    private VirtualDisplayController virtualDisplay = null;
    private VideoPreset videoPreset;
    private Surface surface = null;

    public GeargrinderServiceConnector connector;
    private IPrivd privd = null;

    private final Context context;

    public interface Listener {
        void onProjectionStarted();
        void onProjectionFailed(Throwable t);
    }

    private Listener projectionListener;
    private boolean started = false;
    private boolean inputInit = false;
    private boolean outputInit = false;
    private boolean uiInit = false;
    private boolean virtualDisplayInit = false;

    private boolean dead = false;
    private Throwable error = null;


    public ProjectionService(Context context, Listener projectionListener, VideoPreset videoPreset) {
        this.context = context;
        this.projectionListener = projectionListener;
        this.videoPreset = videoPreset;

        CoordinateTranslator<TouchEvent.PointerLocation> coordinateTranslator = CoordinateTranslator.createTouchEvent(
                x -> x + videoPreset.marginHorizontal() / 2,
                y -> y + videoPreset.marginVertical() / 2
        );
        inputEventConverter = new InputEventConverter(InputChannelMeta.getDefault(), this, coordinateTranslator, 0, videoPreset.width(), videoPreset.height());

        connector = new GeargrinderServiceConnector(TAG, context, this);
        connector.bindAccessibilityService();
        connector.bindProjectionActivity();
        connector.bindPrivdService(BIND_AUTO_CREATE | BIND_IMPORTANT);
    }

    public ProjectionService(Context context, Listener projectionListener) {
        this(context, projectionListener, VideoPreset.getDefault());
    }

    public void destroy() {
        if (dead) return;
        dead = true;
        connector.destroy();
        if (virtualDisplay != null)
            virtualDisplay.release();
    }

    public Throwable getError() {
        return error;
    }

    private void onInitAdvancedLocked() {
        if (started) return;
        if (!inputInit || !outputInit || !uiInit || !virtualDisplayInit) return;
        started = true;

        Log.i(TAG, "init complete");
        projectionListener.onProjectionStarted();
    }

    private void onFailureLocked(String message, Throwable t) {
        if (dead) return;
        if (error != null) {
            Log.w(TAG, "projection already failed, but another failure happened: " + message, t);
            return;
        }
        Log.e(TAG, "projection failure: " + message, t);
        error = new RuntimeException(message, t).fillInStackTrace();
        projectionListener.onProjectionFailed(error);
    }

    public void unsuspend(Listener projectionListener) {
        synchronized (lock) {
            Log.i(TAG, "unsuspending projection");
            assert !dead;
            if (started) Log.w(TAG, "unsuspend() called but projection already unsuspended");

            this.projectionListener = projectionListener;
            if (error != null) projectionListener.onProjectionFailed(error);
            else if (started) projectionListener.onProjectionStarted();
        }
    }

    public void suspend() {
        synchronized (lock) {
            Log.i(TAG, "suspending projection");
            // ui and virtual display stay active so the projection can be resumed
            started = false;
            inputInit = false;
            outputInit = false;

            if (virtualDisplay != null) virtualDisplay.setSurface(null);
        }
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

            outputInit = true;
            onInitAdvancedLocked();
        }
    }

    public void setInput(InputChannel inputChannel) {
        synchronized (lock) {
            inputEventConverter.setInputMeta(inputChannel.getMetadata());
            inputChannel.setInputEventListener(inputEventConverter);

            inputInit = true;
            onInitAdvancedLocked();
        }
    }

    @Override
    public void onInputEvent(InputEvent event, int displayId, boolean displayIdSet) {
        if (!started) return;

        try {
            boolean result = displayIdSet ? privd.injectInputEvent(event) : privd.injectInputEventWithDisplayId(event, displayId);
            if (!result) Log.w(TAG, "motion event result is false");
        } catch (Throwable t) {
            Log.e(TAG, "failed to inject motion event", t);
        }
    }

    @Override
    public void onProjectionActivityConnected(ProjectionActivity.ActivityBinder binder) {
        synchronized (lock) {
            binder.setMargins(videoPreset.marginHorizontal(), videoPreset.marginVertical());

            uiInit = true;
            onInitAdvancedLocked();
        }
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
            if (virtualDisplayInit) return;
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

                try {
                    // this is less consequential for ProjectionService than it is for VirtualActivity
                    Log.w(TAG, "falling back to local virtual display");
                    DisplayManager dm = context.getSystemService(DisplayManager.class);
                    virtualDisplay = new LocalVirtualDisplayController(
                            dm, VIRTUAL_DISPLAY_NAME,
                            videoPreset.width(), videoPreset.height(), videoPreset.density(),
                            surface, LOCAL_VIRTUAL_DISPLAY_FLAGS
                    );
                } catch (Throwable tt) {
                    onFailureLocked("unable to create virtual display through privd or locally", tt);
                    return;
                }
            }

            inputEventConverter.setTargetDisplayId(virtualDisplay.getDisplayId());

            try {
                Log.d(TAG, "launching projection activity");
                privd.launchActivity(
                        new ComponentName(context, ProjectionActivity.class),
                        virtualDisplay.getDisplayId()
                );
            } catch (Throwable t) {
                onFailureLocked("failed to launch projection activity on virtual display via privd", t);
                return;
            }

            virtualDisplayInit = true;
            onInitAdvancedLocked();
        }
    }

    @Override
    public void onPrivdDisconnected() {
        synchronized (lock) {
            onFailureLocked("privd connection lost", null);
        }
    }

    @Override
    public void onPrivdLaunchFailure(Throwable t) {
        synchronized (lock) {
            onFailureLocked("privd failed to launch", t);
        }
    }

}
