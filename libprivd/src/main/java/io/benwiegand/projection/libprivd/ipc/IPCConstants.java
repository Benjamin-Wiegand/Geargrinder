package io.benwiegand.projection.libprivd.ipc;

public class IPCConstants {

    public static final long PING_INTERVAL = 1000;
    public static final long PING_TIMEOUT = 5000;
    public static final long BIND_TIMEOUT = 20000;

    public static final int TOKEN_LENGTH = 1024;

    public static final String ENV_TOKEN = "TOKEN";
    public static final String ENV_TOKEN_PATH = "TOKEN_PATH";

    public static final String APP_PKG_NAME = "io.benwiegand.projection.geargrinder";

    public static final String INTENT_ACTION_BIND_PRIVD = "io.benwiegand.projection.geargrinder.BIND_PRIVD";
    public static final String INTENT_EXTRA_BINDER = "binder";
    public static final String INTENT_EXTRA_TOKEN = "token";

}
