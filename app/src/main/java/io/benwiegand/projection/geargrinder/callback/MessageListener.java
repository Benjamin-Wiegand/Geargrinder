package io.benwiegand.projection.geargrinder.callback;

public interface MessageListener {

    void onMessage(int channelId, int flags, byte[] buffer, int payloadOffset, int payloadLength);
}
