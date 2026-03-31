package io.benwiegand.projection.geargrinder.proto.data.enums;

public enum VideoFocusType {
    UNKNOWN,
    FOCUSED,
    UNFOCUSED;

    public static VideoFocusType parse(int value) {
        if (value < 0) return UNKNOWN;
        if (value >= values().length) return UNKNOWN;
        return values()[value];
    }
}
