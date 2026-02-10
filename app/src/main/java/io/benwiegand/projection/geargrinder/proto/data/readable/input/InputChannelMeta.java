package io.benwiegand.projection.geargrinder.proto.data.readable.input;

import android.util.Base64;
import android.util.Log;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import io.benwiegand.projection.geargrinder.proto.ProtoParser;
import io.benwiegand.projection.geargrinder.proto.data.readable.ChannelMeta;

public record InputChannelMeta(
        int channelId,
        int[] keycodes,
        TouchInputMeta touchScreenMeta,
        TouchInputMeta touchPadMeta
) implements ChannelMeta {
    private static final String TAG = InputChannelMeta.class.getSimpleName();

    public boolean hasTouchScreen() {
        return touchScreenMeta() != null;
    }

    public static InputChannelMeta parse(int channelId, byte[] buffer, int offset, int length) {
        try {
            Map<Integer, List<ProtoParser.ProtoField>> fields = ProtoParser.parse(buffer, offset, length);

            int[] keyCodes = ProtoParser.getUnsignedInteger32Array(buffer, fields.get(1));

            ProtoParser.ProtoVarData touchScreenMetaField = ProtoParser.getSingle(fields.get(2), ProtoParser.ProtoVarData.class);
            TouchInputMeta touchScreenMeta = touchScreenMetaField == null ? null : TouchInputMeta.parse(buffer, touchScreenMetaField.offset(), touchScreenMetaField.length());

            ProtoParser.ProtoVarData touchPadMetaField = ProtoParser.getSingle(fields.get(3), ProtoParser.ProtoVarData.class);
            TouchInputMeta touchPadMeta = touchPadMetaField == null ? null : TouchInputMeta.parse(buffer, touchPadMetaField.offset(), touchPadMetaField.length());

            return new InputChannelMeta(
                    channelId,
                    keyCodes,
                    touchScreenMeta,
                    touchPadMeta
            );

        } catch (Throwable t) {
            Log.wtf(TAG, "failed to parse InputChannelMeta: " + Base64.encodeToString(buffer, offset, length, 0), t);
            return null;
        }
    }

    @Override
    public String toString() {
        return "InputChannelMeta{" +
                "channelId=" + channelId +
                ", keycodes=" + Arrays.toString(keycodes) +
                ", touchScreenMeta=" + touchScreenMeta +
                ", touchPadMeta=" + touchPadMeta +
                '}';
    }
}
