package io.benwiegand.projection.geargrinder.privileged;

import static io.benwiegand.projection.libprivd.ipc.IPCConstants.ENV_TOKEN;
import static io.benwiegand.projection.libprivd.ipc.IPCConstants.ENV_TOKEN_PATH;
import static io.benwiegand.projection.libprivd.ipc.IPCConstants.TOKEN_LENGTH;

import android.content.Context;
import android.util.Base64;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Random;

import io.benwiegand.projection.geargrinder.util.ShellUtil;

public class RootPrivdLauncher {
    private static final String TAG = RootPrivdLauncher.class.getSimpleName();

    private static final String PRIVD_FILE_NAME = "privd.jar";
    private static final String LAUNCH_SCRIPT_FILE_NAME = "launch-privd.sh";
    public static final String TOKEN_FILE_NAME = "privd-token.bin";

    private final Context context;
    private final File daemonFile;
    private final File launchScriptFile;
    private final File tokenFile;

    private boolean init = false;
    private Process rootProcess = null;
    private byte[] token = null;

    public RootPrivdLauncher(Context context) {
        this.context = context;
        daemonFile = context.getCodeCacheDir().toPath().resolve(PRIVD_FILE_NAME).toFile();
        launchScriptFile = context.getCodeCacheDir().toPath().resolve(LAUNCH_SCRIPT_FILE_NAME).toFile();
        tokenFile = context.getFilesDir().toPath().resolve(TOKEN_FILE_NAME).toFile();

        // TODO: for launching non-root (less secure)
//        daemonFile = context.getExternalFilesDir(null).toPath().resolve(PRIVD_FILE_NAME).toFile();
//        launchScriptFile = context.getExternalFilesDir(null).toPath().resolve(LAUNCH_SCRIPT_FILE_NAME).toFile();
//        tokenFile = context.getExternalFilesDir(null).toPath().resolve(TOKEN_FILE_NAME).toFile();
    }

    public void destroy() {
        killRootProcess();
    }

    private void init() throws IOException {
        Log.i(TAG, "init privd launcher");
        copyDaemon();
        generateToken();
        generateScript();
        init = true;
    }

    public void launchRoot() throws IOException {
        if (!init) init();

        Log.i(TAG, "launching privd as root");
        killRootProcess();
        executeDaemonRoot();
    }

    private void killRootProcess() {
        if (rootProcess == null) return;
        Log.d(TAG, "killing privd root process");
        rootProcess.destroyForcibly();
        rootProcess = null;
    }

    private void executeDaemonRoot() throws IOException {
        assert rootProcess == null;

        String command = "sh " + ShellUtil.wrapSingleQuote(launchScriptFile.getAbsolutePath());
        ProcessBuilder processBuilder = new ProcessBuilder("su", "-c", command);

        // privd will fall back to reading the token file if this doesn't make it through su
        if (token != null)
            processBuilder.environment().put(ENV_TOKEN, Base64.encodeToString(token, Base64.NO_WRAP));

        rootProcess = processBuilder.start();
    }

    private void copyDaemon() throws IOException {
        if (daemonFile.isFile()) {
            Log.d(TAG, "deleting " + daemonFile);
            if (!daemonFile.delete()) throw new IOException("failed to delete existing copy of the daemon");
        }

        Log.v(TAG, "copying " + PRIVD_FILE_NAME + " to " + daemonFile);
        try (InputStream is = context.getAssets().open(PRIVD_FILE_NAME);
             FileOutputStream os = new FileOutputStream(daemonFile)) {
            int len;
            byte[] buffer = new byte[4096];
            while ((len = is.read(buffer)) >= 0)
                os.write(buffer, 0, len);
        }
    }

    private void generateToken() {
        try {
            byte[] newToken = new byte[TOKEN_LENGTH];

            Log.d(TAG, "generating random " + newToken.length + " byte token");
            Random random = SecureRandom.getInstanceStrong();
            random.nextBytes(newToken);

            Log.v(TAG, "saving token to " + tokenFile);
            try (FileOutputStream os = new FileOutputStream(tokenFile)) {
                os.write(newToken);
            }

            token = newToken;

        } catch (Throwable t) {
            Log.e(TAG, "failed to generate token", t);
            throw new RuntimeException(t);
        }
    }

    private void generateScript() throws IOException {
        String script = """
        #!/bin/sh
        
        set -e
        
        # generated vars
        """;

        script += "INIT_JAR_PATH=" + ShellUtil.wrapSingleQuote(daemonFile.getAbsolutePath()) + "\n";
        script += "export " + ENV_TOKEN_PATH + "=" + ShellUtil.wrapSingleQuote(tokenFile.getAbsolutePath()) + "\n";

        script += """
        # end generated vars
        
        PRIVD_NAME=Geargrinder-privd
        EXEC_JAR_PATH="/data/local/tmp/geargrinder-privd.jar"
        
        if ! [[ -f "$INIT_JAR_PATH" ]]; then
            echo "file not found: $INIT_JAR_PATH"
            exit 1
        fi
        
        cp -v "$INIT_JAR_PATH" "$EXEC_JAR_PATH"
        chown -v "`id -u`:`id -g`" "$EXEC_JAR_PATH"
        chmod -v 0400 "$EXEC_JAR_PATH"
        export CLASSPATH="$EXEC_JAR_PATH"
        
        set +e
        
        echo "launching $PRIVD_NAME as $USER (`id -u`)"
        app_process /system/bin --nice-name=$PRIVD_NAME io.benwiegand.projection.geargrinder.privd.Main
        exit_code=$?
        echo "app_process exited with code $exit_code"
        exit $exit_code
        """;

        Log.v(TAG, "saving launch script to " + launchScriptFile);
        try (FileOutputStream os = new FileOutputStream(launchScriptFile)) {
            os.write(script.getBytes(StandardCharsets.UTF_8));
        }
    }

    public static byte[] readToken(Context context) throws IOException {
        File tokenFile = context.getFilesDir().toPath().resolve(TOKEN_FILE_NAME).toFile();
        if (!tokenFile.isFile()) return null;

        try (FileInputStream is = new FileInputStream(tokenFile)) {
            byte[] token = new byte[TOKEN_LENGTH];
            int offset = 0;
            int len;
            while (offset < TOKEN_LENGTH) {
                len = is.read(token, offset, TOKEN_LENGTH - offset);
                if (len < 0) {
                    Log.e(TAG, "stored token too short (" + len + " / " + TOKEN_LENGTH + " bytes): " + tokenFile);
                    return null;
                }

                offset += len;
            }

            return token;
        }
    }

}
