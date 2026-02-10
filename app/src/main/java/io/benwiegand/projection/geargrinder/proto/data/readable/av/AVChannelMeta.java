package io.benwiegand.projection.geargrinder.proto.data.readable.av;

import android.util.Base64;
import android.util.Log;

import java.util.List;
import java.util.Map;

import io.benwiegand.projection.geargrinder.proto.ProtoParser;
import io.benwiegand.projection.geargrinder.proto.data.readable.ChannelMeta;

public interface AVChannelMeta extends ChannelMeta {

    static AVChannelMeta parse(int channelId, byte[] buffer, int offset, int length) {
        final String TAG = AVChannelMeta.class.getSimpleName() + "::parse";

        try {
            Map<Integer, List<ProtoParser.ProtoField>> fields = ProtoParser.parse(buffer, offset, length);

            int type = ProtoParser.getSingleUnsignedInteger32(buffer, fields.get(1), -1);
            return switch (type) {
                case 1 -> AudioChannelMeta.parse(channelId, buffer, offset, length, fields);
                // idk what 2 is
                case 3 -> VideoChannelMeta.parse(channelId, buffer, offset, length, fields);
                case 0 -> { // none
                    Log.wtf(TAG, "AV channel type none");
                    yield null;
                }
                case -1 -> throw new AssertionError("AV channel type not defined");
                default -> throw new AssertionError("unknown AV channel type: " + type);
            };
        } catch (Throwable t) {
            Log.wtf(TAG, "failed to parse AVChannelMeta: " + Base64.encodeToString(buffer, offset, length, 0), t);
            return null;
        }
    }
}
