package io.benwiegand.projection.geargrinder.proto.data.writable;

import io.benwiegand.projection.geargrinder.proto.ProtoSerializer;

public record AudioFocusRequest(Type focusType) {

    public enum Type {
        NONE,
        GAIN,
        GAIN_TRANSIENT,
        GAIN_NAVI,
        RELEASE;

        public int serialize() {
            return this.ordinal();
        }
    }

    public byte[] serialize() {
        return ProtoSerializer.serialize(
                new ProtoSerializer.ProtoVarInt(1, focusType().serialize())
        );
    }

}
