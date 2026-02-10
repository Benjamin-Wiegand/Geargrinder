package io.benwiegand.projection.geargrinder.proto.data.readable;

import android.util.Base64;
import android.util.Log;

import java.util.List;
import java.util.Map;

import io.benwiegand.projection.geargrinder.proto.ProtoParser;
import io.benwiegand.projection.geargrinder.proto.data.readable.av.AVChannelMeta;
import io.benwiegand.projection.geargrinder.proto.data.readable.input.InputChannelMeta;

public interface ChannelMeta {
    int channelId();

    static ChannelMeta parse(byte[] buffer, int offset, int length) {
        final String TAG = ChannelMeta.class.getSimpleName() + "::parse";

        try {
            Map<Integer, List<ProtoParser.ProtoField>> fields = ProtoParser.parse(buffer, offset, length);

            if (fields.size() < 2) {
                throw new AssertionError("expected at least 2 fields");
            } else if (fields.size() > 2) {
                Log.wtf(TAG, "unexpected extra data in channel meta. dump: " + Base64.encodeToString(buffer, offset, length, 0));
            }

            int channelId = ProtoParser.getSingleUnsignedInteger32(buffer, fields.get(1), -1);
            if (channelId == -1) throw new AssertionError("no channel id in channel meta");

            ProtoParser.ProtoVarData metaField;

            // AV channel meta
            metaField = ProtoParser.getSingle(fields.get(3), ProtoParser.ProtoVarData.class);
            if (metaField != null)
                return AVChannelMeta.parse(channelId, buffer, metaField.offset(), metaField.length());

            // input channel meta
            metaField = ProtoParser.getSingle(fields.get(4), ProtoParser.ProtoVarData.class);
            if (metaField != null)
                return InputChannelMeta.parse(channelId, buffer, metaField.offset(), metaField.length());

            // known fields but not yet parsed/implemented
            for (int fieldNumber : new int[] {2, 5, 6, 8, 12}) {
                // field 2: sensor channel
                // field 5: av/mic input channel
                // field 6: bluetooth channel
                // field 8: navigation channel
                // field 12: vendor extension channel

                metaField = ProtoParser.getSingle(fields.get(fieldNumber), ProtoParser.ProtoVarData.class);
                if (metaField != null) {
                    Log.d(TAG, "channel " + channelId + " meta (at field " + fieldNumber + ") not parsed: " + Base64.encodeToString(buffer, metaField.offset(), metaField.length(), 0));
                    return null;
                }
            }

            // not yet known but would like to know
            Log.v(TAG, "unknown metadata field for channel meta. dump: " + Base64.encodeToString(buffer, offset, length, 0));
            return null;

        } catch (Throwable t) {
            Log.wtf(TAG, "failed to parse ChannelMeta: " + Base64.encodeToString(buffer, offset, length, 0), t);
            return null;
        }
    }
}
