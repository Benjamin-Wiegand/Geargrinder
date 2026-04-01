package io.benwiegand.projection.geargrinder.proto.data.readable.input;

import android.util.Base64;
import android.util.Log;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import io.benwiegand.projection.geargrinder.proto.ProtoParser;
import io.benwiegand.projection.geargrinder.proto.data.readable.input.event.ButtonEvent;
import io.benwiegand.projection.geargrinder.proto.data.readable.input.event.RelativeEvent;
import io.benwiegand.projection.geargrinder.proto.data.readable.input.event.TouchEvent;

public record InputEventData(
        long timestamp,         // nanoseconds
        // TODO: field 2 (multi-display?)
        TouchEvent touchEvent,
        ButtonEvent[] buttonEvents,
        // TODO: field 5
        RelativeEvent[] relativeEvents
) {
    private static final String TAG = InputEventData.class.getSimpleName();

    public static InputEventData parse(byte[] buffer, int offset, int length) {
        try {
            Map<Integer, List<ProtoParser.ProtoField>> fields = ProtoParser.parse(buffer, offset, length);

            ProtoParser.ProtoVarData touchEventField = ProtoParser.getSingle(fields.get(3), ProtoParser.ProtoVarData.class);
            TouchEvent touchEvent = touchEventField == null ? null : TouchEvent.parse(buffer, touchEventField.offset(), touchEventField.length());

            ButtonEvent[] buttonEvents;
            ProtoParser.ProtoVarData buttonEventField = ProtoParser.getSingle(fields.get(4), ProtoParser.ProtoVarData.class);
            if (buttonEventField != null) {
                buttonEvents = ButtonEvent.parseAll(buffer, buttonEventField.offset(), buttonEventField.length());
            } else {
                buttonEvents = new ButtonEvent[0];
            }

            // encoder/rotary input
            RelativeEvent[] relativeEvents;
            ProtoParser.ProtoVarData relativeEventField = ProtoParser.getSingle(fields.get(6), ProtoParser.ProtoVarData.class);
            if (relativeEventField != null) {
                relativeEvents = RelativeEvent.parseAll(buffer, relativeEventField.offset(), relativeEventField.length());
            } else {
                relativeEvents = new RelativeEvent[0];
            }

            return new InputEventData(
                    ProtoParser.getSingleUnsignedInteger(buffer, fields.get(1), 0),
                    touchEvent,
                    buttonEvents,
                    relativeEvents
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
                ", relativeEvents=" + Arrays.toString(relativeEvents) +
                '}';
    }
}
