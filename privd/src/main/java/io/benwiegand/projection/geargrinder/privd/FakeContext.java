package io.benwiegand.projection.geargrinder.privd;

import android.content.AttributionSource;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Process;
import android.util.Log;

public class FakeContext extends ContextWrapper {
    private static final String TAG = FakeContext.class.getSimpleName();

    private final String fakePackageName;

    public FakeContext(Context base) {
        super(base);

        PackageManager pm = getPackageManager();
        String[] packages = pm.getPackagesForUid(Process.myUid());
        fakePackageName = packages != null ? packages[0] : null;

        Log.i(TAG, "using fake package name: " + fakePackageName);
    }

    @Override
    public String getPackageName() {
        return fakePackageName;
    }

    @Override
    public String getOpPackageName() {
        return fakePackageName;
    }

    @Override
    public AttributionSource getAttributionSource() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) throw new AssertionError();
        return new AttributionSource.Builder(Process.myUid())
                .setPackageName(fakePackageName)
                .build();
    }

    @Override
    public Context getApplicationContext() {
        return this;
    }
}
