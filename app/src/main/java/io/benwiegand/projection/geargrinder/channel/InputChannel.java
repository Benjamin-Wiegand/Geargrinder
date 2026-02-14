package io.benwiegand.projection.geargrinder.channel;

import static io.benwiegand.projection.geargrinder.message.AAFrame.COMMAND_ID_LENGTH;
import static io.benwiegand.projection.geargrinder.protocol.AAConstants.CMD_CHANNEL_OPEN_REQUEST;
import static io.benwiegand.projection.geargrinder.protocol.AAConstants.CMD_CHANNEL_OPEN_RESPONSE;
import static io.benwiegand.projection.geargrinder.protocol.AAConstants.INPUT_CMD_BINDING_REQUEST;
import static io.benwiegand.projection.geargrinder.protocol.AAConstants.INPUT_CMD_BINDING_RESPONSE;
import static io.benwiegand.projection.geargrinder.protocol.AAConstants.INPUT_CMD_EVENT;
import static io.benwiegand.projection.geargrinder.util.ByteUtil.hexDump;
import static io.benwiegand.projection.geargrinder.util.ByteUtil.readUInt16;

import android.util.Log;

import io.benwiegand.projection.geargrinder.callback.InputEventListener;
import io.benwiegand.projection.geargrinder.coordinate.CoordinateTranslator;
import io.benwiegand.projection.geargrinder.message.MessageBroker;
import io.benwiegand.projection.geargrinder.callback.MessageListener;
import io.benwiegand.projection.geargrinder.proto.ProtoParser;
import io.benwiegand.projection.geargrinder.proto.data.readable.ChannelOpenResponse;
import io.benwiegand.projection.geargrinder.proto.data.readable.input.InputChannelMeta;
import io.benwiegand.projection.geargrinder.proto.data.readable.input.InputEventData;
import io.benwiegand.projection.geargrinder.proto.data.readable.input.event.ButtonEvent;
import io.benwiegand.projection.geargrinder.proto.data.readable.input.event.TouchEvent;
import io.benwiegand.projection.geargrinder.proto.data.writable.ChannelOpenRequest;

public class InputChannel implements MessageListener {
    private static final String TAG = InputChannel.class.getSimpleName();

    private static final boolean LOG_EVENT_DEBUG = false;

    private final MessageBroker mb;

    private final InputChannelMeta channelMeta;
    private final MessageBroker.MessageSendParameters messageParams;

    private InputEventListener inputEventListener = null;
    private final CoordinateTranslator<TouchEvent.PointerLocation> touchCoordinateTranslator = CoordinateTranslator.createTouchEventPassthrough();

    public InputChannel(MessageBroker mb, InputChannelMeta channelMeta) {
        this.mb = mb;
        this.channelMeta = channelMeta;
        messageParams = new MessageBroker.MessageSendParameters(channelMeta.channelId(), true, false);

        mb.registerForChannel(channelMeta.channelId(), this);
    }

    public void destroy() {
        // TODO
    }

    public void openChannel() {
        Log.i(TAG, "sending channel open request");
        mb.sendMessage(messageParams, CMD_CHANNEL_OPEN_REQUEST, new ChannelOpenRequest(0, channelMeta.channelId()).serialize());
    }

    public void setInputEventListener(InputEventListener listener) {
        inputEventListener = listener;
    }

    public InputChannelMeta getMetadata() {
        return channelMeta;
    }

    private void onInputEvent(InputEventData eventData) {
        for (ButtonEvent buttonEvent : eventData.buttonEvents()) {
            Log.i(TAG, "button event: " + buttonEvent);
            // TODO: parse
        }

        if (eventData.touchEvent() != null) {
            if (!channelMeta.hasTouchScreen()) {
                Log.wtf(TAG, "got touch event with no attached touch screen???");
                return;
            }
            inputEventListener.onTouchEvent(eventData.touchEvent(), touchCoordinateTranslator);
        }
    }

    @Override
    public void onMessage(int channelId, int flags, byte[] buffer, int payloadOffset, int payloadLength) {
        if (payloadLength < COMMAND_ID_LENGTH) {
            Log.wtf(TAG, "message payload too small!", new RuntimeException());
            return;
        }

        int command = readUInt16(buffer, payloadOffset);
        switch (command) {
            case CMD_CHANNEL_OPEN_RESPONSE -> {
                ChannelOpenResponse response = ChannelOpenResponse.parse(buffer, payloadOffset + COMMAND_ID_LENGTH, payloadLength - COMMAND_ID_LENGTH);
                Log.i(TAG, "channel open response: " + response);
                if (response == null) {
                    Log.e(TAG, "failed to parse channel open response, bailing!");
                    // TODO: terminate connection
                    return;
                }

                switch (response.status()) {
                    case OK -> {}
                    case UNKNOWN -> Log.w(TAG, "channel open status unknown");    // still go ahead anyway
                    case ERROR -> {
                        Log.e(TAG, "failed to open channel");
                        // TODO: terminate connection
                        return;
                    }
                }

                Log.i(TAG, "sending input binding request");
                mb.sendMessage(messageParams, INPUT_CMD_BINDING_REQUEST, new byte[0]);
            }

            case INPUT_CMD_EVENT -> {
                InputEventData eventData = InputEventData.parse(buffer, payloadOffset + COMMAND_ID_LENGTH, payloadLength - COMMAND_ID_LENGTH);
                if (LOG_EVENT_DEBUG) {
                    Log.d(TAG, "got input event: " + eventData);
                    ProtoParser.debugDumpRecursive(buffer, payloadOffset + COMMAND_ID_LENGTH, payloadLength - COMMAND_ID_LENGTH);
                }
                if (eventData == null) {
                    Log.e(TAG, "failed to parse input event");
                    return;
                }

                onInputEvent(eventData);
            }

            case INPUT_CMD_BINDING_RESPONSE -> {
                Log.d(TAG, "binding response");

                // TODO: unparsed
                Log.d(TAG, "payload: " + hexDump(buffer, payloadOffset, payloadLength));
            }

            default -> {
                Log.w(TAG, "input command not handled: " + command);
                Log.d(TAG, "payload: " + hexDump(buffer, payloadOffset, payloadLength));
            }
        }
    }
}
