package io.benwiegand.projection.geargrinder.logs;

import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LogcatReader {
    private static final String TAG = LogcatReader.class.getSimpleName();

    private static final int LOGCAT_UI_FIXED_TAG_LENGTH = 15;

    private static final Pattern LOGCAT_REGEX = Pattern.compile("^(?<date>[0-9]+-[0-9]+) +(?<time>[0-9]+:[0-9]+:[0-9]+.[0-9]+) +(?<pid>[0-9]+) +(?<tid>[0-9]+) +(?<level>[FEWIVD]) +(?<tag>[^:]+): ?(?<msg>.*)$");

    public interface UiLogListener {
        void onLog(String level, String text);
    }

    private final Thread readThread = new Thread(this::readLoop);
    private boolean alive = true;

    private final UiLogListener uiListener;
    private Recording activeRecording = null;

    public LogcatReader(UiLogListener uiListener) {
        this.uiListener = uiListener;
    }

    public void start() {
        readThread.start();
    }

    public void destroy() {
        alive = false;
        stopRecording();
    }

    private void onLine(String rawText) {

        Recording recording = activeRecording;
        if (recording != null) {
            recording.onLine(rawText);
        }

        Matcher m = LOGCAT_REGEX.matcher(rawText);
        if (m.matches()) {
            String time = m.group("time");
            String level = m.group("level");
            String tag = m.group("tag");
            String msg = m.group("msg");

            if (tag != null) {
                if (tag.length() > LOGCAT_UI_FIXED_TAG_LENGTH)
                    tag = tag.substring(0, LOGCAT_UI_FIXED_TAG_LENGTH);

                StringBuilder tagBuilder = new StringBuilder(tag);
                while (tagBuilder.length() < LOGCAT_UI_FIXED_TAG_LENGTH)
                    tagBuilder.insert(0, " ");
                tag = String.valueOf(tagBuilder);
            }

            uiListener.onLog(level, time + " " + tag + " " + level + ": " + msg);
        } else {
            uiListener.onLog(null, rawText);
        }

    }

    private void readLoop() {
        char[] buffer = new char[65535];
        int len, ret, sep;
        Process p = null;
        try {
             p = new ProcessBuilder("logcat", "-D", "-T", "1000")
                    .start();

            InputStreamReader reader = new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8);
            len = 0;
            while (alive) {
                ret = reader.read(buffer, len, buffer.length - len);
                if (ret < 0) break;
                len += ret;

                sep = 0;
                for (int i = 0; i < len; i++) {
                    if (buffer[i] == '\n') {
                        String line = new String(buffer, sep, i - sep);
                        onLine(line);
                        sep = i + 1;
                    }
                }

                if (sep == len) {
                    len = 0;
                    continue;
                }

                // partial line
                System.arraycopy(buffer, sep, buffer, 0, len - sep);
                len -= sep;
            }

        } catch (IOException e) {
            Log.e(TAG, "IOException in read loop", e);
        } finally {
            if (p != null && p.isAlive())
                p.destroyForcibly();
        }
    }


    private static class Recording {
        private final Writer writer;
        private Throwable error = null;
        private Recording(Writer writer) {
            this.writer = writer;
        }

        private void onLine(String line) {
            if (error != null) return;

            try {
                writer.write(line + "\n");
                writer.flush();
            } catch (Throwable t) {
                Log.e(TAG, "failed to write line", t);
                error = t;
            }
        }

    }


    public void startRecording(File file) throws IOException {
        if (activeRecording != null) throw new IllegalStateException("recording already active");
        activeRecording = new Recording(new FileWriter(file));
    }

    public boolean isRecording() {
        return activeRecording != null;
    }

    public Throwable stopRecording() {
        if (activeRecording == null) return null;
        Throwable error = activeRecording.error;
        if (error != null) {
            try {
                activeRecording.writer.flush();
                activeRecording.writer.close();
            } catch (IOException e) {
                error = e;
            }
        }
        activeRecording = null;
        return error;
    }

    public void addMarker() {
        for (int i = 0; i < 10; i++) {
            onLine("========================================================");
        }
    }


}
