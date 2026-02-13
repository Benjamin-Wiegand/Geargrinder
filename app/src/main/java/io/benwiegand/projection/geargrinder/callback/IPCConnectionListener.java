package io.benwiegand.projection.geargrinder.callback;

import io.benwiegand.projection.geargrinder.privileged.PrivdIPCConnection;

public interface IPCConnectionListener {
    void onPrivdConnected(PrivdIPCConnection connection);
}
