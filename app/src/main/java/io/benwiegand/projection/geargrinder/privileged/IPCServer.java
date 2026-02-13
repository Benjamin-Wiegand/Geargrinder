package io.benwiegand.projection.geargrinder.privileged;

import android.util.Log;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.SecureRandom;

import io.benwiegand.projection.geargrinder.callback.IPCConnectionListener;

public class IPCServer {
    private static final String TAG = IPCServer.class.getSimpleName();

    private static final InetAddress BIND_ADDRESS = InetAddress.getLoopbackAddress();
    private static final int BIND_PORT = 0; // auto
    private static final int MAX_CONNECTIONS = 5;   // for resiliency if something else connects first

    private static final int AUTHENTICATION_SECRET_LENGTH = 1024;

    private final Thread serverThread;
    private final ServerSocket server;
    private int port = -1;
    private boolean dead = false;

    private final SecureRandom random;
    private final byte[] tokenA = new byte[AUTHENTICATION_SECRET_LENGTH];
    private final byte[] tokenB = new byte[AUTHENTICATION_SECRET_LENGTH];

    private final PrivdIPCConnection[] connections = new PrivdIPCConnection[MAX_CONNECTIONS];
    private int activeConnection = 0;

    private final IPCConnectionListener connectionListener;

    public IPCServer(IPCConnectionListener connectionListener) {
        this.connectionListener = connectionListener;
        try {
            serverThread = new Thread(this::serverLoop, "geargrinder-ipc-server");
//            server = SSLServerSocketFactory.getDefault().createServerSocket();
            server = new ServerSocket();
            random = SecureRandom.getInstanceStrong();
            rotate();
        } catch (Throwable t) {
            Log.e(TAG, "failed to init IPC server", t);
            throw new RuntimeException(t);
        }
    }

    public interface ConnectionInitCallback {
        void onInitComplete(boolean success);
    }

    public void close() {
        if (dead) return;
        dead = true;
        serverThread.interrupt();
        try {
            if (!server.isClosed())
                server.close();
        } catch (IOException e) {
            Log.e(TAG, "IOException while closing socket", e);
        }

        synchronized (connections) {
            for (int i = 0; i < connections.length; i++) {
                if (connections[i] == null) continue;
                connections[i].close();
                connections[i] = null;
            }
        }
    }

    public void rotate() {
        // keeps the same port number but with different tokens.
        // this is preferred over spinning up a new server for security.
        Log.i(TAG, "generating new tokens");
        random.nextBytes(tokenA);
        random.nextBytes(tokenB);
    }

    public void start() throws IOException {
        server.bind(new InetSocketAddress(BIND_ADDRESS, BIND_PORT));
        Log.i(TAG, "IPC server started on " + server.getLocalSocketAddress());
        port = server.getLocalPort();
        serverThread.start();
    }

    public byte[] getTokenA() {
        return tokenA;
    }

    public byte[] getTokenB() {
        return tokenB;
    }

    public int getPort() {
        return port;
    }

    public PrivdIPCConnection getActiveConnection() {
        synchronized (connections) {
            if (connections[activeConnection] == null) return null;
            if (!connections[activeConnection].isAlive()) return null;
            return connections[activeConnection];
        }
    }

    private int findFreeConnectionIdLocked() {
        int id = -1;
        for (int i = 0; i < connections.length; i++) {
            if (connections[i] != null) continue;
            id = i;
            break;
        }
        return id;
    }

    private void serverLoop() {
        try {
            Log.d(TAG, "server loop start");
            while (!dead) {
                Socket socket = server.accept();
                if (getActiveConnection() != null) {
                    Log.e(TAG, "already connected! refusing connection from " + socket.getRemoteSocketAddress());
                    try {
                        socket.close();
                    } catch (Throwable ignored) {}
                    continue;
                }

                synchronized (connections) {
                    Log.d(TAG, "connection from " + socket.getRemoteSocketAddress());

                    int connectionId = findFreeConnectionIdLocked();
                    if (connectionId == -1) {
                        Log.e(TAG, "too many open connections, dropping");
                        continue;
                    }

                    connections[connectionId] = new PrivdIPCConnection(socket, tokenA, tokenB, success -> {
                        synchronized (connections) {
                            if (!success) {
                                connections[connectionId] = null;
                                return;
                            }

                            activeConnection = connectionId;
                            connectionListener.onPrivdConnected(connections[connectionId]);
                        }
                    });
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "IOException during IPC server loop", e);
        } finally {
            close();
        }
    }
}
