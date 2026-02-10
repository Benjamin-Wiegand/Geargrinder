package io.benwiegand.projection.geargrinder.shell;

import android.util.Log;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

// the most basic root shell you can get
// TODO: better shell
public class RootShell {
    private static final String TAG = RootShell.class.getSimpleName();

    private final Process process;

    public RootShell() throws IOException {
        process = new ProcessBuilder("su").start();
    }

    public void writeLine(String line) throws IOException {
        Log.d(TAG, "writing line: " + line);
        OutputStream os = process.getOutputStream();
        os.write((line + "\n").getBytes(StandardCharsets.UTF_8));
        os.flush();
    }

    public void destroy() {
        process.destroyForcibly();
    }

}
