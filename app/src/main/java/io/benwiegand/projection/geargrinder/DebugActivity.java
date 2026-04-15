package io.benwiegand.projection.geargrinder;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.function.Supplier;

import io.benwiegand.projection.geargrinder.logs.LogUiAdapter;
import io.benwiegand.projection.geargrinder.logs.LogcatReader;

public class DebugActivity extends AppCompatActivity {
    private static final String TAG = DebugActivity.class.getSimpleName();

    private LogcatReader logcatReader;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_debug);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

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

        if (getSupportActionBar() != null)
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);

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

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_debug, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        Map<Integer, Supplier<Boolean>> actionMap = Map.of(
                R.id.force_start_service_button, () -> {
                    startService(new Intent(this, ConnectionService.class)
                            .setAction(ConnectionService.INTENT_ACTION_CONNECT_USB));
                    return true;
                },
                R.id.debug_launch_projection_button, () -> {
                    startActivity(new Intent(this, ProjectionActivity.class));
                    return true;
                },
                R.id.launch_privd_button, () -> {
                    startService(new Intent(this, PrivdService.class));
                    return true;
                },
                R.id.start_audio_capture_button, () -> {
                    startActivity(new Intent(this, ConnectionRequestActivity.class)
                            .setAction(ConnectionRequestActivity.INTENT_ACTION_REQUEST_MEDIA_PROJECTION));
                    return true;
                }
        );
        Supplier<Boolean> action = actionMap.getOrDefault(item.getItemId(), () -> super.onOptionsItemSelected(item));
        assert action != null;
        return action.get();
    }
}
