package io.benwiegand.projection.geargrinder;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.IOException;

import io.benwiegand.projection.geargrinder.logs.LogUiAdapter;
import io.benwiegand.projection.geargrinder.logs.LogcatReader;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();

    private LogcatReader logcatReader;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "requesting notification permission");
            if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.post_notifications_permission_request)
                        .setMessage(R.string.post_notifications_permission_rationale)
                        .setPositiveButton(R.string.grant_permission_button, (d, i) ->
                                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 69))
                        .setNegativeButton(R.string.not_now_button, null)
                        .setCancelable(false)
                        .show();
            } else {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 69);
            }
        }

        findViewById(R.id.force_start_service_button).setOnClickListener(v ->
                startService(new Intent(this, ConnectionService.class)
                        .setAction(ConnectionService.INTENT_ACTION_CONNECT_USB)));

        findViewById(R.id.start_audio_capture_button).setOnClickListener(v ->
                startActivity(new Intent(this, ConnectionRequestActivity.class)
                        .setAction(ConnectionRequestActivity.INTENT_ACTION_REQUEST_MEDIA_PROJECTION)));

        findViewById(R.id.log_marker_button).setOnClickListener(v -> logcatReader.addMarker());

        findViewById(R.id.toggle_recording_button).setOnClickListener(v -> {
            if (logcatReader.isRecording()) {
                Throwable error = logcatReader.stopRecording();
                if (error == null) {
                    Toast.makeText(this, "recording stopped", Toast.LENGTH_SHORT).show();
                    return;
                }
                Log.e(TAG, "error during recording", error);
                new AlertDialog.Builder(this)
                        .setTitle("Log recording error")
                        .setMessage("an error happened during the recording:\n\n" + error.getClass().getSimpleName() + ": " + error.getMessage())
                        .setPositiveButton("close", null)
                        .show();
                return;
            }

            EditText et = new EditText(this);
            new AlertDialog.Builder(this)
                    .setTitle("recording name")
                    .setView(et)
                    .setPositiveButton("start", (d, i) -> {
                        String name = et.getText() + ".log";
                        Log.d(TAG, "starting log recording to: " + name);
                        File logFile = getFilesDir().toPath().resolve(name).toFile();
                        try {
                            logcatReader.startRecording(logFile);
                            Toast.makeText(this, "recording started", Toast.LENGTH_SHORT).show();
                        } catch (IOException e) {
                            Log.e(TAG, "failed to start recording", e);
                        }
                    })
                    .setNegativeButton("cancel", null)
                    .setCancelable(false)
                    .show();

        });

        RecyclerView logRecyclerView = findViewById(R.id.log_recycler);
        LogUiAdapter logUiAdapter = new LogUiAdapter();

        logRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        logRecyclerView.setAdapter(logUiAdapter);
        logRecyclerView.setItemAnimator(null);  // does not work with fast-paced logs

        logUiAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                logRecyclerView.scrollToPosition(positionStart + itemCount - 1);
            }
        });

        logcatReader = new LogcatReader(logUiAdapter);
        logcatReader.start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        logcatReader.destroy();
    }

}
