package io.benwiegand.projection.geargrinder.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

import java.util.HashMap;

import io.benwiegand.projection.geargrinder.R;
import io.benwiegand.projection.geargrinder.pm.AppRecord;

public class AppDock {
    private static final String TAG = AppDock.class.getSimpleName();

    private final Context context;
    private final PackageManager pm;
    private final View rootView;
    private final LinearLayout itemsView;

    private final AppDockListener listener;

    private final HashMap<ComponentName, View> dockItems = new HashMap<>();

    public interface AppDockListener {
        void onAppSelected(AppRecord app);
        void onAppDrawerSelected();
    }


    public AppDock(View rootView, AppDockListener listener) {
        this.rootView = rootView;
        this.listener = listener;
        context = rootView.getContext();
        pm = context.getPackageManager();

        itemsView = rootView.findViewById(R.id.dock_items);

        rootView.findViewById(R.id.app_drawer_button)
                .setOnClickListener(v -> listener.onAppDrawerSelected());
    }

    public View getRootView() {
        return rootView;
    }

    public void addApp(AppRecord app) {
        if (dockItems.containsKey(app.launchComponent())) return;

        LayoutInflater inflater = LayoutInflater.from(context);
        View dockItemView = inflater.inflate(R.layout.layout_dock_item, itemsView, false);

        ImageView iconView = dockItemView.findViewById(R.id.app_icon);
        iconView.setImageDrawable(app.icon(pm));

        dockItemView.findViewById(R.id.touch_target)
                .setOnClickListener(v -> listener.onAppSelected(app));

        itemsView.addView(dockItemView);
        dockItems.put(app.launchComponent(), dockItemView);
    }

}
