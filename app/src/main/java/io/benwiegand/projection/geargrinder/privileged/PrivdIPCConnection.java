package io.benwiegand.projection.geargrinder.privileged;

import android.util.Log;

import java.io.IOException;
import java.net.Socket;

import io.benwiegand.projection.libprivd.ipc.IPCConnection;
import io.benwiegand.projection.libprivd.ipc.IPCConstants;

public class PrivdIPCConnection extends IPCConnection {
    private static final String TAG = PrivdIPCConnection.class.getSimpleName();

    private final IPCServer.ConnectionInitCallback initCallback;

    public PrivdIPCConnection(Socket socket, byte[] tokenA, byte[] tokenB, IPCServer.ConnectionInitCallback initCallback) throws IOException {
        super(socket, tokenA, tokenB);
        this.initCallback = initCallback;
    }

    @Override
    protected String getTag() {
        return TAG;
    }

    @Override
    protected void onInitComplete(boolean success) {
        initCallback.onInitComplete(success);
    }

    @Override
    protected Reply onCommand(int command, byte[] data, int offset, int length) {
        return switch (command) {
            case IPCConstants.COMMAND_PING -> {
                Log.d(TAG, "ping");
                yield new Reply(IPCConstants.REPLY_SUCCESS);
            }
            default -> {
                Log.wtf(TAG, "unhandled command: " + command);
                yield new Reply(IPCConstants.REPLY_FAILURE);
            }
        };
    }

    @Override
    protected void onClose() {
        // TODO
        Log.e(TAG, "connection closed");
    }
}
