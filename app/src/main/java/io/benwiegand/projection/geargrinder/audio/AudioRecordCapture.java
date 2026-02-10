package io.benwiegand.projection.geargrinder.audio;

import static android.media.AudioTimestamp.TIMEBASE_BOOTTIME;

import android.Manifest;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.AudioTimestamp;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.annotation.RequiresPermission;

import io.benwiegand.projection.geargrinder.proto.data.readable.av.preset.AudioPreset;

public class AudioRecordCapture implements AudioCapture {
    private static final String TAG = AudioRecordCapture.class.getSimpleName();

    private final AudioRecord audioRecord;
    private final AudioTimestamp timestamp;

    @RequiresApi(api = Build.VERSION_CODES.Q)
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    public AudioRecordCapture(AudioPlaybackCaptureConfiguration config, AudioPreset preset, int bufferSize) {

        audioRecord = new AudioRecord.Builder()
                .setAudioFormat(preset.createAudioFormat())
                .setBufferSizeInBytes(bufferSize)
                .setAudioPlaybackCaptureConfig(config)
                .build();

        timestamp = new AudioTimestamp();

    }

    @Override
    public void begin() {
        audioRecord.startRecording();
    }

    @Override
    public void destroy() {
        audioRecord.release();
    }

    @Override
    public void nextBuffer(Result result, byte[] buffer, int offset, int length) {
        int ret;

        if (audioRecord.getState() == AudioRecord.STATE_UNINITIALIZED) {
            Log.w(TAG, "not initialized yet");
            result.error = Error.TRY_AGAIN;
            return;
        }

        ret = audioRecord.getTimestamp(timestamp, TIMEBASE_BOOTTIME);
        switch (ret) {
            case AudioRecord.SUCCESS -> {}
            case AudioRecord.ERROR_INVALID_OPERATION -> {
                // not ready yet
                result.error = Error.TRY_AGAIN;
                return;
            }
            default -> {
                Log.wtf(TAG, "unexpected error code while getting timestamp: " + ret);
                result.error = Error.TRY_AGAIN;
                return;
            }
        }

        ret = audioRecord.read(buffer, offset, length);
        if (ret < 0) {
            Log.e(TAG, "AudioRecord error: " + ret);
            switch (ret) {
                case AudioRecord.ERROR,
                     AudioRecord.ERROR_BAD_VALUE -> {
                    result.error = Error.FAILURE;
                    return;
                }
                case AudioRecord.ERROR_INVALID_OPERATION -> {
                    result.error = Error.TRY_AGAIN;
                    return;
                }
                case AudioRecord.ERROR_DEAD_OBJECT -> {
                    result.error = Error.END_OF_STREAM;
                    return;
                }
            }
        } else if (ret == 0) {
            Log.w(TAG, "empty buffer");
            result.error = Error.TRY_AGAIN;
            return;
        }

        boolean silent = true;
        for (int i = offset; i < offset + length; i++) {
            if (buffer[i] == 0) continue;
            silent = false;
        }

        result.error = Error.NO_ERROR;
        result.length = ret;
        result.timestamp = timestamp.nanoTime;
        result.silent = silent;
    }
}
