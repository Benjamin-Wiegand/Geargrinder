package io.benwiegand.projection.geargrinder.proto.data.writable.sensor;

import io.benwiegand.projection.geargrinder.proto.ProtoSerializer;
import io.benwiegand.projection.geargrinder.proto.data.readable.sensor.SensorMeta;

public record SensorStartRequest(
        SensorMeta.Type sensorType,
        long refreshInterval
) {

    public byte[] serialize() {
        return ProtoSerializer.serialize(
                new ProtoSerializer.ProtoVarInt(1, sensorType().serialize()),
                new ProtoSerializer.ProtoVarInt(2, refreshInterval)
        );
    }
}
