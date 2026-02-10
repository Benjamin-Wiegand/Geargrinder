package io.benwiegand.projection.geargrinder.proto.data.writable;

import io.benwiegand.projection.geargrinder.proto.ProtoSerializer;

public record ChannelOpenRequest(
        int priority,
        int channelId
) {

    public byte[] serialize() {
        return ProtoSerializer.serialize(
                new ProtoSerializer.Proto32(1, priority()),
                new ProtoSerializer.Proto32(2, channelId())
        );
    }
}
