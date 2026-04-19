package io.benwiegand.projection.geargrinder;

import android.app.KeyguardManager;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import io.benwiegand.projection.geargrinder.callback.AppLauncherListener;
import io.benwiegand.projection.geargrinder.callback.IPCConnectionListener;
import io.benwiegand.projection.geargrinder.makeshiftbind.MakeshiftBind;
import io.benwiegand.projection.geargrinder.callback.MakeshiftBindCallback;
import io.benwiegand.projection.geargrinder.pm.AppRecord;
import io.benwiegand.projection.geargrinder.projection.ui.BatteryIndicator;
import io.benwiegand.projection.geargrinder.projection.ui.NotificationDisplay;
import io.benwiegand.projection.geargrinder.projection.ui.task.ProjectionTask;
import io.benwiegand.projection.geargrinder.projection.ui.NetworkIndicators;
import io.benwiegand.projection.geargrinder.projection.ui.task.ProjectionTaskManager;
import io.benwiegand.projection.geargrinder.service.GeargrinderServiceConnector;
import io.benwiegand.projection.geargrinder.projection.ui.AppDock;
import io.benwiegand.projection.geargrinder.projection.ui.AppDrawer;
import io.benwiegand.projection.geargrinder.projection.ui.ProjectionModal;
import io.benwiegand.projection.libprivd.IPrivd;

public class ProjectionActivity extends AppCompatActivity implements MakeshiftBindCallback, IPCConnectionListener, GeargrinderServiceConnector.ConnectionListener, AppDock.Listener, AppLauncherListener, ProjectionTaskManager.Listener {
    private static final String TAG = ProjectionActivity.class.getSimpleName();

    // only happens while device is initially locked
    private static final long KEYGUARD_LOCK_STATE_POLL_INTERVAL = 1000;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private final ActivityBinder binder = new ActivityBinder();
    private MakeshiftBind makeshiftBind;

    private ProjectionTaskManager taskManager;
    private AppDock appDock;
    private AppDrawer appDrawer;
    private BatteryIndicator batteryIndicator;
    private NetworkIndicators networkIndicators;
    private NotificationDisplay notificationDisplay;

    private GeargrinderServiceConnector connector;
    private IPrivd privd = null;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        setContentView(R.layout.activity_projection);

        // screen lock
        // only do this on init so the device can be re-locked (like AA)
        KeyguardManager km = getSystemService(KeyguardManager.class);
        if (km.isKeyguardLocked()) {
            Log.i(TAG, "device is locked, restricting projection");

            ProjectionModal keyguardModal = new ProjectionModal(findViewById(R.id.root), true)
                    .setTitle(R.string.keyguard_modal_title)
                    .setMessage(R.string.keyguard_modal_instructions);

            // for extra security
            View projectionRoot = findViewById(R.id.projection_root);
            projectionRoot.setVisibility(View.GONE);

            // can't use callback without SUBSCRIBE_TO_KEYGUARD_LOCKED_STATE
            Runnable pollKeyguardLockState = new Runnable() {
                @Override
                public void run() {
                    if (!km.isKeyguardLocked()) {
                        Log.i(TAG, "device unlocked");
                        keyguardModal.close();
                        projectionRoot.setVisibility(View.VISIBLE);
                        return;
                    }

                    handler.postDelayed(this, KEYGUARD_LOCK_STATE_POLL_INTERVAL);
                }
            };

            pollKeyguardLockState.run();

        }

        // components
        taskManager = new ProjectionTaskManager(findViewById(R.id.content_frame));
        appDock = new AppDock(findViewById(R.id.app_dock), taskManager, this);
        appDrawer = new AppDrawer(findViewById(R.id.app_drawer), this);
        batteryIndicator = new BatteryIndicator(findViewById(R.id.battery_indicator));
        networkIndicators = new NetworkIndicators(findViewById(R.id.network_indicators));
        notificationDisplay = new NotificationDisplay(findViewById(R.id.popup_notification_overlay));

        taskManager.registerListener(this);

        // binds
        connector = new GeargrinderServiceConnector(TAG, this, this);
        connector.bindPrivdService(BIND_AUTO_CREATE | BIND_IMPORTANT);
        connector.bindPackageService(BIND_AUTO_CREATE | BIND_IMPORTANT);
        connector.bindNotificationService();

        makeshiftBind = new MakeshiftBind(this, new ComponentName(this, ProjectionActivity.class), this);

        getOnBackPressedDispatcher().addCallback(new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                Log.i(TAG, "consuming back");
                appDrawer.close();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");

        connector.getPackageBinder().ifPresent(b -> b.unregisterListener(this::onPackageListUpdated));

        taskManager.unregisterListener(this);
        taskManager.destroy();

        makeshiftBind.destroy();
        connector.destroy();

        appDock.destroy();
        appDrawer.destroy();
        batteryIndicator.destroy();
        networkIndicators.destroy();
        notificationDisplay.destroy();
    }

    private boolean focusSearch(int direction) {
        View focus = getCurrentFocus();
        if (focus == null) focus = findViewById(R.id.app_drawer_button);    // TODO: fails in touch focus mode

        View nextFocus = focus.focusSearch(direction);
        if (nextFocus == null) {
            Log.w(TAG, "couldn't find next focus");
            return false;
        }

        return nextFocus.requestFocus();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        boolean handled = switch (keyCode) {
            case KeyEvent.KEYCODE_NAVIGATE_NEXT -> focusSearch(View.FOCUS_FORWARD);
            case KeyEvent.KEYCODE_NAVIGATE_PREVIOUS -> focusSearch(View.FOCUS_BACKWARD);
            default -> false;
        };
        return handled || super.onKeyDown(keyCode, event);
    }

    @Override
    public void onAppSelected(AppRecord app) {
        ProjectionTask activeTask = taskManager.getActiveTask();
        if (activeTask != null && activeTask.contains(app)) {
            taskManager.dynamicOpenSingle(app);
            return;
        }

        taskManager.dynamicOpen(app);
    }

    @Override
    public void onContentFocus() {
        appDrawer.close();
    }

    @Override
    public void onAppDrawerSelected() {
        appDrawer.toggle();
    }

    @Override
    public void onPrivdServiceConnected(PrivdService.ServiceBinder binder) {
        binder.requestDaemon(ProjectionActivity.this);
    }

    private void onPackageListUpdated(PackageService.ServiceBinder binder) {
//        binder.getAppsFor(AppCategory.FOCUSED)
//                .forEach(appDock::addApp);
    }

    @Override
    public void onPackageServiceConnected(PackageService.ServiceBinder binder) {
        appDrawer.setPackageBinder(binder);
        notificationDisplay.setPackageServiceBinder(binder);
        binder.registerListener(this::onPackageListUpdated);
    }

    @Override
    public void onNotificationServiceConnected(NotificationService.ServiceBinder binder) {
        notificationDisplay.setNotificationServiceBinder(binder);
    }

    @Override
    public void onPrivdConnected(IPrivd privd) {
        this.privd = privd;
        taskManager.onPrivdConnected(privd);
    }

    @Override
    public void onPrivdDisconnected() {
        if (isFinishing() || isDestroyed()) return;
        Log.wtf(TAG, "privd connection lost, finishing");
        finish();
    }

    @Override
    public void onPrivdLaunchFailure(Throwable t) {
        // TODO: show error
        onPrivdDisconnected();
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

    }
}
