package io.benwiegand.projection.geargrinder.proto.data.readable.input.event;

import android.util.Base64;
import android.util.Log;

import java.util.List;
import java.util.Map;

import io.benwiegand.projection.geargrinder.proto.ProtoParser;

public record ButtonEvent(
        int code,
        boolean pressed
) {
    private static final String TAG = ButtonEvent.class.getSimpleName();

    public static ButtonEvent parse(byte[] buffer, int offset, int length) {
        try {
            Map<Integer, List<ProtoParser.ProtoField>> fields = ProtoParser.parse(buffer, offset, length);

            return new ButtonEvent(
                    ProtoParser.getSingleUnsignedInteger32(buffer, fields.get(1), -1),
                    ProtoParser.getSingleBoolean(buffer, fields.get(2), false)
            );

        } catch (Throwable t) {
            Log.wtf(TAG, "failed to parse ButtonEvent: " + Base64.encodeToString(buffer, offset, length, 0), t);
            return null;
        }
    }

    public static ButtonEvent[] parseAll(byte[] buffer, int offset, int length) {
        try {
            Map<Integer, List<ProtoParser.ProtoField>> fields = ProtoParser.parse(buffer, offset, length);

            List<ProtoParser.ProtoField> buttonEventFields = fields.get(1);
            ButtonEvent[] buttonEvents = new ButtonEvent[buttonEventFields == null ? 0 : buttonEventFields.size()];
            if (buttonEventFields != null) {
                int i = 0;
                for (ProtoParser.ProtoField buttonEventField : buttonEventFields) {
                    if (buttonEventField instanceof ProtoParser.ProtoVarData vd) {
                        buttonEvents[i++] = ButtonEvent.parse(buffer, vd.offset(), vd.length());
                    } else {
                        throw new AssertionError("expected var data for audio preset");
                    }
                }
            }

            return buttonEvents;

        } catch (Throwable t) {
            Log.wtf(TAG, "failed to parse ButtonEvent array: " + Base64.encodeToString(buffer, offset, length, 0), t);
            return null;
        }
    }
}
