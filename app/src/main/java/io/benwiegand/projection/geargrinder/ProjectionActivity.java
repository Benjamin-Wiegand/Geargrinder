package io.benwiegand.projection.geargrinder;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.benwiegand.projection.geargrinder.makeshiftbind.MakeshiftBind;
import io.benwiegand.projection.geargrinder.makeshiftbind.MakeshiftBindCallback;
import io.benwiegand.projection.geargrinder.projection.VirtualActivity;
import io.benwiegand.projection.geargrinder.shell.RootShell;
import io.benwiegand.projection.geargrinder.util.UiUtil;

public class ProjectionActivity extends AppCompatActivity implements MakeshiftBindCallback {
    private static final String TAG = ProjectionActivity.class.getSimpleName();

    private final ActivityBinder binder = new ActivityBinder();
    private MakeshiftBind makeshiftBind;

    private final List<VirtualActivity> virtualActivities = new ArrayList<>();
    private RootShell rootShell = null;

    private PrivdService.ServiceBinder privdServiceBinder = null;   // use getPrivd()

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        setContentView(R.layout.activity_projection);

        findViewById(R.id.map_button).setOnClickListener(v ->
                launchActivity("net.osmand.plus/.activities.MapActivity"));

        findViewById(R.id.music_button).setOnClickListener(v ->
                launchActivity("org.videolan.vlc/.gui.MainActivity"));

        findViewById(R.id.app_picker_button).setOnClickListener(v ->
                UiUtil.createActivityPickerDialog(this, R.string.launch_app, this::launchActivity).show());

        findViewById(R.id.test_button).setOnClickListener(v -> {

            // temporarily show splash
//            for (VirtualActivity virtualActivity : virtualActivities)
//                virtualActivity.showSplash();

            // test swap
            if (virtualActivities.size() >= 2) {
                LinearLayout ll = findViewById(R.id.split_screen_layout);
                View view = ll.getChildAt(0);
                ll.removeView(view);
                ll.addView(view);
            }


        });

        try {
            rootShell = new RootShell();
        } catch (IOException e) {
            Log.e(TAG, "failed to launch root shell", e);
        }

        makeshiftBind = new MakeshiftBind(this, new ComponentName(this, ProjectionActivity.class), this);
        bindService(new Intent(this, PrivdService.class), serviceConnection, BIND_AUTO_CREATE | BIND_IMPORTANT);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");

        unbindService(serviceConnection);

        for (VirtualActivity virtualActivity : virtualActivities)
            virtualActivity.destroy();

        makeshiftBind.destroy();
        if (rootShell != null) rootShell.destroy();
    }


    private void launchActivity(ComponentName componentName) {
        ViewGroup splitScreenLayout = findViewById(R.id.split_screen_layout);
        try {
            if (rootShell == null) return;
            Log.i(TAG, "launching virtual activity: " + componentName);
            VirtualActivity virtualActivity = new VirtualActivity(rootShell, componentName, splitScreenLayout, this::getPrivd);
            View view = virtualActivity.getRootView();

            virtualActivities.add(virtualActivity);

            view.findViewById(R.id.virtual_activity_close_button).setOnClickListener(v -> {
                virtualActivities.remove(virtualActivity);
                splitScreenLayout.removeView(view);
                virtualActivity.destroy();
            });

            splitScreenLayout.addView(view, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        } catch (IOException | PackageManager.NameNotFoundException e) {
            Log.e(TAG, "failed to launch virtual activity", e);
        }
    }

    private void launchActivity(String component) {
        // TODO: remove this
        launchActivity(ComponentName.unflattenFromString(component));
    }

    @Override
    public IBinder onMakeshiftBind(Intent intent) {
        return binder;
    }

    private Optional<PrivdService.ServiceBinder> getPrivd() {
        return Optional.ofNullable(privdServiceBinder);
    }

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.i(TAG, "connected " + name.getShortClassName());
            privdServiceBinder = (PrivdService.ServiceBinder) service;
            try {
                privdServiceBinder.launchDaemon();
            } catch (IOException e) {
                // TODO
                Log.e(TAG, "failed to launch privd", e);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.i(TAG, "disconnected " + name.getShortClassName());
            privdServiceBinder = null;
        }
    };

    public class ActivityBinder extends Binder {

        public void setMargins(int horizontal, int vertical) {
            runOnUiThread(() -> {
                View root = findViewById(R.id.root);
                root.setPadding(
                        horizontal / 2,
                        vertical / 2,
                        horizontal - horizontal / 2,
                        vertical - vertical / 2
                );
            });
        }

    }
}
