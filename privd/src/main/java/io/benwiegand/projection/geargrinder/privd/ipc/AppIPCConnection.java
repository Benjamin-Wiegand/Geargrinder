package io.benwiegand.projection.geargrinder.privd.ipc;

import android.util.Log;

import java.io.IOException;
import java.net.Socket;

import io.benwiegand.projection.libprivd.ipc.IPCConnection;
import io.benwiegand.projection.libprivd.ipc.IPCConstants;

public class AppIPCConnection extends IPCConnection {
    private static final String TAG = AppIPCConnection.class.getSimpleName();

    public AppIPCConnection(Socket socket, byte[] tokenA, byte[] tokenB) throws IOException {
        super(socket, tokenA, tokenB);
    }

    @Override
    protected String getTag() {
        return TAG;
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
        // daemon has no reason to run without an app
        Log.e(TAG, "connection closed");
        System.exit(0);
    }
}
