package io.benwiegand.projection.geargrinder.proto.data.readable.sensor;

import android.util.Base64;
import android.util.Log;

import java.util.List;
import java.util.Map;

import io.benwiegand.projection.geargrinder.proto.ProtoParser;

public record SensorMeta(
        Type type
) {
    private static final String TAG = SensorMeta.class.getSimpleName();

    public enum Type {
        NONE,
        LOCATION,
        COMPASS,
        CAR_SPEED,
        RPM,
        ODOMETER,
        FUEL_LEVEL,
        PARKING_BRAKE,
        GEAR,
        DIAGNOSTICS,
        NIGHT_DATA,
        ENVIRONMENT,
        HVAC,
        DRIVING_STATUS,
        DEAD_RECONING,
        PASSENGER,
        DOOR,
        LIGHT,
        TIRE,
        ACCEL,
        GYRO,
        GPS;

        public int serialize() {
            return this.ordinal();
        }

        public static Type parse(int value) {
            if (value < 0) return NONE;
            if (value >= values().length) return NONE;
            return values()[value];
        }
    }

    public static SensorMeta parse(byte[] buffer, int offset, int length) {
        try {
            Map<Integer, List<ProtoParser.ProtoField>> fields = ProtoParser.parse(buffer, offset, length);

            return new SensorMeta(
                    Type.parse(ProtoParser.getSingleUnsignedInteger32(buffer, fields.get(1), 0))
            );

        } catch (Throwable t) {
            Log.wtf(TAG, "failed to parse SensorMeta: " + Base64.encodeToString(buffer, offset, length, 0), t);
            return null;
        }
    }

}
