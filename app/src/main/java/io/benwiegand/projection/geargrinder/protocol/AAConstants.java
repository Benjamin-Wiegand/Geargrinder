package io.benwiegand.projection.geargrinder.protocol;

public class AAConstants {

    // channels
    // as far as I can tell, this is the only hard-coded channel id
    // other channel ids are defined in the ServiceDiscoveryResponse
    public static final int CHANNEL_CONTROL = 0x00;

    // control messages
    public static final int CMD_VERSION_REQUEST = 0x0001;
    public static final int CMD_VERSION_RESPONSE = 0x0002;
    public static final int CMD_SSL_HANDSHAKE = 0x0003;
    public static final int CMD_AUTH_COMPLETE = 0x0004;
    public static final int CMD_SERVICE_DISCOVERY_REQUEST = 0x0005;
    public static final int CMD_SERVICE_DISCOVERY_RESPONSE = 0x0006;
    public static final int CMD_PING_REQUEST = 0x000b;
    public static final int CMD_PING_RESPONSE = 0x000c;


    // all channels
    public static final int CMD_CHANNEL_OPEN_REQUEST = 0x0007;
    public static final int CMD_CHANNEL_OPEN_RESPONSE = 0x0008;


    // audio/video channel messages
    public static final int AV_CMD_MEDIA_WITH_TIMESTAMP = 0x0000;
    public static final int AV_CMD_MEDIA = 0x0001;
    public static final int AV_CMD_SETUP_REQUEST = 0x8000;
    public static final int AV_CMD_START = 0x8001;
    public static final int AV_CMD_STOP = 0x8002;
    public static final int AV_CMD_SETUP_RESPONSE = 0x8003;
    public static final int AV_CMD_MEDIA_ACK = 0x8004;
    public static final int AV_CMD_VIDEO_FOCUS_REQUEST = 0x8007;
    public static final int AV_CMD_VIDEO_FOCUSED = 0x8008;

    // input channel messages
    public static final int INPUT_CMD_EVENT = 0x8001;
    public static final int INPUT_CMD_BINDING_REQUEST = 0x8002;
    public static final int INPUT_CMD_BINDING_RESPONSE = 0x8003;
}
