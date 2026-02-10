package io.benwiegand.projection.geargrinder.proto.data.readable.av;

import android.util.Base64;
import android.util.Log;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import io.benwiegand.projection.geargrinder.proto.ProtoParser;
import io.benwiegand.projection.geargrinder.proto.data.readable.av.preset.AudioPreset;

public record AudioChannelMeta(
        int channelId,
        AudioType audioType,
        AudioPreset[] presets
        // calls not implemented yet
) implements AVChannelMeta {
    private static final String TAG = AudioChannelMeta.class.getSimpleName();

    public enum AudioType {
        UNKNOWN,
        SPEECH,
        SYSTEM,
        MEDIA,
        ALARM;

        private static AudioType parse(int value) {
            if (value < 0) return UNKNOWN;
            if (value >= values().length) return UNKNOWN;
            return values()[value];
        }
    }

    public static AudioChannelMeta parse(int channelId, byte[] buffer, int offset, int length, Map<Integer, List<ProtoParser.ProtoField>> fields) {
        try {

            List<ProtoParser.ProtoField> audioPresetFields = fields.get(3);
            AudioPreset[] audioPresets = new AudioPreset[audioPresetFields == null ? 0 : audioPresetFields.size()];
            if (audioPresetFields != null) {
                int i = 0;
                for (ProtoParser.ProtoField audioPresetField : audioPresetFields) {
                    if (audioPresetField instanceof ProtoParser.ProtoVarData vd) {
                        audioPresets[i++] = AudioPreset.parse(buffer, vd.offset(), vd.length());
                    } else {
                        throw new AssertionError("expected var data for audio preset, got " + audioPresetField.getClass().getSimpleName());
                    }
                }
            }

            return new AudioChannelMeta(
                    channelId,
                    AudioType.parse(ProtoParser.getSingleUnsignedInteger32(buffer, fields.get(2), -1)),
                    audioPresets
            );

        } catch (Throwable t) {
            Log.wtf(TAG, "failed to parse AudioChannelMeta: " + Base64.encodeToString(buffer, offset, length, 0), t);
            return null;
        }
    }

    @Override
    public String toString() {
        return "AudioChannelMeta{" +
                "channelId=" + channelId +
                ", audioType=" + audioType +
                ", presets=" + Arrays.toString(presets) +
                '}';
    }
}
