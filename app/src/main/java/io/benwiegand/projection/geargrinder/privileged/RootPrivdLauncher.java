package io.benwiegand.projection.geargrinder.privileged;

import static io.benwiegand.projection.libprivd.ipc.IPCConstants.ENV_PORT;
import static io.benwiegand.projection.libprivd.ipc.IPCConstants.ENV_TOKEN_A;
import static io.benwiegand.projection.libprivd.ipc.IPCConstants.ENV_TOKEN_B;

import android.content.Context;
import android.util.Base64;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import io.benwiegand.projection.geargrinder.callback.IPCConnectionListener;

public class RootPrivdLauncher {
    private static final String TAG = RootPrivdLauncher.class.getSimpleName();

    private static final String PRIVD_FILE_NAME = "privd.jar";
    private static final String ENV_CLASSPATH = "CLASSPATH";

    private final Context context;
    private final IPCConnectionListener connectionListener;
    private final File daemonFile;

    private IPCServer server = null;
    private Process rootProcess = null;

    public RootPrivdLauncher(Context context, IPCConnectionListener connectionListener) {
        this.context = context;
        this.connectionListener = connectionListener;
        daemonFile = context.getFilesDir().toPath().resolve(PRIVD_FILE_NAME).toFile();
    }

    public void destroy() {
        killRootProcess();
        killServer();
    }

    public void launchRoot() throws IOException {
        killRootProcess();
        copyDaemon();
        startServer();
        executeDaemon();
    }

    private void killServer() {
        if (server == null) return;
        Log.i(TAG, "closing IPC server");
        server.close();
        server = null;
    }

    private void killRootProcess() {
        if (rootProcess == null) return;
        Log.i(TAG, "killing privd root process");
        rootProcess.destroyForcibly();
        rootProcess = null;
    }

    private void startServer() throws IOException {
        if (server != null) {
            server.rotate();
            return;
        }

        Log.i(TAG, "starting IPC server");
        server = new IPCServer(connectionListener);
        server.start();
    }

    private void executeDaemon() throws IOException {
        assert rootProcess == null;
        assert server != null;
        Log.i(TAG, "launching privd as root");
        ProcessBuilder procBuilder = new ProcessBuilder("su", "-c", "app_process /system/bin --nice-name=Geargrinder-privd io.benwiegand.projection.geargrinder.privd.Main");
        procBuilder.environment().put(ENV_CLASSPATH, daemonFile.getAbsolutePath());
        procBuilder.environment().put(ENV_PORT, String.valueOf(server.getPort()));
        // tokens are swapped for app/daemon
        procBuilder.environment().put(ENV_TOKEN_A, Base64.encodeToString(server.getTokenB(), Base64.NO_WRAP));
        procBuilder.environment().put(ENV_TOKEN_B, Base64.encodeToString(server.getTokenA(), Base64.NO_WRAP));
        rootProcess = procBuilder.start();
    }

    private void copyDaemon() throws IOException {
        if (daemonFile.isFile()) {
            Log.d(TAG, "deleting " + daemonFile);
            if (!daemonFile.delete()) throw new IOException("failed to delete existing copy of the daemon");
        }

        Log.i(TAG, "copying " + PRIVD_FILE_NAME + " to " + daemonFile);
        try (InputStream is = context.getAssets().open(PRIVD_FILE_NAME);
             FileOutputStream os = new FileOutputStream(daemonFile)) {
            int len;
            byte[] buffer = new byte[4096];
            while ((len = is.read(buffer)) >= 0)
                os.write(buffer, 0, len);
        }
    }

}
