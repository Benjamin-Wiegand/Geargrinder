package io.benwiegand.projection.geargrinder.channel;

import static io.benwiegand.projection.geargrinder.message.AAFrame.COMMAND_ID_LENGTH;
import static io.benwiegand.projection.geargrinder.protocol.AAConstants.*;
import static io.benwiegand.projection.geargrinder.util.ByteUtil.writeInt64;
import static io.benwiegand.projection.geargrinder.util.ByteUtil.writeUInt16;

import android.os.SystemClock;
import android.util.Log;

import java.util.Arrays;
import java.util.function.Supplier;

import io.benwiegand.projection.geargrinder.message.MessageBroker;
import io.benwiegand.projection.geargrinder.projection.ProjectionService;
import io.benwiegand.projection.geargrinder.proto.data.readable.av.VideoChannelMeta;
import io.benwiegand.projection.geargrinder.proto.data.readable.av.VideoFocusIndication;
import io.benwiegand.projection.geargrinder.proto.data.readable.av.preset.VideoPreset;
import io.benwiegand.projection.geargrinder.proto.data.writable.av.AVSetupRequest;
import io.benwiegand.projection.geargrinder.proto.data.writable.av.AVStartIndication;
import io.benwiegand.projection.geargrinder.projection.video.FrameRateCounter;
import io.benwiegand.projection.geargrinder.projection.video.VideoEncoder;
import io.benwiegand.projection.geargrinder.settings.SettingsManager;

public class VideoChannel extends AVChannel<VideoPreset> {
    private static final String TAG = VideoChannel.class.getSimpleName();

    private static final boolean LOG_FRAME_DEBUG = false;
    private static final boolean LOG_FRAME_RATE_DEBUG = false;

    private static final int VIDEO_BUFFER_SIZE_DEFAULT = 0x40000;     // 256 KiB

    private static final long VIDEO_FRAME_TIMEOUT_US = 500000;  // 500 ms
    private static final int VIDEO_BUFFER_RESERVED = COMMAND_ID_LENGTH + 8; // command + 64-bit timestamp

    private final FrameRateCounter frameRateCounter = new FrameRateCounter();

    private final ProjectionService projectionService;
    private final SettingsManager settingsManager;

    private final VideoChannelMeta channelMeta;

    private static int calculateVideoBufferSize(SettingsManager settingsManager) {
        int prefBufferSize = settingsManager.getVideoBufferSize();
        if (prefBufferSize > 0) {
            Log.i(TAG, "using video buffer size set by user: " + prefBufferSize);
            return prefBufferSize;
        }

        // TODO: determine video buffer size based on preset
        Log.i(TAG, "using auto video buffer size: " + VIDEO_BUFFER_SIZE_DEFAULT);
        return VIDEO_BUFFER_SIZE_DEFAULT;
    }

    public VideoChannel(MessageBroker mb, ProjectionService projectionService, SettingsManager settingsManager, VideoChannelMeta channelMeta) {
        super(mb, channelMeta.channelId(), 0, calculateVideoBufferSize(settingsManager));
        this.channelMeta = channelMeta;
        this.projectionService = projectionService;
        this.settingsManager = settingsManager;
    }

    @Override
    protected void onVideoFocusIndication(VideoFocusIndication indication) {
        switch (indication.focusType()) {
            case FOCUSED -> start();
            case UNFOCUSED -> stop();
            case UNKNOWN -> Log.wtf(TAG, "video focus type is unknown", new AssertionError());
        }
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
    protected void avLoop(Supplier<Boolean> runCondition) {
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

            while (runCondition.get()) {
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
