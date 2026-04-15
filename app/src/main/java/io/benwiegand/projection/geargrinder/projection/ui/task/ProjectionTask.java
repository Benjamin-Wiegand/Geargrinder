package io.benwiegand.projection.geargrinder.projection.ui.task;

import android.content.ComponentName;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import io.benwiegand.projection.geargrinder.R;
import io.benwiegand.projection.geargrinder.pm.AppRecord;
import io.benwiegand.projection.geargrinder.projection.ui.VirtualActivity;

public class ProjectionTask {
    private static final LinearLayout.LayoutParams LAYOUT_PARAMS = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, 1);

    private final List<VirtualActivity> activities = new ArrayList<>();
    private final ViewGroup rootView;
    private final LinearLayout splitScreenLayout;
    private boolean attached = false;

    private final Consumer<ProjectionTask> updateListener;

    ProjectionTask(ViewGroup rootView, Consumer<ProjectionTask> updateListener, VirtualActivity... initialActivities) {
        if (initialActivities.length < 1) throw new IllegalArgumentException("workspace must contain at least 1 activity");
        this.rootView = rootView;
        this.updateListener = updateListener;
        splitScreenLayout = rootView.findViewById(R.id.split_screen_layout);

        activities.addAll(Arrays.asList(initialActivities));
    }

    private void onUpdated() {
        updateListener.accept(this);
    }

    private ViewGroup getRootView() {
        return rootView;
    }

    private void attachVirtualActivities() {
        assert splitScreenLayout.getChildCount() == 0;
        splitScreenLayout.removeAllViews();

        attached = true;
        for (VirtualActivity activity : activities)
            splitScreenLayout.addView(activity.getRootView(), LAYOUT_PARAMS);
    }

    private void detachVirtualActivities() {
        attached = false;
        splitScreenLayout.removeAllViews();
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

    public AppRecord[] getAppRecords() {
        AppRecord[] appRecords = new AppRecord[activities.size()];
        for (int i = 0; i < appRecords.length; i++)
            appRecords[i] = activities.get(i).getAppRecord();
        return appRecords;
    }

    public List<VirtualActivity> getVirtualActivities() {
        return List.copyOf(activities);
    }

    public void addActivity(VirtualActivity activity, int index) {
        if (activities.contains(activity)) throw new IllegalArgumentException("activity already in workspace");
        if (attached) splitScreenLayout.addView(activity.getRootView(), index, LAYOUT_PARAMS);
        activities.add(index, activity);
        onUpdated();
    }

    public void addActivity(VirtualActivity activity) {
        addActivity(activity, activities.size());
    }

    public void removeActivity(VirtualActivity activity) {
        if (!activities.contains(activity)) return;
        if (attached) splitScreenLayout.removeView(activity.getRootView());
        activities.remove(activity);
        onUpdated();
    }

}
