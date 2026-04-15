package io.benwiegand.projection.geargrinder.projection.ui;

import static io.benwiegand.projection.geargrinder.util.UiUtil.getViewBoundsInDisplay;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.view.DragEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.core.content.res.ResourcesCompat;

import java.util.HashMap;
import java.util.Map;

import io.benwiegand.projection.geargrinder.R;
import io.benwiegand.projection.geargrinder.pm.AppRecord;
import io.benwiegand.projection.geargrinder.projection.ui.task.ProjectionTask;
import io.benwiegand.projection.geargrinder.projection.ui.task.ProjectionTaskManager;

public class AppDock implements ProjectionTaskManager.Listener {
    private static final String TAG = AppDock.class.getSimpleName();

    private final Context context;
    private final PackageManager pm;
    private final View rootView;
    private final LinearLayout pinnedItemsView;
    private final LinearLayout openItemsView;
    private final LinearLayout[] itemsViews;

    private final View dropPlaceholder;

    private final ProjectionTaskManager taskManager;

    private final Listener listener;

    private final Map<ProjectionTask, View> taskItemViewMap = new HashMap<>();

    private record ItemLocation(ViewGroup itemsView, int index) { }

    public interface Listener {
        void onAppDrawerSelected();
    }


    public AppDock(ViewGroup rootView, ProjectionTaskManager taskManager, Listener listener) {
        this.rootView = rootView;
        this.taskManager = taskManager;
        this.listener = listener;
        context = rootView.getContext();
        pm = context.getPackageManager();
        LayoutInflater inflater = LayoutInflater.from(context);

        pinnedItemsView = rootView.findViewById(R.id.dock_pinned_items);
        openItemsView = rootView.findViewById(R.id.dock_open_items);
        itemsViews = new LinearLayout[] {pinnedItemsView, openItemsView};

        taskManager.registerListener(this);

        // drag to pin
        rootView.setOnDragListener(this::onDragEvent);
        dropPlaceholder = inflater.inflate(R.layout.layout_dock_item, rootView, false);
        inflater.inflate(R.layout.layout_dock_icon, dropPlaceholder.findViewById(R.id.icons_layout), true);


        rootView.findViewById(R.id.app_drawer_button)
                .setOnClickListener(v -> listener.onAppDrawerSelected());
    }

    public void destroy() {
        taskManager.unregisterListener(this);
    }

    public View getRootView() {
        return rootView;
    }

    private void updateDockItemView(ProjectionTask task, View itemView) {
        LayoutInflater inflater = LayoutInflater.from(context);
        LinearLayout iconsLayout = itemView.findViewById(R.id.icons_layout);

        iconsLayout.removeAllViews();

        AppRecord[] appRecords = task.getAppRecords();
        for (AppRecord app : appRecords) {
            View iconFrame = inflater.inflate(R.layout.layout_dock_icon, iconsLayout, false);

            ImageView iconView = iconFrame.findViewById(R.id.app_icon);
            iconView.setImageDrawable(app.icon(pm));

            iconsLayout.addView(iconFrame);
        }

        View touchTarget = itemView.findViewById(R.id.touch_target);
        touchTarget.setOnClickListener(v -> taskManager.switchToTask(task));
        touchTarget.setOnLongClickListener(v -> itemView.startDragAndDrop(null, new View.DragShadowBuilder(itemView), task, 0));
    }

    private View inflateDockItemView(ProjectionTask task) {
        LayoutInflater inflater = LayoutInflater.from(context);
        View itemView = inflater.inflate(R.layout.layout_dock_item, openItemsView, false);
        updateDockItemView(task, itemView);
        return itemView;
    }

    @Override
    public void onTaskMoved(int oldIndex, int newIndex, ProjectionTask task, boolean pinned) {
        LinearLayout itemsView = pinned ? pinnedItemsView : openItemsView;
        View itemView = taskItemViewMap.get(task);

        itemsView.removeView(itemView);
        itemsView.addView(itemView, newIndex);
    }

    @Override
    public void onTaskPinned(int oldIndex, int newIndex, ProjectionTask task) {
        View itemView = taskItemViewMap.get(task);
        openItemsView.removeView(itemView);
        pinnedItemsView.addView(itemView, newIndex);
    }

    @Override
    public void onTaskUnpinned(int oldIndex, int newIndex, ProjectionTask task) {
        View itemView = taskItemViewMap.get(task);
        pinnedItemsView.removeView(itemView);
        openItemsView.addView(itemView, newIndex);
    }

    @Override
    public void onTaskAdded(int index, ProjectionTask task, boolean pinned) {
        LinearLayout itemsView = pinned ? pinnedItemsView : openItemsView;
        View itemView = inflateDockItemView(task);
        taskItemViewMap.put(task, itemView);
        itemsView.addView(itemView, index);
    }

    @Override
    public void onTaskRemoved(int index, ProjectionTask task, boolean pinned) {
        LinearLayout itemsView = pinned ? pinnedItemsView : openItemsView;
        View itemView = taskItemViewMap.remove(task);
        itemsView.removeView(itemView);
    }

    @Override
    public void onTaskUpdated(ProjectionTask task, boolean pinned) {
        View itemView = taskItemViewMap.get(task);
        assert itemView != null;
        updateDockItemView(task, itemView);
    }

    @Override
    public void onSwitchTask(ProjectionTask oldTask, boolean oldPinned, ProjectionTask newTask, boolean newPinned) {
        if (oldTask != null) {
            View oldItemView = taskItemViewMap.get(oldTask);
            assert oldItemView != null;

            View oldIconsLayout = oldItemView.findViewById(R.id.icons_layout);
            oldIconsLayout.setBackground(ResourcesCompat.getDrawable(context.getResources(), R.drawable.dock_item_inactive_background, context.getTheme()));
        }

        View newItemView = taskItemViewMap.get(newTask);
        assert newItemView != null;
        View newIconsLayout = newItemView.findViewById(R.id.icons_layout);
        newIconsLayout.setBackground(ResourcesCompat.getDrawable(context.getResources(), R.drawable.dock_item_active_background, context.getTheme()));
    }

    private ItemLocation findDropLocationForCoordinates(int x, int y, boolean pinnedOnly) {
        Rect bounds = new Rect();
        getViewBoundsInDisplay(rootView, bounds);
        int xOffset = bounds.left;

        getViewBoundsInDisplay(pinnedItemsView, bounds);
        if (x + xOffset < bounds.left) return new ItemLocation(pinnedItemsView, 0);

        for (int i = 0; i < pinnedItemsView.getChildCount(); i++) {
            View item = pinnedItemsView.getChildAt(i);
            getViewBoundsInDisplay(item, bounds);
            if (item == dropPlaceholder) xOffset += dropPlaceholder.getWidth();
            if (x + xOffset > bounds.right) continue;
            return new ItemLocation(pinnedItemsView, i);
        }

        getViewBoundsInDisplay(openItemsView, bounds);
        if (pinnedOnly || x + xOffset < bounds.left)
            return new ItemLocation(pinnedItemsView, pinnedItemsView.getChildCount());

        return new ItemLocation(openItemsView, 0);
    }

    private ItemLocation findDropPlaceholder() {
        for (ViewGroup itemsView : itemsViews) {
            int index = itemsView.indexOfChild(dropPlaceholder);
            if (index < 0) continue;
            return new ItemLocation(itemsView, index);
        }
        return null;
    }

    private AppRecord getAppRecordFromDragState(DragEvent event) {
        return switch (event.getLocalState()) {
            case AppRecord app -> app;
            case null, default -> null;
        };
    }

    private ProjectionTask getProjectionTaskFromDragState(DragEvent event) {
        return switch (event.getLocalState()) {
            case ProjectionTask workspace -> workspace;
            case null, default -> null;
        };
    }

    private View getDockItemViewFromDragState(DragEvent event) {
        ProjectionTask task = getProjectionTaskFromDragState(event);
        if (task == null) return null;

        return taskItemViewMap.get(task);
    }

    private boolean onlyAllowPin(DragEvent event) {
        ProjectionTask task = getProjectionTaskFromDragState(event);
        return task == null || !taskManager.isPinned(task);
    }

    private boolean onDragEvent(View view, DragEvent event) {
        return switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED -> getProjectionTaskFromDragState(event) != null
                    || getAppRecordFromDragState(event) != null;

            case DragEvent.ACTION_DRAG_LOCATION -> {
                ItemLocation dropLocation = findDropLocationForCoordinates((int) event.getX(), (int) event.getY(), onlyAllowPin(event));
                ItemLocation placeholderLocation = findDropPlaceholder();
                int index = dropLocation.index();

                if (dropLocation.equals(placeholderLocation)) yield true;

                if (placeholderLocation != null) {
                    placeholderLocation.itemsView().removeView(dropPlaceholder);
                    if (placeholderLocation.index() < dropLocation.index()) index--;    // index shift from moving placeholder
                }

                dropLocation.itemsView().addView(dropPlaceholder, index);

                yield true;
            }
            case DragEvent.ACTION_DRAG_ENTERED -> {
                View itemView = getDockItemViewFromDragState(event);
                if (itemView != null) itemView.setVisibility(View.INVISIBLE);
                yield true;
            }
            case DragEvent.ACTION_DRAG_EXITED -> {
                ItemLocation placeholderLocation = findDropPlaceholder();
                if (placeholderLocation != null)
                    placeholderLocation.itemsView().removeView(dropPlaceholder);

                yield true;
            }
            case DragEvent.ACTION_DRAG_ENDED -> {
                View itemView = getDockItemViewFromDragState(event);
                if (itemView != null) itemView.setVisibility(View.VISIBLE);
                yield true;
            }
            case DragEvent.ACTION_DROP -> {

                ItemLocation placeholderLocation = findDropPlaceholder();
                if (placeholderLocation != null)
                    placeholderLocation.itemsView().removeView(dropPlaceholder);

                ItemLocation dropLocation = findDropLocationForCoordinates((int) event.getX(), (int) event.getY(), onlyAllowPin(event));

                ProjectionTask task = getProjectionTaskFromDragState(event);
                if (task != null) {
                    boolean pinned = taskManager.isPinned(task);
                    boolean pin = dropLocation.itemsView() == pinnedItemsView;
                    if (!pinned && pin) {
                        taskManager.pinTask(dropLocation.index(), task);
                    } else if (pinned && !pin) {
                        taskManager.unpinTask(task);
                    } else {
                        assert pinned && pin;
                        taskManager.movePinnedTask(dropLocation.index(), task);
                    }
                    yield true;
                }

                AppRecord app = getAppRecordFromDragState(event);
                if (app != null) {
                    taskManager.createNewPinnedTask(dropLocation.index(), app);
                    yield true;
                }

                assert false;
                yield false;
            }
            default -> true;
        };
    }

}
