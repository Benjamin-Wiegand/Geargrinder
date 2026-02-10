package io.benwiegand.projection.geargrinder.proto.data.readable.input;

import android.util.Base64;
import android.util.Log;

import java.util.List;
import java.util.Map;

import io.benwiegand.projection.geargrinder.proto.ProtoParser;

public record TouchInputMeta(
        int width,
        int height
        // TODO: field 3 unknown. (multitouch?)
) {
    private static final String TAG = TouchInputMeta.class.getSimpleName();

    public static TouchInputMeta parse(byte[] buffer, int offset, int length) {
        try {
            Map<Integer, List<ProtoParser.ProtoField>> fields = ProtoParser.parse(buffer, offset, length);

            int width = ProtoParser.getSingleUnsignedInteger32(buffer, fields.get(1), -1);
            int height = ProtoParser.getSingleUnsignedInteger32(buffer, fields.get(2), -1);

            return new TouchInputMeta(
                    width,
                    height
            );

        } catch (Throwable t) {
            Log.wtf(TAG, "failed to parse TouchInputMeta: " + Base64.encodeToString(buffer, offset, length, 0), t);
            return null;
        }
    }
}
