package io.benwiegand.projection.geargrinder.proto.data.readable.av;

import android.util.Base64;
import android.util.Log;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import io.benwiegand.projection.geargrinder.proto.ProtoParser;
import io.benwiegand.projection.geargrinder.proto.data.readable.av.preset.VideoPreset;

public record VideoChannelMeta(
        int channelId,
        VideoPreset[] presets
        // calls not implemented yet
) implements AVChannelMeta {
    private static final String TAG = VideoChannelMeta.class.getSimpleName();

    public static VideoChannelMeta parse(int channelId, byte[] buffer, int offset, int length, Map<Integer, List<ProtoParser.ProtoField>> fields) {
        try {

            List<ProtoParser.ProtoField> videoPresetFields = fields.get(4);
            VideoPreset[] videoPresets = new VideoPreset[videoPresetFields == null ? 0 : videoPresetFields.size()];
            if (videoPresetFields != null) {
                int i = 0;
                for (ProtoParser.ProtoField videoPresetField : videoPresetFields) {
                    if (videoPresetField instanceof ProtoParser.ProtoVarData vd) {
                        videoPresets[i++] = VideoPreset.parse(buffer, vd.offset(), vd.length());
                    } else {
                        throw new AssertionError("expected var data for audio preset");
                    }
                }
            }

            return new VideoChannelMeta(
                    channelId,
                    videoPresets
            );

        } catch (Throwable t) {
            Log.wtf(TAG, "failed to parse VideoChannelMeta: " + Base64.encodeToString(buffer, offset, length, 0), t);
            return null;
        }
    }

    @Override
    public String toString() {
        return "VideoChannelMeta{" +
                "channelId=" + channelId +
                ", presets=" + Arrays.toString(presets) +
                '}';
    }
}
