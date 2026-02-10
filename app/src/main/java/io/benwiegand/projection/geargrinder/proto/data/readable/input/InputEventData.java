package io.benwiegand.projection.geargrinder.proto.data.readable.input;

import android.util.Base64;
import android.util.Log;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import io.benwiegand.projection.geargrinder.proto.ProtoParser;
import io.benwiegand.projection.geargrinder.proto.data.readable.input.event.ButtonEvent;
import io.benwiegand.projection.geargrinder.proto.data.readable.input.event.TouchEvent;

public record InputEventData(
        long timestamp,         // nanoseconds
        // TODO: field 2 (multi-display?)
        TouchEvent touchEvent,
        ButtonEvent[] buttonEvents
        // TODO: fields 5 and 6 (touch pad?)
) {
    private static final String TAG = InputEventData.class.getSimpleName();

    public static InputEventData parse(byte[] buffer, int offset, int length) {
        try {
            Map<Integer, List<ProtoParser.ProtoField>> fields = ProtoParser.parse(buffer, offset, length);

            ProtoParser.ProtoVarData touchEventField = ProtoParser.getSingle(fields.get(3), ProtoParser.ProtoVarData.class);
            TouchEvent touchEvent = touchEventField == null ? null : TouchEvent.parse(buffer, touchEventField.offset(), touchEventField.length());

            List<ProtoParser.ProtoField> buttonEventFields = fields.get(4);
            ButtonEvent[] buttonEvents = new ButtonEvent[buttonEventFields == null ? 0 : buttonEventFields.size()];
            if (buttonEventFields != null) {
                int i = 0;
                for (ProtoParser.ProtoField buttonEventField : buttonEventFields) {
                    if (buttonEventField instanceof ProtoParser.ProtoVarData vd) {
                        buttonEvents[i++] = ButtonEvent.parse(buffer, vd.offset(), vd.length());
                    } else {
                        throw new AssertionError("expected var data for button event, got " + buttonEventField.getClass().getSimpleName());
                    }
                }
            }

            return new InputEventData(
                    ProtoParser.getSingleUnsignedInteger(buffer, fields.get(1), 0),
                    touchEvent,
                    buttonEvents
            );

        } catch (Throwable t) {
            Log.wtf(TAG, "failed to parse InputEventData: " + Base64.encodeToString(buffer, offset, length, 0), t);
            return null;
        }
    }

    @Override
    public String toString() {
        return "InputEventData{" +
                "timestamp=" + timestamp +
                ", touchEvent=" + touchEvent +
                ", buttonEvents=" + Arrays.toString(buttonEvents) +
                '}';
    }
}
