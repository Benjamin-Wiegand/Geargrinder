package io.benwiegand.projection.geargrinder.projection.input;

import android.os.Build;
import android.os.SystemClock;
import android.util.Log;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;

import java.util.HashMap;
import java.util.Map;

import io.benwiegand.projection.geargrinder.callback.InputEventListener;
import io.benwiegand.projection.geargrinder.proto.data.constants.SpecialKeyCodes;
import io.benwiegand.projection.geargrinder.proto.data.readable.input.InputChannelMeta;
import io.benwiegand.projection.geargrinder.proto.data.readable.input.event.ButtonEvent;
import io.benwiegand.projection.geargrinder.proto.data.readable.input.event.RelativeEvent;
import io.benwiegand.projection.geargrinder.proto.data.readable.input.event.TouchEvent;

/**
 * converts {@link TouchEvent} objects to {@link InputEvent}.
 * a whole class is needed for this since some values need to be calculated based on previous ones.
 */
public class InputEventConverter implements InputEventListener {
    private static final String TAG = InputEventConverter.class.getSimpleName();


    private static final float TOUCH_PRESSURE = 1;      // normal pressure
    private static final float TOUCH_SIZE = 1f;         // on a scale of 0-1

    private static final int TOUCH_TOOL_TYPE = MotionEvent.TOOL_TYPE_FINGER;
    private static final int TOUCH_SOURCE = InputDevice.SOURCE_TOUCHSCREEN;
    private static final int TOUCH_DEVICE_ID = 0;   // not from a device

    // not supported
    private static final int DEFAULT_TOUCH_FLAGS = 0;
    private static final int DEFAULT_TOUCH_EDGE_FLAGS = 0;


    private final Map<Integer, TouchEvent.PointerLocation> mostRecentPointerLocations = new HashMap<>(10);

    private final ConvertedInputEventListener listener;

    private final CoordinateTranslator<TouchEvent.PointerLocation> coordinateTranslator;

    private InputChannelMeta inputMeta;
    private int targetDisplayId;
    private int displayWidth;
    private int displayHeight;
    private float xTouchPrecision;
    private float yTouchPrecision;

    private long touchDownTime = 0;

    private final Map<Integer, Long> keyDownTimes = new HashMap<>();


    public InputEventConverter(InputChannelMeta inputMeta, ConvertedInputEventListener listener, CoordinateTranslator<TouchEvent.PointerLocation> coordinateTranslator, int targetDisplayId, int displayWidth, int displayHeight) {
        this.inputMeta = inputMeta;
        this.listener = listener;
        this.coordinateTranslator = coordinateTranslator;
        this.targetDisplayId = targetDisplayId;
        this.displayWidth = displayWidth;
        this.displayHeight = displayHeight;

        updateTouchPrecision();
    }

    public interface ConvertedInputEventListener {
        void onInputEvent(InputEvent event, int displayId, boolean displayIdSet);
    }

    public void setTargetDisplayId(int targetDisplayId) {
        this.targetDisplayId = targetDisplayId;
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
    public void onTouchEvent(TouchEvent event) {
        long timestamp = SystemClock.uptimeMillis();

        if (event.action() == TouchEvent.Action.DOWN) {
            touchDownTime = timestamp;
            mostRecentPointerLocations.clear();
        }

        MotionEvent.PointerProperties[] pps = new MotionEvent.PointerProperties[event.pointerCount()];
        MotionEvent.PointerCoords[] pcs = new MotionEvent.PointerCoords[event.pointerCount()];

        for (int i = 0; i < event.pointerCount(); i++) {
            TouchEvent.PointerLocation pl = event.pointerLocations()[i];
            TouchEvent.PointerLocation plPrev = mostRecentPointerLocations.getOrDefault(pl.pointerIndex(), pl);
            int x = coordinateTranslator.translateX(pl), y = coordinateTranslator.translateY(pl);
            int xPrev = coordinateTranslator.translateX(plPrev), yPrev = coordinateTranslator.translateY(plPrev);

            MotionEvent.PointerProperties pp = new MotionEvent.PointerProperties();
            pp.clear();
            pp.id = pl.pointerIndex();
            pp.toolType = TOUCH_TOOL_TYPE;

            MotionEvent.PointerCoords pc = new MotionEvent.PointerCoords();
            pc.clear();
            pc.x = x;
            pc.y = y;
            pc.pressure = TOUCH_PRESSURE;
            pc.size = TOUCH_SIZE;
            pc.setAxisValue(MotionEvent.AXIS_RELATIVE_X, x - xPrev);
            pc.setAxisValue(MotionEvent.AXIS_RELATIVE_Y, y - yPrev);

            pps[i] = pp;
            pcs[i] = pc;
            mostRecentPointerLocations.put(pl.pointerIndex(), pl);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            MotionEvent motionEvent = MotionEvent.obtain(
                    touchDownTime,
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
                    MotionEvent.CLASSIFICATION_NONE
            );

            listener.onInputEvent(motionEvent, targetDisplayId, true);
        } else {
            MotionEvent motionEvent = MotionEvent.obtain(
                    touchDownTime,
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
                    DEFAULT_TOUCH_FLAGS
            );

            listener.onInputEvent(motionEvent, targetDisplayId, false);
        }
    }

    @Override
    public void onButtonEvent(ButtonEvent event) {
        long timestamp = SystemClock.uptimeMillis();

        long downTime;
        if (event.pressed()) {
            keyDownTimes.put(event.code(), timestamp);
            downTime = timestamp;
        } else {
            Long tDownTime = keyDownTimes.get(event.code());
            downTime = tDownTime != null ? tDownTime : timestamp;
        }

        // TODO: repeating key events for arrows
        KeyEvent keyEvent = new KeyEvent(
                downTime,
                timestamp,
                event.pressed() ? KeyEvent.ACTION_DOWN : KeyEvent.ACTION_UP,
                event.code(),
                0
        );

        listener.onInputEvent(keyEvent, targetDisplayId, false);
    }

    @Override
    public void onRelativeEvent(RelativeEvent event) {
        if (event.code() == SpecialKeyCodes.KEYCODE_ROTARY_INPUT) {
            if (event.delta() == 0) return; // no input

            // for now just translate to key events
            int steps = Math.abs(event.delta());
            int keycode = event.delta() > 0 ? KeyEvent.KEYCODE_NAVIGATE_NEXT : KeyEvent.KEYCODE_NAVIGATE_PREVIOUS;
            for (int i = 0; i < steps; i++)
                onButtonEvent(new ButtonEvent(keycode, true));
            onButtonEvent(new ButtonEvent(keycode, false));

        } else {
            Log.e(TAG, "unhandled keycode for relative input: " + event.code());
        }
    }
}
