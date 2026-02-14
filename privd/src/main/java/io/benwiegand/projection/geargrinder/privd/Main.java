package io.benwiegand.projection.geargrinder.privd;

import static io.benwiegand.projection.libprivd.ipc.IPCConstants.COMMAND_PING;
import static io.benwiegand.projection.libprivd.ipc.IPCConstants.ENV_PORT;
import static io.benwiegand.projection.libprivd.ipc.IPCConstants.ENV_TOKEN_A;
import static io.benwiegand.projection.libprivd.ipc.IPCConstants.ENV_TOKEN_B;
import static io.benwiegand.projection.libprivd.ipc.IPCConstants.PING_INTERVAL;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;

import io.benwiegand.projection.libprivd.reflection.ReflectionException;
import io.benwiegand.projection.geargrinder.privd.ipc.IPCClient;
import io.benwiegand.projection.geargrinder.privd.reflected.ReflectedActivityThread;
import io.benwiegand.projection.libprivd.ipc.IPCConnection;

public class Main {
    private static final String TAG = "privd-" + Main.class.getSimpleName();

    private static final long IPC_INIT_TIMEOUT = 5000;

    private static void pingTask(Handler handler, IPCConnection connection) {
        connection.send(COMMAND_PING)
                .doOnResult(reply -> Log.i(TAG, "pong: " + reply.status))
                .doOnError(t -> {
                    Log.e(TAG, "failed to ping", t);
                    System.exit(1);
                })
                .callMeWhenDone();

        if (!handler.postDelayed(() -> pingTask(handler, connection), PING_INTERVAL)) {
            Log.w(TAG, "failed to post next ping task, bailing");
            System.exit(0);
        }
    }

    public static void main(String[] args) {
        Log.i(TAG, "Geargrinder privd");


        // env
        int port;
        byte[] tokenA;
        byte[] tokenB;
        try {
            String portString = System.getenv(ENV_PORT);
            String tokenAString = System.getenv(ENV_TOKEN_A);
            String tokenBString = System.getenv(ENV_TOKEN_B);

            if (portString == null || tokenAString == null || tokenBString == null)
                throw new AssertionError("missing required environment variable(s)");

            port = Integer.parseInt(portString);
            tokenA = Base64.decode(tokenAString, 0);
            tokenB = Base64.decode(tokenBString, 0);

        } catch (Throwable t) {
            Log.e(TAG, "failed to parse environment", t);
            System.exit(1);
            return;
        }


        // context
        Looper.prepareMainLooper();
        Context context;
        try {
            ReflectedActivityThread activityThread = new ReflectedActivityThread();

            context = activityThread.getSystemContext();
            Log.i(TAG, "got a system context: " + context);

        } catch (ReflectionException e) {
            Log.e(TAG, "failed to get system context", e);
            System.exit(1);
            return;
        }


        // ipc
        IPCClient ipcClient;
        IPCConnection connection;
        try {
            ipcClient = new IPCClient(context, port, tokenA, tokenB);
            connection = ipcClient.connect();
            connection.waitForInit(IPC_INIT_TIMEOUT);
        } catch (Throwable t) {
            Log.e(TAG, "failed to open IPC connection", t);
            System.exit(1);
            return;
        }


        // ping loop
        Handler handler = new Handler(Looper.getMainLooper());
        if (!handler.post(() -> pingTask(handler, connection))) {
            Log.e(TAG, "failed to post ping task");
            System.exit(1);
        }


        // loop until death
        Looper.loop();
    }
}
