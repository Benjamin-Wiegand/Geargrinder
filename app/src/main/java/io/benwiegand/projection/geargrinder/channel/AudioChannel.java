package io.benwiegand.projection.geargrinder.channel;

import static io.benwiegand.projection.geargrinder.message.AAFrame.COMMAND_ID_LENGTH;
import static io.benwiegand.projection.geargrinder.protocol.AAConstants.AV_CMD_MEDIA_WITH_TIMESTAMP;
import static io.benwiegand.projection.geargrinder.util.ByteUtil.writeInt64;
import static io.benwiegand.projection.geargrinder.util.ByteUtil.writeUInt16;

import android.util.Log;

import java.util.Arrays;

import io.benwiegand.projection.geargrinder.audio.AudioCapture;
import io.benwiegand.projection.geargrinder.message.MessageBroker;
import io.benwiegand.projection.geargrinder.proto.data.readable.av.AudioChannelMeta;
import io.benwiegand.projection.geargrinder.proto.data.readable.av.preset.AudioPreset;
import io.benwiegand.projection.geargrinder.proto.data.writable.av.AVSetupRequest;
import io.benwiegand.projection.geargrinder.proto.data.writable.av.AVStartIndication;

public class AudioChannel extends AVChannel<AudioPreset> {
    private static final String TAG = AudioChannel.class.getSimpleName();

    private static final boolean LOG_FRAME_DEBUG = false;

    private static final int AUDIO_BUFFER_RESERVED = COMMAND_ID_LENGTH + 8; // command + 64-bit timestamp
    private static final int TRAILING_SILENCE_BUFFERS = 2;    // stop sending silence after this many silent buffers

    private final AudioCaptureProvider audioCaptureProvider;

    private final AudioChannelMeta channelMeta;


    public interface AudioCaptureProvider {
        AudioCapture getInstance(AudioPreset audioPreset, int bufferSize);
    }

    private static int calculateBufferSize(AudioChannelMeta channelMeta) {
        int bufferSize = 0;
        for (AudioPreset preset : channelMeta.presets()) {
            if (!preset.isSupported()) continue;
            if (preset.getMinBufferSize() < bufferSize) continue;
            bufferSize = preset.getMinBufferSize();
        }
        Log.i(TAG, "selected buffer size of " + bufferSize + " (plus " + AUDIO_BUFFER_RESERVED + " reserved)");
        return bufferSize + AUDIO_BUFFER_RESERVED;
    }


    public AudioChannel(MessageBroker mb, AudioChannelMeta channelMeta, AudioCaptureProvider audioCaptureProvider) {
        super(mb, channelMeta.channelId(), 0, calculateBufferSize(channelMeta));
        this.channelMeta = channelMeta;
        this.audioCaptureProvider = audioCaptureProvider;
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
            AudioPreset preset = channelMeta.presets()[i];
            if (!preset.isSupported()) continue;

            Log.i(TAG, "found supported audio preset: " + preset);
            avPresets.add(new AVPreset<>(i, preset));
        }

        if (avPresets.isEmpty())
            Log.e(TAG, "failed to find any supported accepted audio presets");
    }

    @Override
    protected AVSetupRequest getAvSetupRequest() {
        return AVSetupRequest.createAudio();
    }

    @Override
    protected void avLoop() {
        Log.i(TAG, "audio loop start");
        int silence = 0;
        AudioCapture.Result result = new AudioCapture.Result();

        // find working preset
        AVPreset<AudioPreset> avPreset = null;
        AudioCapture audioCapture = null;
        for (AVPreset<AudioPreset> p : avPresets) {
            audioCapture = audioCaptureProvider.getInstance(p.preset(), buffer.length - AUDIO_BUFFER_RESERVED);
            if (audioCapture == null) {
                Log.e(TAG, "failed to init audio capture with preset: " + p.preset());
                continue;
            }

            try {
                audioCapture.begin();
            } catch (Throwable t) {
                Log.e(TAG, "failed to start audio capture with preset: " + p.preset(), t);
                audioCapture.destroy();
                audioCapture = null;
                continue;
            }

            avPreset = p;
            break;
        }

        if (avPreset == null) {
            Log.e(TAG, "no working audio presets!");
            return;
        }

        try {
            sendStartIndication(new AVStartIndication(0, avPreset.index()));

            while (!dead) {
                if (!waitForAck(AV_ACK_TIMEOUT)) continue;

                audioCapture.nextBuffer(result, buffer, AUDIO_BUFFER_RESERVED, buffer.length - AUDIO_BUFFER_RESERVED);
                switch (result.error) {
                    case NO_ERROR -> { }
                    case TRY_AGAIN -> {
                        // TODO: calculate and wait buffer time
                        if (LOG_FRAME_DEBUG) Log.w(TAG, "try again");
                        continue;
                    }
                    case FAILURE -> {
                        Log.e(TAG, "audio capture failure");
                        return;
                    }
                    case END_OF_STREAM -> {
                        Log.v(TAG, "end of stream");
                        return;
                    }
                }

                // when no audio is playing, no audio packets are sent
                // but some trailing empty buffers must be sent to avoid the receiver indefinitely repeating the last 40 ms or so of audio
                if (result.silent) {
                    if (silence > TRAILING_SILENCE_BUFFERS) continue;
                    silence++;
                } else {
                    silence = 0;
                }

                writeUInt16(AV_CMD_MEDIA_WITH_TIMESTAMP, buffer, 0);
                writeInt64(result.timestamp, buffer, COMMAND_ID_LENGTH);

                sendAvBuffer(0, result.length + AUDIO_BUFFER_RESERVED);
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "interrupted", e);
        } finally {
            Log.d(TAG, "audio loop death");
            sendStopIndication();
            audioCapture.destroy();
        }
    }

    @Override
    protected String getTag() {
        return TAG;
    }

}
