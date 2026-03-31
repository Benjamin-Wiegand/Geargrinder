package io.benwiegand.projection.geargrinder.channel;

import static io.benwiegand.projection.geargrinder.message.AAFrame.COMMAND_ID_LENGTH;
import static io.benwiegand.projection.geargrinder.protocol.AAConstants.CMD_CHANNEL_OPEN_REQUEST;
import static io.benwiegand.projection.geargrinder.protocol.AAConstants.CMD_CHANNEL_OPEN_RESPONSE;
import static io.benwiegand.projection.geargrinder.protocol.AAConstants.SENSOR_CMD_EVENT_INDICATION;
import static io.benwiegand.projection.geargrinder.protocol.AAConstants.SENSOR_CMD_START_REQUEST;
import static io.benwiegand.projection.geargrinder.protocol.AAConstants.SENSOR_CMD_START_RESPONSE;
import static io.benwiegand.projection.geargrinder.util.ByteUtil.hexDump;
import static io.benwiegand.projection.geargrinder.util.ByteUtil.readUInt16;

import android.util.Log;

import io.benwiegand.projection.geargrinder.callback.MessageListener;
import io.benwiegand.projection.geargrinder.message.MessageBroker;
import io.benwiegand.projection.geargrinder.proto.data.readable.ChannelOpenResponse;
import io.benwiegand.projection.geargrinder.proto.data.readable.input.InputEventData;
import io.benwiegand.projection.geargrinder.proto.data.readable.sensor.SensorChannelMeta;
import io.benwiegand.projection.geargrinder.proto.data.readable.sensor.SensorMeta;
import io.benwiegand.projection.geargrinder.proto.data.writable.ChannelOpenRequest;
import io.benwiegand.projection.geargrinder.proto.data.writable.sensor.SensorStartRequest;

public class SensorChannel implements MessageListener {
    private static final String TAG = InputChannel.class.getSimpleName();

    private static final boolean LOG_EVENT_DEBUG = false;

    private final MessageBroker mb;

    private final SensorChannelMeta channelMeta;
    private final MessageBroker.MessageSendParameters controlMessageParams;
    private final MessageBroker.MessageSendParameters messageParams;

    public SensorChannel(MessageBroker mb, SensorChannelMeta channelMeta) {
        this.mb = mb;
        this.channelMeta = channelMeta;
        controlMessageParams = new MessageBroker.MessageSendParameters(channelMeta.channelId(), true, true);
        messageParams = new MessageBroker.MessageSendParameters(channelMeta.channelId(), true, false);

        mb.registerForChannel(channelMeta.channelId(), this);
    }

    public void destroy() {
        // TODO
    }

    public void openChannel() {
        Log.i(TAG, "sending channel open request");
        mb.sendMessage(controlMessageParams, CMD_CHANNEL_OPEN_REQUEST, new ChannelOpenRequest(0, channelMeta.channelId()).serialize());
    }

    private void onSensorEvent() {
        // TODO
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

//                Log.i(TAG, "requesting all sensors");
//                for (SensorMeta sensor : channelMeta.sensors()) {
//                    mb.sendMessage(messageParams, SENSOR_CMD_START_REQUEST, new SensorStartRequest(sensor.type(), 0).serialize());
//                }
            }

            case SENSOR_CMD_START_RESPONSE -> {
                Log.d(TAG, "sensor start response");

                // TODO: unparsed
                Log.d(TAG, "payload: " + hexDump(buffer, payloadOffset, payloadLength));
            }

            case SENSOR_CMD_EVENT_INDICATION -> {
                Log.d(TAG, "sensor event");

                // TODO: unparsed
                Log.d(TAG, "payload: " + hexDump(buffer, payloadOffset, payloadLength));
            }

            default -> {
                Log.w(TAG, "sensor command not handled: " + command);
                Log.d(TAG, "payload: " + hexDump(buffer, payloadOffset, payloadLength));
            }
        }
    }
}
