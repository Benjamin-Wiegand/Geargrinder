package io.benwiegand.projection.geargrinder.channel;

import static io.benwiegand.projection.geargrinder.message.AAFrame.COMMAND_ID_LENGTH;
import static io.benwiegand.projection.geargrinder.protocol.AAConstants.AV_CMD_MEDIA_ACK;
import static io.benwiegand.projection.geargrinder.protocol.AAConstants.AV_CMD_SETUP_REQUEST;
import static io.benwiegand.projection.geargrinder.protocol.AAConstants.AV_CMD_SETUP_RESPONSE;
import static io.benwiegand.projection.geargrinder.protocol.AAConstants.AV_CMD_START;
import static io.benwiegand.projection.geargrinder.protocol.AAConstants.AV_CMD_STOP;
import static io.benwiegand.projection.geargrinder.protocol.AAConstants.AV_CMD_VIDEO_FOCUSED;
import static io.benwiegand.projection.geargrinder.protocol.AAConstants.CMD_CHANNEL_OPEN_REQUEST;
import static io.benwiegand.projection.geargrinder.protocol.AAConstants.CMD_CHANNEL_OPEN_RESPONSE;
import static io.benwiegand.projection.geargrinder.util.ByteUtil.hexDump;
import static io.benwiegand.projection.geargrinder.util.ByteUtil.readUInt16;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import io.benwiegand.projection.geargrinder.message.MessageBroker;
import io.benwiegand.projection.geargrinder.callback.MessageListener;
import io.benwiegand.projection.geargrinder.proto.data.readable.av.AVSetupResponse;
import io.benwiegand.projection.geargrinder.proto.data.readable.ChannelOpenResponse;
import io.benwiegand.projection.geargrinder.proto.data.readable.av.VideoFocusIndication;
import io.benwiegand.projection.geargrinder.proto.data.writable.av.AVSetupRequest;
import io.benwiegand.projection.geargrinder.proto.data.writable.av.AVStartIndication;
import io.benwiegand.projection.geargrinder.proto.data.writable.ChannelOpenRequest;

public abstract class AVChannel<T> implements MessageListener {
    private final String TAG = getTag();

    // some headunits set the max outstanding ack number too high which would cause major latency if the headunit lags behind
    // for video this is a quarter second at 60 fps, and half a second at 30 fps
    // for 48000 Hz audio this is over half a second
    // this should probably be even lower
    private static final int MAX_OUTSTANDING_ACK_LIMIT = 15;

    protected final long AV_ACK_TIMEOUT = 500;

    private final Object avAckLock = new Object();
    private int outstandingAcks = 0;
    private int maxOutstandingAcks = 1;

    private final Object avThreadLock = new Object();
    private Thread avThread = null;
    private Runnable stopAvThreadRunnable = null;

    protected final byte[] buffer;


    protected final MessageBroker mb;

    protected final MessageBroker.MessageSendParameters controlParams;
    protected final MessageBroker.MessageSendParameters mediaParams;
    protected final int channelPriority;

    protected record AVPreset<T>(int index, T preset) {}
    protected final List<AVPreset<T>> avPresets = new ArrayList<>();

    protected boolean dead = false;

    public AVChannel(MessageBroker mb, int channelId, int channelPriority, int bufferSize) {
        this.mb = mb;
        this.channelPriority = channelPriority;
        buffer = new byte[bufferSize];

        // TODO: determine correct places to use control flag, this is just a guess
        controlParams = new MessageBroker.MessageSendParameters(channelId, true, true);
        mediaParams = new MessageBroker.MessageSendParameters(channelId, true, false);

        mb.registerForChannel(channelId, this);
    }

    public void destroy() {
        dead = true;
        stop();
    }

    public void openChannel() {
        Log.i(TAG, "sending channel open request");
        mb.sendMessage(controlParams, CMD_CHANNEL_OPEN_REQUEST,
                new ChannelOpenRequest(channelPriority, controlParams.channelId()).serialize());
    }

    protected void onAck() {
        synchronized (avAckLock) {
            outstandingAcks--;
            avAckLock.notify();
        }
    }

    protected void expectAck() {
        synchronized (avAckLock) {
            outstandingAcks++;
        }
    }

    protected boolean waitForAck(long timeout) throws InterruptedException {
        synchronized (avAckLock) {
            if (outstandingAcks >= maxOutstandingAcks) {
                avAckLock.wait(timeout);
            }
            return outstandingAcks < maxOutstandingAcks;
        }
    }

    protected void start() {
        synchronized (avThreadLock) {

            if (avThread != null) {
                Log.w(TAG, "av thread already running");
                return;
            }

            outstandingAcks = 0;

            AtomicBoolean runBool = new AtomicBoolean(true);
            stopAvThreadRunnable = () -> runBool.set(false);
            avThread = new Thread(() -> {
                try {
                    avLoop(runBool::get);
                } finally {
                    avThread = null;
                }
            });

            Log.i(TAG, "starting av thread");
            avThread.start();
        }
    }

    protected void stop() {
        synchronized (avThreadLock) {
            if (avThread == null) {
                Log.w(TAG, "av thread not running");
                return;
            }

            if (stopAvThreadRunnable == null) {
                Log.w(TAG, "av thread already stopping");
                return;
            }

            Log.i(TAG, "stopping av thread");
            stopAvThreadRunnable.run();
            stopAvThreadRunnable = null;
        }
    }

    protected void sendStartIndication(AVStartIndication startIndication) {
        Log.i(TAG, "sending start indication");
        mb.sendMessage(mediaParams, AV_CMD_START, startIndication.serialize());
    }

    protected void sendStopIndication() {
        Log.i(TAG, "sending stop indication");
        mb.sendMessage(mediaParams, AV_CMD_STOP, new byte[0]);
    }

    protected void sendAvBuffer(int offset, int length) {
        mb.sendMessage(mediaParams, buffer, offset, length);
        expectAck();
    }

    protected void onAvSetupResponse(AVSetupResponse response) {
        switch (response.status()) {
            case OK, NO_ERROR -> {}
            case UNKNOWN -> Log.w(TAG, "av setup status unknown");
            case ERROR -> {
                Log.e(TAG, "av setup failed");
                // TODO: terminate connection
                return;
            }
        }

        maxOutstandingAcks = response.maxOutstandingAck();
        if (maxOutstandingAcks > MAX_OUTSTANDING_ACK_LIMIT) {
            // some headunits return excessive numbers for this which causes latency issues
            Log.w(TAG, "limiting maxOutstandingAcks from " + maxOutstandingAcks + " to " + MAX_OUTSTANDING_ACK_LIMIT);
            maxOutstandingAcks = MAX_OUTSTANDING_ACK_LIMIT;
        }

        updatePresets(response.acceptedPresets());
    }

    protected void onVideoFocusIndication(VideoFocusIndication indication) {
        Log.wtf(TAG, "got an unhandled video focus indication: " + indication, new AssertionError());
        assert false;
    }

    protected abstract void updatePresets(int[] acceptedPresets);
    protected abstract void avLoop(Supplier<Boolean> runCondition);

    protected abstract AVSetupRequest getAvSetupRequest();

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

                Log.i(TAG, "sending av setup request");
                mb.sendMessage(mediaParams, AV_CMD_SETUP_REQUEST, getAvSetupRequest().serialize());
            }
            case AV_CMD_SETUP_RESPONSE -> {
                AVSetupResponse response = AVSetupResponse.parse(buffer, payloadOffset + COMMAND_ID_LENGTH, payloadLength - COMMAND_ID_LENGTH);
                Log.d(TAG, "av setup response: " + response);
                if (response == null) {
                    Log.e(TAG, "failed to parse av setup response, bailing!");
                    // TODO: terminate connection
                    return;
                }

                onAvSetupResponse(response);
            }
            case AV_CMD_MEDIA_ACK -> {
                // TODO: unparsed
                onAck();
            }
            case AV_CMD_VIDEO_FOCUSED -> {
                VideoFocusIndication indication = VideoFocusIndication.parse(buffer, payloadOffset + COMMAND_ID_LENGTH, payloadLength - COMMAND_ID_LENGTH);
                Log.v(TAG, "video focus indication: " + indication);
                if (indication == null) {
                    Log.e(TAG, "failed to parse video focus indication");
                    assert false;
                    return;
                }

                onVideoFocusIndication(indication);
            }

            default -> {
                Log.w(TAG, "av command not handled: " + command);
                Log.d(TAG, "payload: " + hexDump(buffer, payloadOffset, payloadLength));
            }
        }

    }

    protected abstract String getTag();
}
