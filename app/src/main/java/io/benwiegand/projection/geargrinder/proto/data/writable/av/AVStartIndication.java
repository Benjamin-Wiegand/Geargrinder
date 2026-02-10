package io.benwiegand.projection.geargrinder.proto.data.writable.av;

import io.benwiegand.projection.geargrinder.proto.ProtoSerializer;

public record AVStartIndication(
        int session,
        int preset
) {

    public byte[] serialize() {
        return ProtoSerializer.serialize(
                new ProtoSerializer.Proto32(1, session()),
                new ProtoSerializer.Proto32(2, preset())
        );
    }

}
