package io.benwiegand.projection.geargrinder.projection.ui.task;

import static io.benwiegand.projection.geargrinder.util.UiUtil.errorDialog;

import android.content.ComponentName;
import android.content.Context;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.appcompat.app.AlertDialog;

import java.util.ArrayList;
import java.util.List;

import io.benwiegand.projection.geargrinder.R;
import io.benwiegand.projection.geargrinder.exception.UserFriendlyException;
import io.benwiegand.projection.geargrinder.pm.AppRecord;
import io.benwiegand.projection.geargrinder.projection.ui.VirtualActivity;
import io.benwiegand.projection.geargrinder.projection.ui.preset.ButtonPreset;
import io.benwiegand.projection.geargrinder.util.UiUtil;

public class ProjectionTask {
    public static final LinearLayout.LayoutParams SPLIT_SCREEN_LAYOUT_PARAMS = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT, 1);

    // TODO: make this configurable
    private static final int MAX_SPLIT_SCREEN = 3;

    private final List<VirtualActivity> activities = new ArrayList<>();
    private final ViewGroup rootView;
    private final LinearLayout splitScreenLayout;
    private boolean attached = false;

    private final ProjectionTaskSplash splash;

    private final ProjectionTaskManager taskManager;

    ProjectionTask(ViewGroup rootView, ProjectionTaskManager taskManager, VirtualActivity... initialActivities) {
        if (initialActivities.length < 1) throw new IllegalArgumentException("workspace must contain at least 1 activity");
        this.rootView = rootView;
        this.taskManager = taskManager;
        splitScreenLayout = rootView.findViewById(R.id.split_screen_layout);

        splash = new ProjectionTaskSplash(rootView.findViewById(R.id.projection_task_splash));

        splash.setVirtualActivityContextButtonGenerator(activity -> new ButtonPreset[] {
                new ButtonPreset(R.string.close_button, android.R.drawable.ic_menu_close_clear_cancel, v -> {
                    removeActivity(activity);
                    splash.hide();
                }),
                new ButtonPreset(R.string.relaunch_button, android.R.drawable.ic_popup_sync, v -> relaunchActivity(activity)),
        });

        for (VirtualActivity activity : initialActivities) {
            activities.add(activity);
            splash.addVirtualActivity(activity);
        }

        updateContextButtons();
    }

    public Context getContext() {
        return rootView.getContext();
    }

    private ViewGroup getRootView() {
        return rootView;
    }

    public void toggleSplash() {
        splash.toggle();
    }

    public AlertDialog createAddSplitScreenDialog() {
        List<AppRecord> appRecords = new ArrayList<>();
        taskManager.getOrderedVirtualActivities().stream()
                .filter(va -> !activities.contains(va))
                .map(VirtualActivity::getAppRecord)
                .forEach(appRecords::add);

        AlertDialog dialog = UiUtil.createAppRecordPickerDialog(getContext(), R.string.add_split_screen_title, appRecords, app -> {
            addActivity(taskManager.getOrCreateVirtualActivity(app));
            taskManager.removeSingle(app);
        });
        dialog.setOnDismissListener(d -> splash.hide());
        return dialog;
    }

    private void updateContextButtons() {
        ButtonPreset addSplitScreenButton = new ButtonPreset(R.string.launch_split_screen, android.R.drawable.ic_input_add, v -> createAddSplitScreenDialog().show());
        if (activityCount() >= MAX_SPLIT_SCREEN) {
            splash.inflateButtons();
        } else {
            splash.inflateButtons(addSplitScreenButton);
        }
    }

    private void onUpdated() {
        taskManager.onTaskUpdated(this);
        updateContextButtons();
    }

    private void attachVirtualActivities() {
        assert splitScreenLayout.getChildCount() == 0;
        splitScreenLayout.removeAllViews();

        attached = true;
        for (VirtualActivity activity : activities)
            splitScreenLayout.addView(activity.getRootView(), SPLIT_SCREEN_LAYOUT_PARAMS);
    }

    private void detachVirtualActivities() {
        attached = false;
        splitScreenLayout.removeAllViews();
        splash.hide(false);
    }

    public void attach(ViewGroup contentFrame) {
        contentFrame.removeAllViews();
        attachVirtualActivities();
        contentFrame.addView(getRootView());
    }

    public void detach(ViewGroup contentFrame) {
        contentFrame.removeAllViews();
        detachVirtualActivities();
    }

    public int activityCount() {
        return activities.size();
    }

    public boolean contains(ComponentName componentName) {
        for (VirtualActivity activity : activities) {
            if (!activity.getComponentName().equals(componentName)) continue;
            return true;
        }
        return false;
    }

    public boolean contains(AppRecord app) {
        return contains(app.launchComponent());
    }

    boolean contains(VirtualActivity activity) {
        return activities.contains(activity);
    }

    public AppRecord[] getAppRecords() {
        AppRecord[] appRecords = new AppRecord[activities.size()];
        for (int i = 0; i < appRecords.length; i++)
            appRecords[i] = activities.get(i).getAppRecord();
        return appRecords;
    }

    public List<VirtualActivity> getVirtualActivities() {
        return List.copyOf(activities);
    }

    void addActivity(VirtualActivity activity, int index) {
        if (activities.contains(activity)) throw new IllegalArgumentException("activity already in workspace");
        if (attached) splitScreenLayout.addView(activity.getRootView(), index, SPLIT_SCREEN_LAYOUT_PARAMS);
        activities.add(index, activity);
        splash.addVirtualActivity(activity, index);
        onUpdated();
    }

    void addActivity(VirtualActivity activity) {
        addActivity(activity, activities.size());
    }

    void removeActivity(VirtualActivity activity) {
        if (!activities.contains(activity)) return;
        if (attached) splitScreenLayout.removeView(activity.getRootView());
        activities.remove(activity);
        splash.removeVirtualActivity(activity);
        onUpdated();
        taskManager.destroyVirtualActivityIfUnused(activity);
    }

    private void relaunchActivity(VirtualActivity activity) {
        try {
            activity.launch(true);
            splash.hide();
        } catch (UserFriendlyException e) {
            errorDialog(getContext(), e)
                    .setNeutralButton(R.string.cancel_button, null)
                    .setPositiveButton(R.string.relaunch_button, (d, i) -> relaunchActivity(activity))
                    .show();
        }
    }

}
