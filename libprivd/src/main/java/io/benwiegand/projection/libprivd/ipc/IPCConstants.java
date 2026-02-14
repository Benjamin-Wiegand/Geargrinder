package io.benwiegand.projection.libprivd.ipc;

public class IPCConstants {

    public static final long PING_INTERVAL = 1000;
    public static final String ENV_PORT = "PORT";
    public static final String ENV_TOKEN_A = "TOKEN_A";
    public static final String ENV_TOKEN_B = "TOKEN_B";


    public static final int FLAG_REPLY = 1;

    public static final int COMMAND_PING = 0;
    public static final int COMMAND_INJECT_MOTION_EVENT = 10;

    public static final int REPLY_SUCCESS = 0;
    public static final int REPLY_FAILURE = 1;
}
