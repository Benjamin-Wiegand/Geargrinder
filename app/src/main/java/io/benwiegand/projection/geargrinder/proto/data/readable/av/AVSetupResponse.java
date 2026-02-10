package io.benwiegand.projection.geargrinder.proto.data.readable.av;

import android.util.Base64;
import android.util.Log;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import io.benwiegand.projection.geargrinder.proto.ProtoParser;

public record AVSetupResponse(
        Status status,
        int maxOutstandingAck,
        int[] acceptedPresets
) {
    private static final String TAG = AVSetupResponse.class.getSimpleName();

    public enum Status {
        UNKNOWN,
        NO_ERROR,
        ERROR,
        OK;

        private static Status parse(int value) {
            return switch (value) {
                case 0 -> NO_ERROR;
                case 1 -> ERROR;
                case 2 -> OK;
                default -> UNKNOWN;
            };
        }
    }

    public static AVSetupResponse parse(byte[] buffer, int offset, int length) {
        try {
            Map<Integer, List<ProtoParser.ProtoField>> fields = ProtoParser.parse(buffer, offset, length);

            return new AVSetupResponse(
                    Status.parse(ProtoParser.getSingleUnsignedInteger32(buffer, fields.get(1), -1)),
                    ProtoParser.getSingleUnsignedInteger32(buffer, fields.get(2), 1),
                    ProtoParser.getUnsignedInteger32Array(buffer, fields.get(3))
            );

        } catch (Throwable t) {
            Log.wtf(TAG, "failed to parse AVSetupResponse: " + Base64.encodeToString(buffer, offset, length, 0), t);
            return null;
        }
    }

    @Override
    public String toString() {
        return "AVSetupResponse{" +
                "status=" + status +
                ", maxOutstandingAck=" + maxOutstandingAck +
                ", acceptedPresets=" + Arrays.toString(acceptedPresets) +
                '}';
    }
}
