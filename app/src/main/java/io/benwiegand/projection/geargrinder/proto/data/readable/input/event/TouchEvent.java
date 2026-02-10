package io.benwiegand.projection.geargrinder.proto.data.readable.input.event;

import android.util.Base64;
import android.util.Log;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import io.benwiegand.projection.geargrinder.proto.ProtoParser;

public record TouchEvent(
        PointerLocation[] pointerLocations,
        int actionPointerIndex,     // similar to MotionEvent.getActionIndex()
        Action action
) {
    private static final String TAG = TouchEvent.class.getSimpleName();

    public record PointerLocation(int x, int y, int pointerIndex) {
        public int xScaled(int srcWidth, int dstWidth) {
            return (dstWidth * x) / srcWidth;
        }

        public int yScaled(int srcHeight, int dstHeight) {
            return (dstHeight * y) / srcHeight;
        }

        public static PointerLocation parse(byte[] buffer, int offset, int length) {
            try {
                Map<Integer, List<ProtoParser.ProtoField>> fields = ProtoParser.parse(buffer, offset, length);

                return new PointerLocation(
                        ProtoParser.getSingleUnsignedInteger32(buffer, fields.get(1), 0),
                        ProtoParser.getSingleUnsignedInteger32(buffer, fields.get(2), 0),
                        ProtoParser.getSingleUnsignedInteger32(buffer, fields.get(3), 0)
                );

            } catch (Throwable t) {
                Log.wtf(TAG, "failed to parse PointerLocation: " + Base64.encodeToString(buffer, offset, length, 0), t);
                return null;
            }
        }
    }

    public enum Action {
        // appears to match MotionEvent actions

        DOWN,
        UP,
        MOVE,
        CANCEL,
        OUTSIDE,
        // multitouch
        POINTER_DOWN,
        POINTER_UP;

        private static Action parse(int value) {
            if (value < 0) return null;
            if (value >= values().length) return null;
            return values()[value];
        }
    }

    public static TouchEvent parse(byte[] buffer, int offset, int length) {
        try {
            Map<Integer, List<ProtoParser.ProtoField>> fields = ProtoParser.parse(buffer, offset, length);

            List<ProtoParser.ProtoField> pointerLocationFields = fields.get(1);
            PointerLocation[] pointerLocations = new PointerLocation[pointerLocationFields == null ? 0 : pointerLocationFields.size()];
            if (pointerLocationFields != null) {
                int i = 0;
                for (ProtoParser.ProtoField pointerLocationField : pointerLocationFields) {
                    if (pointerLocationField instanceof ProtoParser.ProtoVarData vd) {
                        pointerLocations[i++] = PointerLocation.parse(buffer, vd.offset(), vd.length());
                    } else {
                        throw new AssertionError("expected var data for pointer location, got " + pointerLocationField.getClass().getSimpleName());
                    }
                }
            }

            return new TouchEvent(
                    pointerLocations,
                    ProtoParser.getSingleUnsignedInteger32(buffer, fields.get(2), 0),
                    Action.parse(ProtoParser.getSingleUnsignedInteger32(buffer, fields.get(3), 0))
            );

        } catch (Throwable t) {
            Log.wtf(TAG, "failed to parse TouchEvent: " + Base64.encodeToString(buffer, offset, length, 0), t);
            return null;
        }
    }

    @Override
    public String toString() {
        return "TouchEvent{" +
                "pointerLocations=" + Arrays.toString(pointerLocations) +
                ", action=" + action +
                '}';
    }
}
