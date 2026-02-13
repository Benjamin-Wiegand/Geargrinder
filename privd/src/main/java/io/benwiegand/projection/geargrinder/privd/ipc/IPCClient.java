package io.benwiegand.projection.geargrinder.privd.ipc;

import android.util.Log;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

import io.benwiegand.projection.libprivd.ipc.IPCConnection;

public class IPCClient {
    private static final String TAG = IPCClient.class.getSimpleName();

    private static final InetAddress SERVER_ADDRESS = InetAddress.getLoopbackAddress();

    private final Socket socket;
    private final int port;

    private final byte[] tokenA;
    private final byte[] tokenB;

    public IPCClient(int port, byte[] tokenA, byte[] tokenB) throws IOException {
        // TODO
//        socket = SSLSocketFactory.getDefault().createSocket();
        socket = new Socket();
        this.port = port;
        this.tokenA = tokenA;
        this.tokenB = tokenB;
    }

    public IPCConnection connect() throws IOException {
        InetSocketAddress socketAddress = new InetSocketAddress(SERVER_ADDRESS, port);
        Log.i(TAG, "connecting to " + socketAddress);
        socket.connect(socketAddress);
        return new AppIPCConnection(socket, tokenA, tokenB);
    }
}
