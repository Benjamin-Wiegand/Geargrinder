package io.benwiegand.projection.geargrinder.coordinate;

import android.os.Build;
import android.os.SystemClock;
import android.util.Log;
import android.view.InputDevice;
import android.view.MotionEvent;

import java.util.HashMap;
import java.util.Map;

import io.benwiegand.projection.geargrinder.callback.InputEventListener;
import io.benwiegand.projection.geargrinder.proto.data.readable.input.InputChannelMeta;
import io.benwiegand.projection.geargrinder.proto.data.readable.input.event.TouchEvent;
import io.benwiegand.projection.libprivd.data.SerializableMotionEvent;

/**
 * converts {@link io.benwiegand.projection.geargrinder.proto.data.readable.input.event.TouchEvent} objects to {@link io.benwiegand.projection.libprivd.data.SerializableMotionEvent}.
 * a whole class is needed for this since some values need to be calculated based on previous ones.
 */
public class InputEventConverter implements InputEventListener {
    private static final String TAG = InputEventConverter.class.getSimpleName();


    private static final float TOUCH_PRESSURE = 1;      // normal pressure
    private static final float TOUCH_SIZE = 0.5f;       // on a scale of 0-1
    private static final float TOUCH_ORIENTATION = 0;   // 0 radians

    private static final int TOUCH_TOOL_TYPE = MotionEvent.TOOL_TYPE_FINGER;
    private static final int TOUCH_SOURCE = InputDevice.SOURCE_TOUCHSCREEN;
    private static final int TOUCH_DEVICE_ID = 0;   // not from a device

    // not supported
    private static final int DEFAULT_TOUCH_CLASSIFICATION = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? MotionEvent.CLASSIFICATION_NONE : 0;
    private static final int DEFAULT_TOUCH_FLAGS = 0;
    private static final int DEFAULT_TOUCH_EDGE_FLAGS = 0;
    private static final float DEFAULT_TOUCH_TOOL_MAJOR = 0;
    private static final float DEFAULT_TOUCH_TOOL_MINOR = 0;


    private final Map<Integer, TouchEvent.PointerLocation> mostRecentPointerLocations = new HashMap<>(10);

    private final ConvertedInputEventListener listener;
    private final int targetDisplayId;

    private InputChannelMeta inputMeta;
    private int displayWidth;
    private int displayHeight;
    private float xTouchPrecision;
    private float yTouchPrecision;

    private long downTime = 0;


    public InputEventConverter(InputChannelMeta inputMeta, ConvertedInputEventListener listener, int targetDisplayId, int displayWidth, int displayHeight) {
        this.inputMeta = inputMeta;
        this.listener = listener;
        this.targetDisplayId = targetDisplayId;
        this.displayWidth = displayWidth;
        this.displayHeight = displayHeight;

        updateTouchPrecision();
    }

    public interface ConvertedInputEventListener {
        void onMotionEvent(SerializableMotionEvent event);
    }

    public void updateTouchPrecision() {
        if (!inputMeta.hasTouchScreen()) return;
        xTouchPrecision = (float) inputMeta.touchScreenMeta().width() / displayWidth;
        yTouchPrecision = (float) inputMeta.touchScreenMeta().height() / displayHeight;
        Log.d(TAG, "new touch screen precision: " + xTouchPrecision + " x " + yTouchPrecision);
    }

    public void setTargetDisplaySize(int width, int height) {
        displayWidth = width;
        displayHeight = height;
        updateTouchPrecision();
    }

    public void setInputMeta(InputChannelMeta inputMeta) {
        this.inputMeta = inputMeta;
        updateTouchPrecision();
    }

    @Override
    public void onTouchEvent(TouchEvent event, CoordinateTranslator<TouchEvent.PointerLocation> translator) {
        long timestamp = SystemClock.uptimeMillis();

        if (event.action() == TouchEvent.Action.DOWN) {
            downTime = timestamp;
            mostRecentPointerLocations.clear();
        }

        SerializableMotionEvent.PointerProperties[] pps = new SerializableMotionEvent.PointerProperties[event.pointerCount()];
        SerializableMotionEvent.PointerCoords[] pcs = new SerializableMotionEvent.PointerCoords[event.pointerCount()];

        for (int i = 0; i < event.pointerCount(); i++) {
            TouchEvent.PointerLocation pl = event.pointerLocations()[i];
            TouchEvent.PointerLocation plPrev = mostRecentPointerLocations.getOrDefault(pl.pointerIndex(), pl);
            int x = translator.translateX(pl), y = translator.translateY(pl);
            int xPrev = translator.translateX(plPrev), yPrev = translator.translateY(plPrev);

            pps[i] = new SerializableMotionEvent.PointerProperties(pl.pointerIndex(), TOUCH_TOOL_TYPE);
            pcs[i] = new SerializableMotionEvent.PointerCoords(
                    x, y,
                    TOUCH_PRESSURE,
                    TOUCH_SIZE,
                    DEFAULT_TOUCH_TOOL_MAJOR,
                    DEFAULT_TOUCH_TOOL_MINOR,
                    DEFAULT_TOUCH_TOOL_MAJOR,
                    DEFAULT_TOUCH_TOOL_MINOR,
                    TOUCH_ORIENTATION,
                    x - xPrev, y - yPrev
            );

            mostRecentPointerLocations.put(pl.pointerIndex(), pl);
        }

        SerializableMotionEvent convertedEvent = new SerializableMotionEvent(
                downTime,
                timestamp,
                event.actionInt(),
                event.pointerCount(),
                pps,
                pcs,
                0, 0,  // touch screen doesn't have buttons
                xTouchPrecision,
                yTouchPrecision,
                TOUCH_DEVICE_ID,
                DEFAULT_TOUCH_EDGE_FLAGS,
                TOUCH_SOURCE,
                targetDisplayId,
                DEFAULT_TOUCH_FLAGS,
                DEFAULT_TOUCH_CLASSIFICATION
        );

        listener.onMotionEvent(convertedEvent);
    }
}
