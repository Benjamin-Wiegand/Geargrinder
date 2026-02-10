package io.benwiegand.projection.geargrinder.proto.data.readable;

import android.util.Base64;
import android.util.Log;

import java.util.List;
import java.util.Map;

import io.benwiegand.projection.geargrinder.proto.ProtoParser;

public record ChannelOpenResponse(
        Status status
) {
    private static final String TAG = ChannelOpenResponse.class.getSimpleName();

    public enum Status {
        UNKNOWN,
        OK,
        ERROR;

        private static Status parse(int value) {
            return switch (value) {
                case 0 -> OK;
                case 1 -> ERROR;
                default -> UNKNOWN;
            };
        }
    }

    public static ChannelOpenResponse parse(byte[] buffer, int offset, int length) {
        try {
            Map<Integer, List<ProtoParser.ProtoField>> fields = ProtoParser.parse(buffer, offset, length);

            return new ChannelOpenResponse(
                    Status.parse(ProtoParser.getSingleUnsignedInteger32(buffer, fields.get(1), -1))
            );

        } catch (Throwable t) {
            Log.wtf(TAG, "failed to parse ChannelOpenResponse: " + Base64.encodeToString(buffer, offset, length, 0), t);
            return null;
        }
    }

}
