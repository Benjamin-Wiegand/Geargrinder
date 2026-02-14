package io.benwiegand.projection.libprivd.data;

import static android.view.MotionEvent.AXIS_RELATIVE_X;
import static android.view.MotionEvent.AXIS_RELATIVE_Y;

import android.os.Build;
import android.view.MotionEvent;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;

import io.benwiegand.projection.libprivd.reflected.ReflectedInputEvent;
import io.benwiegand.projection.libprivd.reflection.ReflectionException;

public class SerializableMotionEvent implements Serializable {
    private final long downTime;
    private final long eventTime;
    private final int action;
    private final int pointerCount;
    private final PointerProperties[] pointerProperties;
    private final PointerCoords[] pointerCoords;
    private final int metaState;
    private final int buttonState;
    private final float xPrecision;
    private final float yPrecision;
    private final int deviceId;
    private final int edgeFlags;
    private final int source;
    private final int displayId;
    private final int flags;
    private final int classification;

    public SerializableMotionEvent(long downTime, long eventTime, int action, int pointerCount, PointerProperties[] pointerProperties, PointerCoords[] pointerCoords, int metaState, int buttonState, float xPrecision, float yPrecision, int deviceId, int edgeFlags, int source, int displayId, int flags, int classification) {
        this.downTime = downTime;
        this.eventTime = eventTime;
        this.action = action;
        this.pointerCount = pointerCount;
        this.pointerProperties = pointerProperties;
        this.pointerCoords = pointerCoords;
        this.metaState = metaState;
        this.buttonState = buttonState;
        this.xPrecision = xPrecision;
        this.yPrecision = yPrecision;
        this.deviceId = deviceId;
        this.edgeFlags = edgeFlags;
        this.source = source;
        this.displayId = displayId;
        this.flags = flags;
        this.classification = classification;
    }

    public static class PointerProperties implements Serializable {
        private final int id;
        private final int toolType;

        public PointerProperties(int id, int toolType) {
            this.id = id;
            this.toolType = toolType;
        }

        public MotionEvent.PointerProperties toMotionEventPointerProperties() {
            MotionEvent.PointerProperties pp = new MotionEvent.PointerProperties();
            pp.id = id;
            pp.toolType = toolType;
            return pp;
        }

        public static MotionEvent.PointerProperties[] toMotionEventPointerPropertiesArray(PointerProperties... pointerProperties) {
            MotionEvent.PointerProperties[] pps = new MotionEvent.PointerProperties[pointerProperties.length];
            for (int i = 0; i < pointerProperties.length; i++) {
                pps[i] = pointerProperties[i].toMotionEventPointerProperties();
            }
            return pps;
        }

        public static PointerProperties fromMotionEventPointerProperties(MotionEvent.PointerProperties pp) {
            return new PointerProperties(pp.id, pp.toolType);
        }

        public static PointerProperties[] fromMotionEvent(MotionEvent event) {
            MotionEvent.PointerProperties pp = new MotionEvent.PointerProperties();
            PointerProperties[] pps = new PointerProperties[event.getPointerCount()];
            for (int i = 0; i < event.getPointerCount(); i++) {
                event.getPointerProperties(i, pp);
                pps[i] = fromMotionEventPointerProperties(pp);
            }
            return pps;
        }
    }

    public static class PointerCoords implements Serializable {
        private final float x;
        private final float y;
        private final float pressure;
        private final float size;
        private final float touchMajor;
        private final float touchMinor;
        private final float toolMajor;
        private final float toolMinor;
        private final float orientation;
        private final float relativeX;
        private final float relativeY;

        public PointerCoords(float x, float y, float pressure, float size, float touchMajor, float touchMinor, float toolMajor, float toolMinor, float orientation, float relativeX, float relativeY) {
            this.x = x;
            this.y = y;
            this.pressure = pressure;
            this.size = size;
            this.touchMajor = touchMajor;
            this.touchMinor = touchMinor;
            this.toolMajor = toolMajor;
            this.toolMinor = toolMinor;
            this.orientation = orientation;
            this.relativeX = relativeX;
            this.relativeY = relativeY;
        }

        public MotionEvent.PointerCoords toMotionEventPointerCoords() {
            MotionEvent.PointerCoords pc = new MotionEvent.PointerCoords();
            pc.x = x;
            pc.y = y;
            pc.pressure = pressure;
            pc.size = size;
            pc.touchMajor = touchMajor;
            pc.touchMinor = touchMinor;
            pc.toolMajor = toolMajor;
            pc.toolMinor = toolMinor;
            pc.orientation = orientation;
            pc.setAxisValue(AXIS_RELATIVE_X, relativeX);
            pc.setAxisValue(AXIS_RELATIVE_Y, relativeY);
            return pc;
        }

        public static MotionEvent.PointerCoords[] toMotionEventPointerCoordsArray(PointerCoords... pointerCoords) {
            MotionEvent.PointerCoords[] pcs = new MotionEvent.PointerCoords[pointerCoords.length];
            for (int i = 0; i < pointerCoords.length; i++) {
                pcs[i] = pointerCoords[i].toMotionEventPointerCoords();
            }
            return pcs;
        }

        public static PointerCoords fromMotionEventPointerCoords(MotionEvent.PointerCoords pc) {
            return new PointerCoords(
                    pc.x,
                    pc.y,
                    pc.pressure,
                    pc.size,
                    pc.touchMajor,
                    pc.touchMinor,
                    pc.toolMajor,
                    pc.toolMinor,
                    pc.orientation,
                    pc.getAxisValue(AXIS_RELATIVE_X),
                    pc.getAxisValue(AXIS_RELATIVE_Y)
            );
        }

        public static PointerCoords[] fromMotionEvent(MotionEvent event) {
            MotionEvent.PointerCoords pc = new MotionEvent.PointerCoords();
            PointerCoords[] pcs = new PointerCoords[event.getPointerCount()];
            for (int i = 0; i < event.getPointerCount(); i++) {
                event.getPointerCoords(i, pc);
                pcs[i] = fromMotionEventPointerCoords(pc);
            }
            return pcs;
        }
    }

    public MotionEvent toMotionEvent() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return MotionEvent.obtain(
                    downTime, eventTime, action, pointerCount,
                    PointerProperties.toMotionEventPointerPropertiesArray(pointerProperties),
                    PointerCoords.toMotionEventPointerCoordsArray(pointerCoords),
                    metaState, buttonState, xPrecision, yPrecision,
                    deviceId, edgeFlags, source, displayId,
                    flags, classification
            );
        }

        MotionEvent event = MotionEvent.obtain(
                downTime, eventTime, action, pointerCount,
                PointerProperties.toMotionEventPointerPropertiesArray(pointerProperties),
                PointerCoords.toMotionEventPointerCoordsArray(pointerCoords),
                metaState, buttonState, xPrecision, yPrecision,
                deviceId, edgeFlags, source, flags
        );

        if (displayId == 0) return event;

        try {
            ReflectedInputEvent reflectedEvent = new ReflectedInputEvent(event);
            reflectedEvent.setDisplayId(displayId);
            return event;
        } catch (ReflectionException e) {
            throw new RuntimeException("failed to set displayId on reflected InputEvent", e);
        }
    }

    public static SerializableMotionEvent fromMotionEvent(MotionEvent event, int displayId) {
        int classification = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? event.getClassification() : 0 /* CLASSIFICATION_NONE */;
        return new SerializableMotionEvent(
                event.getDownTime(),
                event.getEventTime(),
                event.getAction(),
                event.getPointerCount(),
                PointerProperties.fromMotionEvent(event),
                PointerCoords.fromMotionEvent(event),
                event.getMetaState(),
                event.getButtonState(),
                event.getXPrecision(),
                event.getYPrecision(),
                event.getDeviceId(),
                event.getEdgeFlags(),
                event.getSource(),
                displayId,
                event.getFlags(),
                classification
        );
    }

    public static SerializableMotionEvent fromByteArray(byte[] data, int offset, int length) {
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(data, offset, length))) {
            return (SerializableMotionEvent) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new AssertionError(e);
        }
    }
}
