package io.benwiegand.projection.geargrinder.proto.data.writable.av;

import io.benwiegand.projection.geargrinder.proto.ProtoSerializer;

public record AVSetupRequest(
        int type
) {

    // TODO: unsure if this is correct
    public static int AV_SETUP_REQUEST_TYPE_AUDIO = 1;
    public static int AV_SETUP_REQUEST_TYPE_VIDEO = 3;

    public static AVSetupRequest createAudio() {
        return new AVSetupRequest(AV_SETUP_REQUEST_TYPE_AUDIO);
    }

    public static AVSetupRequest createVideo() {
        return new AVSetupRequest(AV_SETUP_REQUEST_TYPE_VIDEO);
    }

    public byte[] serialize() {
        return ProtoSerializer.serialize(
                new ProtoSerializer.Proto32(1, type())
        );
    }
}
