package io.benwiegand.projection.geargrinder.proto.data.writable.input;

import io.benwiegand.projection.geargrinder.proto.ProtoSerializer;

public record InputBindingRequest(long[] keyCodes) {

    public byte[] serialize() {
        return ProtoSerializer.serialize(ProtoSerializer.createVarIntArray(1, keyCodes()));
    }

}
