package io.benwiegand.projection.geargrinder.ui;

import static io.benwiegand.projection.geargrinder.util.UiUtil.dpToPx;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.tabs.TabLayout;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import io.benwiegand.projection.geargrinder.PackageService;
import io.benwiegand.projection.geargrinder.R;
import io.benwiegand.projection.geargrinder.callback.AppLauncherListener;
import io.benwiegand.projection.geargrinder.pm.AppCategory;
import io.benwiegand.projection.geargrinder.pm.AppRecord;
import io.benwiegand.projection.geargrinder.thread.ResourceLoaderThread;

public class AppDrawer implements PackageService.PackageServiceListener, TabLayout.OnTabSelectedListener {
    private static final long DRAWER_ANIMATION_DURATION = 200;
    private static final int DEFAULT_MAX_DRAWER_COLUMNS = 6;

    private static final long APP_LOAD_ANIMATION_DURATION = 200;
    private static final long ICON_LOAD_THREAD_KEEPALIVE = 1000;

    // 100dp + 6dp margin
    private static final int ICON_SIZE_DP = 100 + 6 * 2;

    private record TabInfo(@StringRes int title, Function<PackageService.ServiceBinder, List<AppRecord>> listGetter) {
        public TabInfo(AppCategory category) {
            this(category.getLabel(), b -> b.getAppsFor(category));
        }

        public List<AppRecord> getAppList(PackageService.ServiceBinder binder) {
            return listGetter().apply(binder);
        }
    }

    private static final TabInfo ALL_APPS_TAB = new TabInfo(R.string.category_all_apps, PackageService.ServiceBinder::getAllApps);

    private static final TabInfo[] definedTabInfos = new TabInfo[] {
            new TabInfo(AppCategory.FOCUSED),
            new TabInfo(AppCategory.NAVIGATION),
            new TabInfo(AppCategory.MEDIA),
            ALL_APPS_TAB,
    };

    private static final TabInfo[] fallbackTabInfos = new TabInfo[] {
            ALL_APPS_TAB,
    };

    private final View rootView;
    private final Context context;

    private final Adapter adapter;
    private final GridLayoutManager layoutManager;

    private PackageService.ServiceBinder packageBinder = null;
    private boolean open = false;

    private TabInfo[] currentTabInfos = definedTabInfos;

    public AppDrawer(View rootView, AppLauncherListener listener) {
        this.rootView = rootView;
        context = rootView.getContext();


        // app list
        layoutManager = new GridLayoutManager(context, DEFAULT_MAX_DRAWER_COLUMNS);
        adapter = new Adapter(context, listener);

        RecyclerView recycler = rootView.findViewById(R.id.app_drawer_recycler);
        recycler.setLayoutManager(layoutManager);
        recycler.setAdapter(adapter);

        // categories
        TabLayout categoryTabs = rootView.findViewById(R.id.app_drawer_categories);
        categoryTabs.addOnTabSelectedListener(this);

        // close button
        rootView.findViewById(R.id.app_drawer_close_button)
                .setOnClickListener(v -> close());

        // layout updates
        rootView.getViewTreeObserver().addOnGlobalLayoutListener(this::onLayoutUpdated);

        // nothing to show until initial package list comes in
        showCategoriesLoadingIndicator();
    }

    public void destroy() {
        adapter.destroy();
    }

    private void onLayoutUpdated() {
        RecyclerView recycler = rootView.findViewById(R.id.app_drawer_recycler);
        if (recycler.getWidth() == 0) return;

        float iconWidth = dpToPx(context, ICON_SIZE_DP);
        int appListWidth = recycler.getWidth() - (recycler.getPaddingStart() + recycler.getPaddingEnd());
        int maxCols = (int) (appListWidth / iconWidth);
        if (maxCols == 0) maxCols = 1;
        if (maxCols > DEFAULT_MAX_DRAWER_COLUMNS) maxCols = DEFAULT_MAX_DRAWER_COLUMNS;

        layoutManager.setSpanCount(maxCols);
    }

    private void showCategoriesLoadingIndicator() {
        rootView.findViewById(R.id.app_drawer_loading_indicator)
                .setVisibility(View.VISIBLE);
        rootView.findViewById(R.id.app_drawer_recycler)
                .setVisibility(View.GONE);
        rootView.findViewById(R.id.app_drawer_categories)
                .setVisibility(View.GONE);
    }

    private void hideLoadingIndicator() {
        rootView.findViewById(R.id.app_drawer_loading_indicator)
                .setVisibility(View.GONE);
        rootView.findViewById(R.id.app_drawer_recycler)
                .setVisibility(View.VISIBLE);
        rootView.findViewById(R.id.app_drawer_categories)
                .setVisibility(View.VISIBLE);
    }

    public void setPackageBinder(PackageService.ServiceBinder packageBinder) {
        this.packageBinder = packageBinder;
        showCategoriesLoadingIndicator();
        packageBinder.registerListener(this);
    }

    public void open() {
        if (isOpen()) return;

        rootView.setVisibility(View.VISIBLE);


        if (rootView.getTranslationY() == 0) {
            rootView.setTranslationY(rootView.getHeight());
            rootView.setAlpha(0);
        }

        // when on top of SurfaceView (like the one found in VirtualActivity) this doesn't show up sometimes weirdly
        // setting/animating the z seems to fix it
        rootView.setZ(rootView.getHeight());

        rootView.animate()
                .setStartDelay(0)
                .setDuration(DRAWER_ANIMATION_DURATION)
                .translationY(0)
                .alpha(1)
                .z(0)
                .start();
        open = true;
    }

    public void close() {
        if (!isOpen()) return;

        rootView.animate()
                .setStartDelay(0)
                .setDuration(DRAWER_ANIMATION_DURATION)
                .translationY(rootView.getHeight())
                .alpha(0)
                .withEndAction(() -> rootView.setVisibility(View.GONE))
                .start();
        open = false;
    }

    public boolean isOpen() {
        return open;
    }

    public void toggle() {
        if (isOpen()) close();
        else open();
    }

    private TabInfo getSelectedTabInfo() {
        TabLayout categoryTabs = rootView.findViewById(R.id.app_drawer_categories);
        int pos = categoryTabs.getSelectedTabPosition();
        if (pos < 0 || pos >= currentTabInfos.length) return null;
        return currentTabInfos[pos];
    }

    @Override
    public void onPackageListUpdated(PackageService.ServiceBinder binder) {
        hideLoadingIndicator();

        TabInfo currentCategory = getSelectedTabInfo();

        TabLayout categoryTabsView = rootView.findViewById(R.id.app_drawer_categories);
        categoryTabsView.removeAllTabs();

        currentTabInfos = Arrays.stream(definedTabInfos)
                .filter(info -> !info.getAppList(binder).isEmpty())
                .toArray(TabInfo[]::new);

        // at least one tab must exist
        if (currentTabInfos.length == 0)
            currentTabInfos = fallbackTabInfos;

        for (TabInfo info : currentTabInfos) {
            TabLayout.Tab tab = categoryTabsView.newTab()
                    .setText(info.title());

            categoryTabsView.addTab(tab);

            if (info == currentCategory)
                categoryTabsView.selectTab(tab);
        }

        // ensure a tab gets selected
        if (getSelectedTabInfo() == null)
            categoryTabsView.selectTab(categoryTabsView.getTabAt(0));

    }

    @Override
    public void onTabSelected(TabLayout.Tab tab) {
        if (packageBinder == null) return;
        TabInfo info = getSelectedTabInfo();
        List<AppRecord> apps = info != null ? info.getAppList(packageBinder) : List.of();
        adapter.setApps(apps);
    }

    @Override
    public void onTabUnselected(TabLayout.Tab tab) { }

    @Override
    public void onTabReselected(TabLayout.Tab tab) { }

    private static class Adapter extends RecyclerView.Adapter<IconViewHolder> {
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final ResourceLoaderThread iconLoader = new ResourceLoaderThread(handler, ICON_LOAD_THREAD_KEEPALIVE);
        private final AppLauncherListener listener;
        private final PackageManager pm;
        private List<AppRecord> apps = List.of();

        private Adapter(Context context, AppLauncherListener listener) {
            pm = context.getPackageManager();
            this.listener = listener;
        }

        public void destroy() {
            iconLoader.destroy();
        }

        @SuppressLint("NotifyDataSetChanged")
        public void setApps(List<AppRecord> apps) {
            this.apps = apps;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public IconViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.layout_app_drawer_icon, parent, false);
            return new IconViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull IconViewHolder holder, int position) {
            View view = holder.itemView;
            AppRecord app = apps.get(position);

            TextView labelView = view.findViewById(R.id.app_name);
            ImageView iconView = view.findViewById(R.id.app_icon);
            View touchTarget = view.findViewById(R.id.touch_target);

            holder.setCurrentApp(app);
            view.setAlpha(0);
            touchTarget.setOnClickListener(v -> listener.onAppSelected(app));

            touchTarget.setOnLongClickListener(v -> view.startDragAndDrop(null, new View.DragShadowBuilder(iconView), app, 0));

            // TODO: seems to still stutter sometimes even with all of this (but only for certain icons?)
            //       might need a bitmap cache
            record AppResources(CharSequence label, Drawable icon) { }
            iconLoader.execute(
                    () -> new AppResources(app.label(pm), app.icon(pm)),
                    r -> {
                        if (!holder.isCurrentApp(app)) return;

                        labelView.setText(app.label(pm));
                        iconView.setImageDrawable(r.icon());

                        view.animate()
                                .setStartDelay(0)
                                .setDuration(APP_LOAD_ANIMATION_DURATION)
                                .alpha(1f)
                                .start();
                    }
            );
        }

        @Override
        public int getItemCount() {
            return apps.size();
        }
    }

    private static class IconViewHolder extends RecyclerView.ViewHolder {
        private AppRecord currentApp;

        public IconViewHolder(@NonNull View itemView) {
            super(itemView);
        }

        public void setCurrentApp(AppRecord currentApp) {
            this.currentApp = currentApp;
        }

        public boolean isCurrentApp(AppRecord app) {
            return currentApp == app;
        }
    }
}
