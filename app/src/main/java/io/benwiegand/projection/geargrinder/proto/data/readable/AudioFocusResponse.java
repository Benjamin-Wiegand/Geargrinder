package io.benwiegand.projection.geargrinder.proto.data.readable;

import android.util.Base64;
import android.util.Log;

import java.util.List;
import java.util.Map;

import io.benwiegand.projection.geargrinder.proto.ProtoParser;

public record AudioFocusResponse(
        State focusState,
        long unknown     // probably status code
) {
    private static final String TAG = AudioFocusResponse.class.getSimpleName();

    public enum State {
        NONE,
        GAIN,
        GAIN_TRANSIENT,
        LOSS,
        LOSS_TRANSIENT_CAN_DUCK,
        LOSS_TRANSIENT,
        GAIN_MEDIA_ONLY,
        GAIN_TRANSIENT_GUIDANCE_ONLY;

        public static State parse(int value) {
            if (value < 0) return NONE;
            if (value >= values().length) return NONE;
            return values()[value];
        }
    }

    public static AudioFocusResponse parse(byte[] buffer, int offset, int length) {
        try {
            Map<Integer, List<ProtoParser.ProtoField>> fields = ProtoParser.parse(buffer, offset, length);

            return new AudioFocusResponse(
                    State.parse(ProtoParser.getSingleUnsignedInteger32(buffer, fields.get(1), 0)),
                    ProtoParser.getSingleUnsignedInteger(buffer, fields.get(2), 0)
            );

        } catch (Throwable t) {
            Log.wtf(TAG, "failed to parse AudioFocusResponse: " + Base64.encodeToString(buffer, offset, length, 0), t);
            return null;
        }
    }

}
