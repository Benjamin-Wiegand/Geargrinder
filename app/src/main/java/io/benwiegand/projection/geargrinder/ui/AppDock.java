package io.benwiegand.projection.geargrinder.ui;

import static io.benwiegand.projection.geargrinder.util.UiUtil.getViewBoundsInDisplay;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.view.DragEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

import java.util.HashMap;

import io.benwiegand.projection.geargrinder.R;
import io.benwiegand.projection.geargrinder.callback.AppLauncherListener;
import io.benwiegand.projection.geargrinder.pm.AppRecord;
import io.benwiegand.projection.geargrinder.projection.VirtualActivity;

public class AppDock {
    private static final String TAG = AppDock.class.getSimpleName();

    private final Context context;
    private final PackageManager pm;
    private final View rootView;
    private final LinearLayout itemsView;

    private final View dropPlaceholder;

    private final AppDockListener listener;

    private final HashMap<ComponentName, View> dockItems = new HashMap<>();

    public interface AppDockListener extends AppLauncherListener {
        void onAppDrawerSelected();
    }


    public AppDock(View rootView, AppDockListener listener) {
        this.rootView = rootView;
        this.listener = listener;
        context = rootView.getContext();
        pm = context.getPackageManager();

        itemsView = rootView.findViewById(R.id.dock_items);

        // drag to pin
        rootView.setOnDragListener(this::onDragEvent);
        dropPlaceholder = LayoutInflater.from(context).inflate(R.layout.layout_dock_item, itemsView, false);


        rootView.findViewById(R.id.app_drawer_button)
                .setOnClickListener(v -> listener.onAppDrawerSelected());
    }

    public View getRootView() {
        return rootView;
    }

    public boolean addApp(AppRecord app, int index) {
        if (dockItems.containsKey(app.launchComponent())) return false;

        LayoutInflater inflater = LayoutInflater.from(context);
        View dockItemView = inflater.inflate(R.layout.layout_dock_item, itemsView, false);

        ImageView iconView = dockItemView.findViewById(R.id.app_icon);
        iconView.setImageDrawable(app.icon(pm));

        dockItemView.findViewById(R.id.touch_target)
                .setOnClickListener(v -> listener.onAppSelected(app));

        itemsView.addView(dockItemView, index);
        dockItems.put(app.launchComponent(), dockItemView);

        return true;
    }

    public boolean addApp(AppRecord app) {
        return addApp(app, itemsView.getChildCount());
    }

    private int getDockIndexOfCoordinates(int x, int y) {
        Rect bounds = new Rect();
        getViewBoundsInDisplay(rootView, bounds);
        int xOffset = bounds.left;

        for (int i = 0; i < itemsView.getChildCount(); i++) {
            View icon = itemsView.getChildAt(i);
            getViewBoundsInDisplay(icon, bounds);
            if (x + xOffset > bounds.left) continue;
            return i;
        }
        return itemsView.getChildCount();
    }

    private AppRecord getAppRecordFromDragState(DragEvent event) {
        return switch (event.getLocalState()) {
            case AppRecord app -> app;
            case VirtualActivity va -> va.getAppRecord();
            case null, default -> null;
        };
    }

    private boolean onDragEvent(View view, DragEvent event) {
        return switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED -> {
                AppRecord app = getAppRecordFromDragState(event);
                if (app == null) yield false;

                yield !dockItems.containsKey(app.launchComponent());
            }
            case DragEvent.ACTION_DRAG_LOCATION -> {

                int index = getDockIndexOfCoordinates((int) event.getX(), (int) event.getY());
                int placeholderIndex = itemsView.indexOfChild(dropPlaceholder);

                if (placeholderIndex == index) yield true;
                if (placeholderIndex >= 0 && placeholderIndex < index) index--;  // index shift from moving placeholder

                itemsView.removeView(dropPlaceholder);
                itemsView.addView(dropPlaceholder, index);
                yield true;
            }
            case DragEvent.ACTION_DRAG_EXITED -> {
                itemsView.removeView(dropPlaceholder);
                yield true;
            }
            case DragEvent.ACTION_DROP -> {
                itemsView.removeView(dropPlaceholder);
                AppRecord app = getAppRecordFromDragState(event);
                assert app != null;

                yield addApp(app, getDockIndexOfCoordinates((int) event.getX(), (int) event.getY()));
            }
            default -> true;
        };
    }

}
