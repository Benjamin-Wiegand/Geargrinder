package io.benwiegand.projection.geargrinder.proto.data.readable.sensor;

import android.util.Base64;
import android.util.Log;

import java.util.List;
import java.util.Map;

import io.benwiegand.projection.geargrinder.proto.ProtoParser;
import io.benwiegand.projection.geargrinder.proto.data.readable.ChannelMeta;

public record SensorChannelMeta(
        int channelId,
        SensorMeta[] sensors
) implements ChannelMeta {
    private static final String TAG = SensorChannelMeta.class.getSimpleName();

    public static SensorChannelMeta parse(int channelId, byte[] buffer, int offset, int length) {
        try {
            Map<Integer, List<ProtoParser.ProtoField>> fields = ProtoParser.parse(buffer, offset, length);

            List<ProtoParser.ProtoField> sensorMetaFields = fields.get(1);
            SensorMeta[] sensorMetas = new SensorMeta[sensorMetaFields == null ? 0 : sensorMetaFields.size()];
            if (sensorMetaFields != null) {
                int i = 0;
                for (ProtoParser.ProtoField sensorMetaField : sensorMetaFields) {
                    if (sensorMetaField instanceof ProtoParser.ProtoVarData vd) {
                        sensorMetas[i++] = SensorMeta.parse(buffer, vd.offset(), vd.length());
                    } else {
                        throw new AssertionError("expected var data for audio preset");
                    }
                }
            }

            return new SensorChannelMeta(
                    channelId,
                    sensorMetas
            );

        } catch (Throwable t) {
            Log.wtf(TAG, "failed to parse SensorChannelMeta: " + Base64.encodeToString(buffer, offset, length, 0), t);
            return null;
        }
    }
}
