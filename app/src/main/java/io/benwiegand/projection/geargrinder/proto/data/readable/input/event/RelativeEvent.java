package io.benwiegand.projection.geargrinder.proto.data.readable.input.event;

import android.util.Base64;
import android.util.Log;

import java.util.List;
import java.util.Map;

import io.benwiegand.projection.geargrinder.proto.ProtoParser;

public record RelativeEvent(
        long code,
        int delta
) {
    private static final String TAG = RelativeEvent.class.getSimpleName();

    public static RelativeEvent parse(byte[] buffer, int offset, int length) {
        try {
            Map<Integer, List<ProtoParser.ProtoField>> fields = ProtoParser.parse(buffer, offset, length);

            return new RelativeEvent(
                    ProtoParser.getSingleUnsignedInteger(buffer, fields.get(1), -1),
                    ProtoParser.getSingleInteger32(buffer, fields.get(2), 0)
            );

        } catch (Throwable t) {
            Log.wtf(TAG, "failed to parse RelativeEvent: " + Base64.encodeToString(buffer, offset, length, 0), t);
            return null;
        }
    }

    public static RelativeEvent[] parseAll(byte[] buffer, int offset, int length) {
        try {
            Map<Integer, List<ProtoParser.ProtoField>> fields = ProtoParser.parse(buffer, offset, length);

            List<ProtoParser.ProtoField> relativeEventFields = fields.get(1);
            RelativeEvent[] relativeEvents = new RelativeEvent[relativeEventFields == null ? 0 : relativeEventFields.size()];
            if (relativeEventFields != null) {
                int i = 0;
                for (ProtoParser.ProtoField relativeEventField : relativeEventFields) {
                    if (relativeEventField instanceof ProtoParser.ProtoVarData vd) {
                        relativeEvents[i++] = RelativeEvent.parse(buffer, vd.offset(), vd.length());
                    } else {
                        throw new AssertionError("expected var data for audio preset");
                    }
                }
            }

            return relativeEvents;

        } catch (Throwable t) {
            Log.wtf(TAG, "failed to parse RelativeEvent array: " + Base64.encodeToString(buffer, offset, length, 0), t);
            return null;
        }
    }
}
