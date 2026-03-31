package io.benwiegand.projection.geargrinder.proto.data.readable.av;

import android.util.Base64;
import android.util.Log;

import java.util.List;
import java.util.Map;

import io.benwiegand.projection.geargrinder.proto.ProtoParser;
import io.benwiegand.projection.geargrinder.proto.data.enums.VideoFocusType;

public record VideoFocusIndication(
        VideoFocusType focusType,
        boolean notFromUser
) {
    private static final String TAG = VideoFocusIndication.class.getSimpleName();

    public static VideoFocusIndication parse(byte[] buffer, int offset, int length) {
        try {
            Map<Integer, List<ProtoParser.ProtoField>> fields = ProtoParser.parse(buffer, offset, length);
            return new VideoFocusIndication(
                    VideoFocusType.parse(ProtoParser.getSingleUnsignedInteger32(buffer, fields.get(1), 0)),
                    ProtoParser.getSingleBoolean(buffer, fields.get(2), false)
            );
        } catch (Throwable t) {
            Log.wtf(TAG, "failed to parse VideoFocusIndication: " + Base64.encodeToString(buffer, offset, length, 0), t);
            return null;
        }
    }
}
