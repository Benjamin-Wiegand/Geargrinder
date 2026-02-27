package io.benwiegand.projection.geargrinder;

import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.icu.text.Collator;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import io.benwiegand.projection.geargrinder.data.EnumCategoryLookup;
import io.benwiegand.projection.geargrinder.pm.AppCategory;
import io.benwiegand.projection.geargrinder.pm.AppRecord;

public class PackageService extends Service {
    private static final String TAG = PackageService.class.getSimpleName();

    private static final float PACKAGE_NAME_LOOKUP_LOAD_FACTOR = 0.75f; // default load factor

    // TODO: handler might not be the best for this
    private final HandlerThread updateHandlerThread = new HandlerThread("geargrinder-pkg-scan");
    private final Handler updateHandler;
    private final Handler callbackHandler = new Handler(Looper.getMainLooper());
    private final ServiceBinder binder = new ServiceBinder();

    private final Object appListUpdateLock = new Object();
    private List<AppRecord> allApps = List.of();
    private EnumCategoryLookup<AppCategory, AppRecord> categoryLookup = EnumCategoryLookup.makeEmptyImmutable(AppCategory.class);
    private Map<String, AppRecord> packageNameLookup = Map.of();
    private boolean appListReady = false;

    private final List<PackageServiceListener> listeners = new ArrayList<>();


    public interface PackageServiceListener {
        void onPackageListUpdated(ServiceBinder binder);
    }

    public PackageService() {
        updateHandlerThread.start();
        updateHandler = new Handler(updateHandlerThread.getLooper());
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");
        rebuildAppList();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        updateHandlerThread.quit();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    private void callListeners(Consumer<PackageServiceListener> consumer) {
        callbackHandler.post(() -> {
            synchronized (listeners) {
                for (PackageServiceListener listener : listeners) {
                    try {
                        consumer.accept(listener);
                    } catch (Throwable t) {
                        Log.wtf(TAG, "exception in package service listener", t);
                    }
                }
            }
        });
    }

    private void rebuildAppList() {
        updateHandler.post(() -> {
            Log.i(TAG, "building app list");

            try {
                PackageManager pm = getPackageManager();
                List<PackageInfo> packages = pm.getInstalledPackages(PackageManager.MATCH_ALL);

                List<AppRecord> newApps = new ArrayList<>(packages.size());
                EnumCategoryLookup<AppCategory, AppRecord> newCategoryLookup = new EnumCategoryLookup<>(AppCategory.class);
                Map<String, AppRecord> newPackageNameLookup = new HashMap<>(packages.size(), PACKAGE_NAME_LOOKUP_LOAD_FACTOR);

                Collator collator = Collator.getInstance();
                packages.stream()
                        .map(pkg -> AppRecord.createForPackage(pm, pkg.packageName))
                        .filter(Objects::nonNull)
                        .sorted((a, b) -> collator.compare(a.label(pm), b.label(pm)))
                        .forEachOrdered(app -> {
                            newApps.add(app);
                            newPackageNameLookup.put(app.packageName(), app);
                            for (AppCategory category : app.categories())
                                newCategoryLookup.add(category, app);
                        });

                synchronized (appListUpdateLock) {
                    newCategoryLookup.makeImmutable();

                    allApps = List.copyOf(newApps);
                    categoryLookup = newCategoryLookup;
                    packageNameLookup = Map.copyOf(newPackageNameLookup);

                    Log.i(TAG, "app list update completed");
                    Log.d(TAG, "total apps: " + allApps.size());
                    for (AppCategory category : AppCategory.values()) {
                        Log.d(TAG, " - " + category + " apps: " + categoryLookup.size(category));
                        for (AppRecord app : categoryLookup.get(category)) {
                            Log.d(TAG, "    - " + app.label(pm) + " (" + app + ")");
                        }
                    }

                    appListReady = true;
                    callListeners(l -> l.onPackageListUpdated(binder));
                }


            } catch (Throwable t) {
                Log.wtf(TAG, "failed to update app list", t);
                throw t;
            }
        });
    }

    public class ServiceBinder extends Binder {

        public void registerListener(PackageServiceListener listener) {
            synchronized (listeners) {
                synchronized (appListUpdateLock) {
                    listeners.add(listener);
                    if (appListReady) listener.onPackageListUpdated(this);
                }
            }
        }

        public List<AppRecord> getAllApps() {
            synchronized (appListUpdateLock) {
                return allApps;
            }
        }

        public List<AppRecord> getAppsFor(AppCategory category) {
            synchronized (appListUpdateLock) {
                return categoryLookup.get(category);
            }
        }

        public AppRecord getApp(String packageName) {
            synchronized (appListUpdateLock) {
                return packageNameLookup.get(packageName);
            }
        }

    }
}
