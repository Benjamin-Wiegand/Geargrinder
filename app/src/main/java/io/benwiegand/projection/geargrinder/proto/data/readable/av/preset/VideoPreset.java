package io.benwiegand.projection.geargrinder.proto.data.readable.av.preset;

import android.util.Base64;
import android.util.Log;

import java.util.List;
import java.util.Map;

import io.benwiegand.projection.geargrinder.proto.ProtoParser;

public record VideoPreset(
    Resolution resolution,
    RefreshRate refreshRate,
    int marginHorizontal,
    int marginVertical,
    int density
) {
    private static final String TAG = VideoPreset.class.getSimpleName();
    public static final int DEFAULT_RESOLUTION_WIDTH = 800;
    public static final int DEFAULT_RESOLUTION_HEIGHT = 480;
    public static final int DEFAULT_DENSITY_DPI = 200;
    public static final int DEFAULT_REFRESH_RATE_HZ = 24;

    public enum Resolution {
        UNKNOWN,
        RES_480P,
        RES_720P,
        RES_1080P;

        public int width() {
            return switch (this) {
                case UNKNOWN -> DEFAULT_RESOLUTION_WIDTH;
                case RES_480P -> 800;
                case RES_720P -> 1280;
                case RES_1080P -> 1920;
            };
        }

        public int height() {
            return switch (this) {
                case UNKNOWN -> DEFAULT_RESOLUTION_HEIGHT;
                case RES_480P -> 480;
                case RES_720P -> 720;
                case RES_1080P -> 1080;
            };
        }

        private static Resolution parse(int value) {
            if (value < 0) return UNKNOWN;
            if (value >= values().length) return UNKNOWN;
            return values()[value];
        }
    }

    public enum RefreshRate {
        UNKNOWN,
        RATE_30HZ,
        RATE_60HZ;

        public int hz() {
            return switch (this) {
                case UNKNOWN -> DEFAULT_REFRESH_RATE_HZ;
                case RATE_30HZ -> 30;
                case RATE_60HZ -> 60;
            };
        }

        private static RefreshRate parse(int value) {
            if (value < 0) return UNKNOWN;
            if (value >= values().length) return UNKNOWN;
            return values()[value];
        }
    }

    public int width() {
        return resolution().width();
    }

    public int height() {
        return resolution().height();
    }

    public static VideoPreset getDefault() {
        return new VideoPreset(
                Resolution.UNKNOWN,
                RefreshRate.UNKNOWN,
                0, 0,
                DEFAULT_DENSITY_DPI
        );
    }

    public static VideoPreset parse(byte[] buffer, int offset, int length) {
        try {
            Map<Integer, List<ProtoParser.ProtoField>> fields = ProtoParser.parse(buffer, offset, length);

            return new VideoPreset(
                    Resolution.parse(ProtoParser.getSingleUnsignedInteger32(buffer, fields.get(1), -1)),
                    RefreshRate.parse(ProtoParser.getSingleUnsignedInteger32(buffer, fields.get(2), -1)),
                    ProtoParser.getSingleUnsignedInteger32(buffer, fields.get(3), 0),
                    ProtoParser.getSingleUnsignedInteger32(buffer, fields.get(4), 0),
                    ProtoParser.getSingleUnsignedInteger32(buffer, fields.get(5), DEFAULT_DENSITY_DPI)
            );

        } catch (Throwable t) {
            Log.wtf(TAG, "failed to parse VideoPreset: " + Base64.encodeToString(buffer, offset, length, 0), t);
            return null;
        }
    }

}
