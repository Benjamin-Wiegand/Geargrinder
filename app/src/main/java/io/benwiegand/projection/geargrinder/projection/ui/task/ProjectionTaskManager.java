package io.benwiegand.projection.geargrinder.projection.ui.task;

import android.content.ComponentName;
import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

import io.benwiegand.projection.geargrinder.R;
import io.benwiegand.projection.geargrinder.pm.AppRecord;
import io.benwiegand.projection.geargrinder.projection.ui.VirtualActivity;
import io.benwiegand.projection.libprivd.IPrivd;

public class ProjectionTaskManager {
    private static final String TAG = ProjectionTaskManager.class.getSimpleName();

    public interface Listener {
        default void onTaskMoved(int oldIndex, int newIndex, ProjectionTask task, boolean pinned) {};
        default void onTaskPinned(int oldIndex, int newIndex, ProjectionTask task) {};
        default void onTaskUnpinned(int oldIndex, int newIndex, ProjectionTask task) {};
        default void onTaskAdded(int index, ProjectionTask task, boolean pinned) {};
        default void onTaskRemoved(int index, ProjectionTask task, boolean pinned) {};
        default void onTaskUpdated(ProjectionTask task, boolean pinned) {};
        default void onSwitchTask(ProjectionTask oldTask, boolean oldPinned, ProjectionTask newTask, boolean newPinned) {};
        default void onContentFocus() {};
    }

    private final ViewGroup contentFrame;
    private final Context context;
    private IPrivd privd = null;

    private final Map<ComponentName, VirtualActivity> virtualActivities = new HashMap<>();
    private final List<ProjectionTask> pinnedTasks = new ArrayList<>();
    private final List<ProjectionTask> openTasks = new LinkedList<>();
    private final List<List<ProjectionTask>> taskLists = List.of(pinnedTasks, openTasks);
    private ProjectionTask activeTask = null;

    private int splitScreenOrientation = LinearLayout.HORIZONTAL;

    private final Queue<Listener> listeners = new LinkedList<>();

    public ProjectionTaskManager(ViewGroup contentFrame) {
        this.contentFrame = contentFrame;
        this.context = contentFrame.getContext();

        // layout updates
        contentFrame.getViewTreeObserver().addOnGlobalLayoutListener(this::onGlobalLayout);
    }

    public void destroy() {
        for (VirtualActivity virtualActivity : virtualActivities.values())
            virtualActivity.destroy();
        virtualActivities.clear();
        pinnedTasks.clear();
        openTasks.clear();
        activeTask = null;
        listeners.clear();
    }

    private void onGlobalLayout() {
        splitScreenOrientation = contentFrame.getWidth() < contentFrame.getHeight() ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL;
    }

    void onTaskUpdated(ProjectionTask task) {
        if (task.activityCount() == 0) {
            Log.i(TAG, "removing now empty task");
            removeTask(task);
            return;
        }
        boolean pinned = isPinned(task);
        callListeners(l -> l.onTaskUpdated(task, pinned));
    }

    public void onPrivdConnected(IPrivd privd) {
        this.privd = privd;
    }

    private void callListeners(Consumer<Listener> consumer) {
        for (Listener listener : listeners) {
            try {
                consumer.accept(listener);
            } catch (Throwable t) {
                Log.wtf(TAG, "exception thrown in listener", t);
                throw t;
            }
        }
    }

    public void registerListener(Listener listener) {
        listeners.add(listener);
        for (int i = 0; i < pinnedTasks.size(); i++)
            listener.onTaskAdded(i, pinnedTasks.get(i), true);
        for (int i = 0; i < openTasks.size(); i++)
            listener.onTaskAdded(i, openTasks.get(i), false);
    }

    public void unregisterListener(Listener listener) {
        listeners.remove(listener);
    }

    public boolean isPinned(ProjectionTask task) {
        return pinnedTasks.contains(task);
    }

    public void removeTask(ProjectionTask task) {
        boolean pinned = isPinned(task);
        List<ProjectionTask> taskList = pinned ? pinnedTasks : openTasks;
        int oldIndex = taskList.indexOf(task);
        if (oldIndex < 0)
            throw new AssertionError("can't find provided task");

        if (activeTask == task) switchToTask(null);
        taskList.remove(task);

        callListeners(l -> l.onTaskRemoved(oldIndex, task, pinned));
    }

    public void movePinnedTask(int index, ProjectionTask task) {
        int oldIndex = pinnedTasks.indexOf(task);
        if (oldIndex < index) index--;  // will shift on removal

        pinnedTasks.remove(task);
        pinnedTasks.add(index, task);

        int newIndex = index;
        callListeners(l -> l.onTaskMoved(oldIndex, newIndex, task, true));
    }

    public void pinTask(int index, ProjectionTask task) {
        int oldIndex = openTasks.indexOf(task);
        if (oldIndex < 0)
            throw new AssertionError("can't find provided task");

        openTasks.remove(task);
        pinnedTasks.add(index, task);

        callListeners(l -> l.onTaskPinned(oldIndex, index, task));
    }

    public void pinTask(ProjectionTask task) {
        pinTask(pinnedTasks.size(), task);
    }

    public void unpinTask(ProjectionTask task) {
        int oldIndex = pinnedTasks.indexOf(task);
        if (oldIndex < 0)
            throw new AssertionError("can't find provided task");

        pinnedTasks.remove(task);
        openTasks.add(0, task);

        callListeners(l -> l.onTaskUnpinned(oldIndex, 0, task));
    }

    private void moveToFront(ProjectionTask task) {
        int oldIndex = openTasks.indexOf(task);
        if (oldIndex == 0) return;
        if (oldIndex < 0)
            throw new AssertionError("can't find provided task");

        openTasks.remove(task);
        openTasks.add(0, task);

        callListeners(l -> l.onTaskMoved(oldIndex, 0, task, false));
    }

    VirtualActivity getOrCreateVirtualActivity(AppRecord app) {
        VirtualActivity oldActivity = virtualActivities.get(app.launchComponent());
        if (oldActivity != null) return oldActivity;

        try {
            Log.i(TAG, "launching virtual activity: " + app);

            VirtualActivity virtualActivity = new VirtualActivity(privd, app, contentFrame);
            virtualActivities.put(app.launchComponent(), virtualActivity);
            return virtualActivity;

        } catch (Throwable t) {
            Log.e(TAG, "failed to construct virtual activity", t);
            Toast.makeText(context, R.string.failed_to_launch_app, Toast.LENGTH_SHORT).show();
            return null;
        }
    }

    void destroyVirtualActivityIfUnused(VirtualActivity activity) {
        if (getAllTasksAsStream().anyMatch(task -> task.contains(activity))) return;
        activity.destroy();
        virtualActivities.remove(activity.getComponentName());
    }

    private ProjectionTask createTask(AppRecord... apps) {
        ViewGroup taskView = (ViewGroup) LayoutInflater.from(context).inflate(R.layout.layout_projection_task, contentFrame, false);
        LinearLayout splitScreenLayout = taskView.findViewById(R.id.split_screen_layout);
        splitScreenLayout.setOrientation(splitScreenOrientation);

        VirtualActivity[] activities = new VirtualActivity[apps.length];
        for (int i = 0; i < apps.length; i++)
            activities[i] = getOrCreateVirtualActivity(apps[i]);

        return new ProjectionTask(taskView, this, activities);
    }

    public ProjectionTask createNewPinnedTask(int index, AppRecord app) {
        ProjectionTask task = createTask(app);
        pinnedTasks.add(index, task);
        callListeners(l -> l.onTaskAdded(index, task, true));
        return task;
    }

    public ProjectionTask createNewTask(AppRecord app) {
        ProjectionTask task = createTask(app);
        openTasks.add(0, task);
        callListeners(l -> l.onTaskAdded(0, task, false));
        return task;
    }

    public void requestContentFocus() {
        callListeners(Listener::onContentFocus);
    }

    public void switchToTask(ProjectionTask newTask) {
        ProjectionTask oldTask = activeTask;
        boolean oldPinned = oldTask != null && isPinned(oldTask);
        boolean newPinned = isPinned(newTask);

        if (oldTask != null) oldTask.detach(contentFrame);
        activeTask = newTask;
        if (newTask != null) newTask.attach(contentFrame);
        callListeners(l -> l.onSwitchTask(oldTask, oldPinned, newTask, newPinned));
        if (newTask != null && !newPinned) moveToFront(newTask);

        requestContentFocus();
    }

    /**
     * finds the first task containing the app represented by the app record
     * @param app app
     * @return first task containing the app, or null if none have it
     */
    public ProjectionTask findTaskContaining(AppRecord app) {
        if (!virtualActivities.containsKey(app.launchComponent())) return null;

        for (ProjectionTask task : pinnedTasks) {
            if (!task.contains(app.launchComponent())) continue;
            return task;
        }

        for (ProjectionTask task : openTasks) {
            if (!task.contains(app.launchComponent())) continue;
            return task;
        }

        return null;
    }

    /**
     * similar to {@link ProjectionTaskManager#findTaskContaining(AppRecord)} but only considers tasks with exactly 1 app
     */
    public ProjectionTask findTaskForApp(AppRecord app) {
        if (!virtualActivities.containsKey(app.launchComponent())) return null;

        for (ProjectionTask task : pinnedTasks) {
            if (task.activityCount() != 1) continue;
            if (!task.contains(app.launchComponent())) continue;
            return task;
        }

        for (ProjectionTask task : openTasks) {
            if (task.activityCount() != 1) continue;
            if (!task.contains(app.launchComponent())) continue;
            return task;
        }

        return null;
    }

    public void dynamicOpen(AppRecord app) {
        ProjectionTask task = findTaskContaining(app);
        if (task == null) task = createNewTask(app);
        switchToTask(task);
    }

    public void dynamicOpenSingle(AppRecord app) {
        ProjectionTask task = findTaskForApp(app);
        if (task == null) task = createNewTask(app);
        switchToTask(task);
    }

    public ProjectionTask getActiveTask() {
        return activeTask;
    }

    public Stream<ProjectionTask> getAllTasksAsStream() {
        return taskLists.stream()
                .flatMap(Collection::stream);
    }

    public List<VirtualActivity> getOrderedVirtualActivities() {
        Set<ComponentName> components = new HashSet<>(virtualActivities.size() * 2);
        List<VirtualActivity> activities = new ArrayList<>(virtualActivities.size());

        getAllTasksAsStream()
                .flatMap(task -> task.getVirtualActivities().stream())
                .filter(va -> components.add(va.getComponentName()))
                .forEachOrdered(activities::add);

        assert activities.size() == virtualActivities.size();
        return activities;
    }

    /**
     * removes a task containing a single app, if found
     * @param app the app to check for
     * @return true if a task was removed, false otherwise
     */
    public boolean removeSingle(AppRecord app) {
        ProjectionTask task = getAllTasksAsStream()
                .filter(t -> t.activityCount() == 1)
                .filter(t -> t.contains(app))
                .findFirst()
                .orElse(null);
        if (task == null) return false;
        removeTask(task);
        return true;
    }


}
