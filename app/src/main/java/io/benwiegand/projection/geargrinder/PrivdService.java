package io.benwiegand.projection.geargrinder;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;
import java.util.function.Consumer;

import io.benwiegand.projection.geargrinder.callback.IPCConnectionListener;
import io.benwiegand.projection.geargrinder.privileged.PrivdIPCConnection;
import io.benwiegand.projection.geargrinder.privileged.RootPrivdLauncher;
import io.benwiegand.projection.libprivd.data.ActivityLaunchParams;
import io.benwiegand.projection.libprivd.data.InjectMotionEventParams;
import io.benwiegand.projection.libprivd.data.IntResult;
import io.benwiegand.projection.libprivd.ipc.IPCConstants;
import io.benwiegand.projection.libprivd.sec.Sec;

public class PrivdService extends Service implements IPCConnectionListener {
    private static final String TAG = PrivdService.class.getSimpleName();

    private static final Object lock = new Object();

    private final ServiceBinder binder = new ServiceBinder();

    private RootPrivdLauncher privdLauncher;
    private PrivdIPCConnection connection = null;

    private final Queue<Consumer<PrivdIPCConnection>> ipcConnectionListeners = new LinkedList<>();

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");
        privdLauncher = new RootPrivdLauncher(this, this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        privdLauncher.destroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            privdLauncher.launchRoot();
        } catch (IOException e) {
            Log.e(TAG, "failed to launch daemon");
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onPrivdConnected(PrivdIPCConnection connection) {
        synchronized (lock) {
            Log.i(TAG, "IPC connected");
            this.connection = connection;

            while (!ipcConnectionListeners.isEmpty()) {
                Consumer<PrivdIPCConnection> listener = ipcConnectionListeners.poll();
                if (listener == null) continue;
                listener.accept(connection);
            }

        }
    }

    @Override
    public void onPrivdDisconnected() {
        synchronized (lock) {
            Log.i(TAG, "IPC disconnected");
            connection = null;
        }
    }

    public class ServiceBinder extends Binder {

        public void addDaemonListener(Consumer<PrivdIPCConnection> listener) {
            synchronized (lock) {
                if (connection != null && connection.isAlive()) {
                    listener.accept(connection);
                } else {
                    ipcConnectionListeners.add(listener);
                }
            }
        }

        public void launchDaemon() throws IOException {
            synchronized (lock) {
                if (connection != null && connection.isAlive()) return;
                privdLauncher.launchRoot();
            }
        }

        public Sec<Boolean> injectMotionEvent(InjectMotionEventParams params) {
            synchronized (lock) {
                if (connection == null)
                    return Sec.premeditatedError(new IOException("daemon not connected"));

                return connection.send(IPCConstants.COMMAND_INJECT_MOTION_EVENT, params)
                        .map(r -> switch (r.status) {
                            case IPCConstants.REPLY_SUCCESS -> r.data[0] != 0;
                            case IPCConstants.REPLY_FAILURE -> throw new RuntimeException("got REPLY_FAILURE from daemon");
                            default -> throw new AssertionError("unexpected reply status from daemon: " + r.status);
                        });
            }
        }

        public Sec<Integer> launchActivity(ActivityLaunchParams params) {
            synchronized (lock) {
                if (connection == null)
                    return Sec.premeditatedError(new IOException("daemon not connected"));

                return connection.send(IPCConstants.COMMAND_LAUNCH_ACTIVITY, params)
                        .map(r -> switch (r.status) {
                            case IPCConstants.REPLY_SUCCESS -> r.unmarshallParcelableData(IntResult.CREATOR).getResult();
                            case IPCConstants.REPLY_FAILURE -> throw new RuntimeException("got REPLY_FAILURE from daemon");
                            default -> throw new AssertionError("unexpected reply status from daemon: " + r.status);
                        });
            }
        }

    }
}
