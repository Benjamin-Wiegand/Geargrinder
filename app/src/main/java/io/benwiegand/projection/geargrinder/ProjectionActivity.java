package io.benwiegand.projection.geargrinder;

import static io.benwiegand.projection.geargrinder.util.UiUtil.getDisplayId;

import android.content.ComponentName;
import android.content.Intent;
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

import io.benwiegand.projection.geargrinder.callback.InputEventListener;
import io.benwiegand.projection.geargrinder.makeshiftbind.MakeshiftBind;
import io.benwiegand.projection.geargrinder.makeshiftbind.MakeshiftBindCallback;
import io.benwiegand.projection.geargrinder.projection.InputEventMuxer;
import io.benwiegand.projection.geargrinder.projection.VirtualActivity;
import io.benwiegand.projection.geargrinder.shell.RootShell;
import io.benwiegand.projection.geargrinder.util.RootUtil;
import io.benwiegand.projection.geargrinder.util.UiUtil;

public class ProjectionActivity extends AppCompatActivity implements MakeshiftBindCallback {
    private static final String TAG = ProjectionActivity.class.getSimpleName();

    private final ActivityBinder binder = new ActivityBinder();
    private MakeshiftBind makeshiftBind;

    private final List<VirtualActivity> virtualActivities = new ArrayList<>();
    private InputEventMuxer inputEventMuxer;
    private RootShell rootShell = null;

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

        inputEventMuxer = new InputEventMuxer((event, translator) -> {
            try {
                if (rootShell == null) return;
                RootUtil.simulateTouchEventRoot(rootShell, getDisplayId(this), event, translator);
            } catch (IOException e) {
                Log.e(TAG, "failed to simulate touch", e);
            }
        });

        makeshiftBind = new MakeshiftBind(this, new ComponentName(this, ProjectionActivity.class), this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");

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
            VirtualActivity virtualActivity = new VirtualActivity(rootShell, inputEventMuxer, componentName, splitScreenLayout);
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

        public InputEventListener getInputEventListener() {
            return inputEventMuxer;
        }

    }
}
