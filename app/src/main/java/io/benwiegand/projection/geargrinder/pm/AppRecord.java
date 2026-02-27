package io.benwiegand.projection.geargrinder.pm;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.util.Log;

import java.util.Set;

public record AppRecord(
        String packageName,
        String launchActivity,
        Set<AppCategory> categories,
        Set<CarFeature> carFeatures
) {
    private static final String TAG = AppRecord.class.getSimpleName();

    public ComponentName launchComponent() {
        return new ComponentName(packageName(), launchActivity());
    }

    public boolean has(AppCategory category) {
        return categories().contains(category);
    }

    public boolean has(CarFeature feature) {
        return carFeatures().contains(feature);
    }

    public PackageInfo packageInfo(PackageManager pm) {
        try {
            return pm.getPackageInfo(packageName(), 0);
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "failed to find package for " + this);
            return null;
        }
    }

    public ApplicationInfo applicationInfo(PackageManager pm) {
        try {
            return pm.getApplicationInfo(packageName(), 0);
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "failed to find application for " + this);
            return null;
        }
    }

    public ActivityInfo activityInfo(PackageManager pm) {
        try {
            return pm.getActivityInfo(launchComponent(), 0);
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "failed to find activity for " + this);
            return null;
        }
    }

    public Drawable icon(PackageManager pm) {
        try {
            return pm.getActivityIcon(launchComponent());
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "failed to find activity icon for " + this);
            return pm.getDefaultActivityIcon();
        }
    }

    public CharSequence label(PackageManager pm) {
        ApplicationInfo app = applicationInfo(pm);
        if (app == null) {
            Log.e(TAG, "failed to find a label for " + this);
            return packageName();
        }

        return pm.getApplicationLabel(app);
    }

    public static AppRecord createForPackage(PackageManager pm, String packageName) {
        Intent launchIntent = pm.getLaunchIntentForPackage(packageName);
        if (launchIntent == null || launchIntent.getComponent() == null)
            return null;    // nothing to launch, nothing to see

        PackageInfo pkg;
        try {
            pkg = pm.getPackageInfo(packageName, PackageManager.MATCH_ALL | PackageManager.GET_META_DATA);
        } catch (PackageManager.NameNotFoundException e) {
            Log.wtf(TAG, "failed to find package: " + packageName);
            assert false;
            return null;
        }

        Set<CarFeature> carFeatures = CarFeature.getFeaturesForPackage(pm, pkg);
        return new AppRecord(
                packageName,
                launchIntent.getComponent().getClassName(),
                AppCategory.getCategoriesForPackage(pm, pkg, carFeatures),
                carFeatures
        );

        // TODO
//        pkg.applicationInfo.category;
    }

}
