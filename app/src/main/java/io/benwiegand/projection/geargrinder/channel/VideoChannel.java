package io.benwiegand.projection.geargrinder.channel;

import static io.benwiegand.projection.geargrinder.message.AAFrame.COMMAND_ID_LENGTH;
import static io.benwiegand.projection.geargrinder.protocol.AAConstants.*;
import static io.benwiegand.projection.geargrinder.util.ByteUtil.writeInt64;
import static io.benwiegand.projection.geargrinder.util.ByteUtil.writeUInt16;

import android.os.SystemClock;
import android.util.Log;

import java.util.Arrays;

import io.benwiegand.projection.geargrinder.message.MessageBroker;
import io.benwiegand.projection.geargrinder.projection.ProjectionService;
import io.benwiegand.projection.geargrinder.proto.data.readable.av.VideoChannelMeta;
import io.benwiegand.projection.geargrinder.proto.data.readable.av.preset.VideoPreset;
import io.benwiegand.projection.geargrinder.proto.data.writable.av.AVSetupRequest;
import io.benwiegand.projection.geargrinder.proto.data.writable.av.AVStartIndication;
import io.benwiegand.projection.geargrinder.video.FrameRateCounter;
import io.benwiegand.projection.geargrinder.video.VideoEncoder;

public class VideoChannel extends AVChannel<VideoPreset> {
    private static final String TAG = VideoChannel.class.getSimpleName();

    private static final boolean LOG_FRAME_DEBUG = false;
    private static final boolean LOG_FRAME_RATE_DEBUG = false;

    // TODO: find the ideal limit
//    private static final int VIDEO_BUFFER_MAX_LENGTH = 0x10000;     // 64 KiB
    private static final int VIDEO_BUFFER_MAX_LENGTH = 0x20000;     // 128 KiB

    private static final long VIDEO_FRAME_TIMEOUT_US = 500000;  // 500 ms
    private static final int VIDEO_BUFFER_RESERVED = COMMAND_ID_LENGTH + 8; // command + 64-bit timestamp

    private final FrameRateCounter frameRateCounter = new FrameRateCounter();

    private final ProjectionService projectionService;

    private final VideoChannelMeta channelMeta;

    private static int calculateVideoBufferSize(MessageBroker mb) {
        int bufferSize = VIDEO_BUFFER_MAX_LENGTH;
        int maxPayload = mb.getMaxPayloadSize(true);
        if (bufferSize > maxPayload) {
            Log.w(TAG, "limiting video buffer to " + maxPayload + " bytes due to max payload size");
            bufferSize = maxPayload;
        }
        return bufferSize;
    }

    public VideoChannel(MessageBroker mb, ProjectionService projectionService, VideoChannelMeta channelMeta) {
        super(mb, channelMeta.channelId(), 0, calculateVideoBufferSize(mb));
        this.channelMeta = channelMeta;
        this.projectionService = projectionService;
    }

    @Override
    protected void updatePresets(int[] acceptedPresets) {
        // TODO: presets in the metadata can be null

        boolean presetListValid = true;
        if (acceptedPresets.length == 0) {
            Log.w(TAG, "no preset selection provided, assuming all are valid");
            presetListValid = false;
        } else {
            for (int i : acceptedPresets) {
                if (i >= 0 && i < channelMeta.presets().length) continue;
                Log.wtf(TAG, "index out of range in accepted presets");
                presetListValid = false;
                break;
            }
        }

        if (!presetListValid) {
            acceptedPresets = new int[channelMeta.presets().length];
            for (int i = 0; i < acceptedPresets.length; i++) acceptedPresets[i] = i;
        }

        Log.d(TAG, "accepted presets: " + Arrays.toString(acceptedPresets));

        avPresets.clear();

        for (int i : acceptedPresets) {
            VideoPreset preset = channelMeta.presets()[i];
            Log.i(TAG, "found video preset: " + preset);
            avPresets.add(new AVPreset<>(i, preset));
        }

        // TODO: Sort presets

        if (avPresets.isEmpty())
            Log.wtf(TAG, "no presets!");

    }

    @Override
    protected AVSetupRequest getAvSetupRequest() {
        return AVSetupRequest.createVideo();
    }

    @Override
    protected void avLoop() {
        Log.i(TAG, "video loop start");
        long lastFrameGeneratedAt = 0;
        long minFrameInterval, frameTs, timeToNextFrame;
        int payloadOffset, payloadLength;
        VideoEncoder.FrameResult result = new VideoEncoder.FrameResult();

        // find working preset
        AVPreset<VideoPreset> avPreset = null;
        VideoEncoder videoEncoder = null;
        for (AVPreset<VideoPreset> p : avPresets) {
            videoEncoder = new VideoEncoder(
                    p.preset().width(),
                    p.preset().height(),
                    p.preset().refreshRate().hz(),
                    buffer.length - VIDEO_BUFFER_RESERVED
            );
            try {
                Log.i(TAG, "video mode: " + p.preset().width() + " x " + p.preset().height() + " @ " + p.preset().refreshRate().hz() + ", density = " + p.preset().density());
                videoEncoder.init();
            } catch (Throwable t) {
                Log.e(TAG, "failed to initialize video with preset: " + p.preset(), t);
                videoEncoder.destroy();
                videoEncoder = null;
                continue;
            }

            avPreset = p;
            break;
        }

        if (avPreset == null) {
            Log.e(TAG, "no working video presets!");
            return;
        }

        minFrameInterval = 1000 / avPreset.preset().refreshRate().hz();

        try {
            sendStartIndication(new AVStartIndication(0, avPreset.index()));

            // video resolution and projection resolution are different sometimes to account for screen margin
            // AA always uses a 16:9 video, but the headunit screen may differ
            projectionService.setOutput(
                    videoEncoder.getInputSurface(),
                    avPreset.preset()
            );

            while (!dead) {
                waitForAck(AV_ACK_TIMEOUT);

                timeToNextFrame = minFrameInterval - (SystemClock.elapsedRealtime() - lastFrameGeneratedAt);
                if (timeToNextFrame > 0) {
                    if (LOG_FRAME_DEBUG) Log.d(TAG, "too early for next frame, waiting " + timeToNextFrame);
                    // noinspection BusyWait: this is for throttling
                    Thread.sleep(timeToNextFrame);
                } else if (timeToNextFrame < 0) {
                    if (LOG_FRAME_DEBUG) Log.d(TAG, "late for next frame by " + -timeToNextFrame);
                }

                frameTs = SystemClock.elapsedRealtime();
                videoEncoder.getFrame(result, buffer, VIDEO_BUFFER_RESERVED, VIDEO_FRAME_TIMEOUT_US);

                switch (result.error) {
                    case NO_ERROR -> {}
                    case NO_FRAME -> {
                        if (LOG_FRAME_DEBUG) Log.d(TAG, "no frame");
                        lastFrameGeneratedAt = frameTs;
                        continue;
                    }
                    case TRY_AGAIN -> {
                        if (LOG_FRAME_DEBUG) Log.d(TAG, "try again");
                        continue;
                    }
                    case DROP -> {
                        Log.w(TAG, "dropping frame!!");
                        lastFrameGeneratedAt = frameTs;
                        continue;
                    }
                    case END_OF_STREAM -> {
                        Log.i(TAG, "end of stream");
                        return;
                    }
                    case FAILURE -> {
                        Log.e(TAG, "frame generation failure");
                        return;
                    }
                }

                assert result.length > 0;
                assert result.length + VIDEO_BUFFER_RESERVED <= buffer.length;

                if (result.timestamp == 0) {
                    // with no timestamp
                    writeUInt16(AV_CMD_MEDIA, buffer, 8);
                    payloadOffset = 8;
                    payloadLength = COMMAND_ID_LENGTH + result.length;
                } else {
                    // with timestamp
                    writeUInt16(AV_CMD_MEDIA_WITH_TIMESTAMP, buffer, 8);
                    writeInt64(result.timestamp, buffer, 0);
                    payloadOffset = 0;
                    payloadLength = COMMAND_ID_LENGTH + 8 + result.length;
                }

                lastFrameGeneratedAt = frameTs;
                frameRateCounter.onFrame();
                if (LOG_FRAME_RATE_DEBUG) Log.v(TAG, "fps: " + frameRateCounter.getFrameRate());

                if (LOG_FRAME_DEBUG) Log.v(TAG, "sending frame size: " + payloadLength);
                sendAvBuffer(payloadOffset, payloadLength);
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "interrupted", e);
        } finally {
            Log.d(TAG, "video loop death");
            sendStopIndication();
            videoEncoder.destroy();
        }
    }

    @Override
    protected String getTag() {
        return TAG;
    }

}
