package io.benwiegand.projection.geargrinder.privileged;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class RootPrivdLauncher {
    private static final String TAG = RootPrivdLauncher.class.getSimpleName();

    private static final String PRIVD_FILE_NAME = "privd.jar";

    private final Context context;
    private final File daemonFile;

    private Process rootProcess = null;

    public RootPrivdLauncher(Context context) {
        this.context = context;
        daemonFile = context.getFilesDir().toPath().resolve(PRIVD_FILE_NAME).toFile();
    }

    public void destroy() {
        killRootProcess();
    }

    public void launchRoot() throws IOException {
        killRootProcess();
        copyDaemon();

        Log.i(TAG, "launching privd as root");
        ProcessBuilder procBuilder = new ProcessBuilder("su", "-c", "app_process /system/bin --nice-name=Geargrinder-privd io.benwiegand.projection.geargrinder.privd.Main");
        procBuilder.environment().put("CLASSPATH", daemonFile.getAbsolutePath());
        rootProcess = procBuilder.start();
    }

    private void killRootProcess() {
        if (rootProcess != null) {
            Log.i(TAG, "killing privd root process");
            rootProcess.destroyForcibly();
            rootProcess = null;
        }
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
