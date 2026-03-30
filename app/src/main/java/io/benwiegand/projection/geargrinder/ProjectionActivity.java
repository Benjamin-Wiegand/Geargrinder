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
import android.view.DragEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.util.HashMap;
import java.util.Map;

import io.benwiegand.projection.geargrinder.callback.IPCConnectionListener;
import io.benwiegand.projection.geargrinder.makeshiftbind.MakeshiftBind;
import io.benwiegand.projection.geargrinder.callback.MakeshiftBindCallback;
import io.benwiegand.projection.geargrinder.pm.AppCategory;
import io.benwiegand.projection.geargrinder.pm.AppRecord;
import io.benwiegand.projection.geargrinder.projection.ui.BatteryIndicator;
import io.benwiegand.projection.geargrinder.projection.ui.NotificationDisplay;
import io.benwiegand.projection.geargrinder.projection.ui.VirtualActivity;
import io.benwiegand.projection.geargrinder.projection.ui.NetworkIndicators;
import io.benwiegand.projection.geargrinder.service.GeargrinderServiceConnector;
import io.benwiegand.projection.geargrinder.projection.ui.AppDock;
import io.benwiegand.projection.geargrinder.projection.ui.AppDrawer;
import io.benwiegand.projection.geargrinder.projection.ui.ProjectionModal;
import io.benwiegand.projection.libprivd.IPrivd;

public class ProjectionActivity extends AppCompatActivity implements MakeshiftBindCallback, VirtualActivity.VirtualActivityListener, IPCConnectionListener, AppDock.AppDockListener, GeargrinderServiceConnector.ConnectionListener {
    private static final String TAG = ProjectionActivity.class.getSimpleName();

    // only happens while device is initially locked
    private static final long KEYGUARD_LOCK_STATE_POLL_INTERVAL = 1000;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private final ActivityBinder binder = new ActivityBinder();
    private MakeshiftBind makeshiftBind;

    private final Map<ComponentName, VirtualActivity> virtualActivities = new HashMap<>();

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


        findViewById(R.id.edit_layout_button).setOnClickListener(v -> {
            // temporarily show splash
            for (VirtualActivity virtualActivity : virtualActivities.values())
                virtualActivity.showSplash();
        });

        findViewById(R.id.edit_layout_button).setOnLongClickListener(v -> {
            if (virtualActivities.size() < 2) return false;

            // test swap
            LinearLayout ll = findViewById(R.id.split_screen_layout);
            View view = ll.getChildAt(0);
            ll.removeView(view);
            ll.addView(view);
            return true;
        });

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
        appDock = new AppDock(findViewById(R.id.app_dock), this);
        appDrawer = new AppDrawer(findViewById(R.id.app_drawer), this);
        batteryIndicator = new BatteryIndicator(findViewById(R.id.battery_indicator));
        networkIndicators = new NetworkIndicators(findViewById(R.id.network_indicators));
        notificationDisplay = new NotificationDisplay(findViewById(R.id.popup_notification_overlay));

        // layout updates
        findViewById(R.id.root).getViewTreeObserver().addOnGlobalLayoutListener(this::onGlobalLayout);

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
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");

        connector.getPackageBinder().ifPresent(b -> b.unregisterListener(this::onPackageListUpdated));

        for (VirtualActivity virtualActivity : virtualActivities.values())
            virtualActivity.destroy();
        virtualActivities.clear();

        makeshiftBind.destroy();
        connector.destroy();

        appDrawer.destroy();
        batteryIndicator.destroy();
        networkIndicators.destroy();
        notificationDisplay.destroy();
    }

    public void onGlobalLayout() {
        LinearLayout splitScreenLayout = findViewById(R.id.split_screen_layout);
        splitScreenLayout.setOrientation(splitScreenLayout.getWidth() < splitScreenLayout.getHeight() ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
    }

    private void closeActivity(VirtualActivity virtualActivity) {
        ViewGroup splitScreenLayout = findViewById(R.id.split_screen_layout);
        virtualActivities.remove(virtualActivity.getComponentName());
        splitScreenLayout.removeView(virtualActivity.getRootView());
        virtualActivity.destroy();
    }

    private void swapActivities(VirtualActivity a, VirtualActivity b) {
        ViewGroup splitScreenLayout = findViewById(R.id.split_screen_layout);
        int aIndex = splitScreenLayout.indexOfChild(a.getRootView());
        int bIndex = splitScreenLayout.indexOfChild(b.getRootView());

        splitScreenLayout.removeView(a.getRootView());
        splitScreenLayout.removeView(b.getRootView());

        if (bIndex > aIndex) {
            splitScreenLayout.addView(b.getRootView(), aIndex);
            splitScreenLayout.addView(a.getRootView(), bIndex);
        } else {
            splitScreenLayout.addView(a.getRootView(), bIndex);
            splitScreenLayout.addView(b.getRootView(), aIndex);
        }
    }


    private void launchActivity(AppRecord app) {
        VirtualActivity oldActivity = virtualActivities.get(app.launchComponent());
        if (oldActivity != null) {
            Log.d(TAG, "closing existing instance for relaunch");
            closeActivity(oldActivity);
        }

        ViewGroup splitScreenLayout = findViewById(R.id.split_screen_layout);
        try {
            Log.i(TAG, "launching virtual activity: " + app);

            VirtualActivity virtualActivity = new VirtualActivity(privd, app, splitScreenLayout, this);
            virtualActivities.put(app.launchComponent(), virtualActivity);

            View rootView = virtualActivity.getRootView();
            View splashView = rootView.findViewById(R.id.virtual_activity_splash);

            splashView.setOnLongClickListener(v -> rootView.startDragAndDrop(null, new View.DragShadowBuilder(rootView), virtualActivity, 0));

            rootView.setOnDragListener((v, e) -> {
                if (e.getLocalState() instanceof VirtualActivity dragTarget)
                    return onVirtualActivityDragEvent(dragTarget, virtualActivity, e);
                return false;
            });

            splitScreenLayout.addView(rootView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        } catch (Throwable t) {
            Log.e(TAG, "failed to launch virtual activity", t);
            Toast.makeText(this, R.string.failed_to_launch_app, Toast.LENGTH_SHORT).show();
        }
    }

    public boolean onVirtualActivityDragEvent(VirtualActivity dragTarget, VirtualActivity dropTarget, DragEvent event) {
        return switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_ENTERED -> {
                ImageView iconView = dropTarget.getRootView().findViewById(R.id.virtual_activity_icon);
                iconView.setVisibility(View.INVISIBLE);
                yield true;
            }
            case DragEvent.ACTION_DRAG_EXITED -> {
                ImageView iconView = dropTarget.getRootView().findViewById(R.id.virtual_activity_icon);
                iconView.setVisibility(View.VISIBLE);
                yield true;
            }
            case DragEvent.ACTION_DROP -> {
                if (dragTarget == dropTarget) yield false;
                swapActivities(dragTarget, dropTarget);
                yield true;
            }
            default -> true;
        };
    }

    @Override
    public void onVirtualActivityCloseButton(VirtualActivity virtualActivity) {
        closeActivity(virtualActivity);
    }

    @Override
    public void onAppSelected(AppRecord app) {
        appDrawer.close();
        launchActivity(app);
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
        // TODO: temporary
        binder.getAppsFor(AppCategory.FOCUSED)
                .forEach(appDock::addApp);
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
