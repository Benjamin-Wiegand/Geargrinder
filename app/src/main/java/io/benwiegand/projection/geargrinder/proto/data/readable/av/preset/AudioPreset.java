package io.benwiegand.projection.geargrinder.proto.data.readable.av.preset;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.os.Build;
import android.util.Base64;
import android.util.Log;

import java.util.List;
import java.util.Map;

import io.benwiegand.projection.geargrinder.proto.ProtoParser;

public record AudioPreset(
        int sampleRate,
        int bitDepth,
        int channelCount
) {
    private static final String TAG = AudioPreset.class.getSimpleName();

    public int audioFormatEncoding() {
        return switch(bitDepth()) {
            // TODO: 8, 24, and 32 are untested
            case 8 -> AudioFormat.ENCODING_PCM_8BIT;
            case 16 -> AudioFormat.ENCODING_PCM_16BIT;
            case 24 -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? AudioFormat.ENCODING_PCM_24BIT_PACKED : AudioFormat.ENCODING_INVALID;
            case 32 -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? AudioFormat.ENCODING_PCM_32BIT : AudioFormat.ENCODING_INVALID;
            default -> {
                // unlike mpeg, raw pcm audio is not very forgiving for discrepancies
                Log.e(TAG, "unsupported bit depth: " + bitDepth());
                yield AudioFormat.ENCODING_INVALID;
            }
        };
    }

    public int audioFormatChannelConfig() {
        return switch (channelCount()) {
            // surround sound is not supported yet
            case 1 -> AudioFormat.CHANNEL_IN_MONO;
            case 2 -> AudioFormat.CHANNEL_IN_STEREO;
            default -> {
                Log.e(TAG, "unsupported channel count: " + channelCount());
                yield AudioFormat.CHANNEL_INVALID;
            }
        };
    }

    public AudioFormat createAudioFormat() {
        return new AudioFormat.Builder()
                .setEncoding(audioFormatEncoding())
                .setSampleRate(sampleRate())
                .setChannelMask(audioFormatChannelConfig())
                .build();
    }

    public int getMinBufferSize() {
        return AudioRecord.getMinBufferSize(
                sampleRate(),
                audioFormatChannelConfig(),
                audioFormatEncoding()
        );
    }

    public boolean isSupported() {
        // does not guarantee that the sample rate is supported
        if (audioFormatEncoding() == AudioFormat.ENCODING_INVALID) return false;
        if (audioFormatChannelConfig() == AudioFormat.CHANNEL_INVALID) return false;
        return sampleRate() > 0;
    }

    public static AudioPreset parse(byte[] buffer, int offset, int length) {
        try {
            Map<Integer, List<ProtoParser.ProtoField>> fields = ProtoParser.parse(buffer, offset, length);

            return new AudioPreset(
                    ProtoParser.getSingleUnsignedInteger32(buffer, fields.get(1), 0),
                    ProtoParser.getSingleUnsignedInteger32(buffer, fields.get(2), 0),
                    ProtoParser.getSingleUnsignedInteger32(buffer, fields.get(3), 0)
            );

        } catch (Throwable t) {
            Log.wtf(TAG, "failed to parse AudioPreset: " + Base64.encodeToString(buffer, offset, length, 0), t);
            return null;
        }
    }
}

