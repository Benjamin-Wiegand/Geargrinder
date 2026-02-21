package io.benwiegand.projection.geargrinder.privd;

import static io.benwiegand.projection.libprivd.ipc.IPCConstants.APP_PKG_NAME;
import static io.benwiegand.projection.libprivd.ipc.IPCConstants.BIND_TIMEOUT;
import static io.benwiegand.projection.libprivd.ipc.IPCConstants.PING_TIMEOUT;

import android.app.ActivityOptions;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.hardware.input.InputManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.InputEvent;

import java.io.IOException;

import io.benwiegand.projection.geargrinder.privd.reflected.ReflectedIActivityManager;
import io.benwiegand.projection.geargrinder.privd.reflected.ReflectedInputEvent;
import io.benwiegand.projection.geargrinder.privd.reflected.ReflectedInputManager;
import io.benwiegand.projection.geargrinder.privd.reflection.ReflectionException;
import io.benwiegand.projection.libprivd.IPrivd;

public class Privd extends IPrivd.Stub {
    private static final String TAG = Privd.class.getSimpleName();
    private static final boolean LOG_DEBUG = true;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private final int appUid;
    private final ReflectedInputManager rim;
    private final ReflectedIActivityManager ram;

    private long lastPingAt = 0;

    public Privd(Context context, int appUid) {
        this.appUid = appUid;
        InputManager im = context.getSystemService(InputManager.class);
        rim = new ReflectedInputManager(im);

        ReflectedIActivityManager ram;
        try {
            ram = new ReflectedIActivityManager();
        } catch (ReflectionException e) {
            Log.e(TAG, "failed to initialize reflected ActivityManagerNative", e);
            ram = null;
        }

        this.ram = ram;

        // init timeout
        handler.postDelayed(() -> {
            if (lastPingAt != 0) return;
            Log.e(TAG, "timed out waiting for app to bind");
            System.exit(1);
        }, BIND_TIMEOUT);
    }

    @Override
    protected void checkCaller() {
        int callingUid = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? Binder.getCallingUidOrThrow() : Binder.getCallingUid();
        if (callingUid != appUid)
            throw new SecurityException("only for use by " + APP_PKG_NAME);
    }

    @Override
    public void ping() {
        if (LOG_DEBUG) Log.d(TAG, "ping");
        long pingAt = SystemClock.elapsedRealtime();
        lastPingAt = pingAt;

        // not the most efficient way to do this but it works
        handler.postDelayed(() -> {
            if (lastPingAt > pingAt) return;
            Log.w(TAG, "ping timeout reached");
            System.exit(0);
        }, PING_TIMEOUT);
    }

    @Override
    public boolean injectInputEvent(InputEvent event) {
        if (LOG_DEBUG) Log.d(TAG, "injecting input event: " + event);
        try {
            return rim.injectInputEvent(event, ReflectedInputManager.INJECT_MODE_ASYNC);
        } catch (ReflectionException e) {
            Log.e(TAG, "reflection exception while injecting input event", e);
            throw new RuntimeException("failed to inject input event", e);
        }
    }

    @Override
    public boolean injectInputEvent(InputEvent event, int displayId) {
        try {
            if (LOG_DEBUG) Log.d(TAG, "setting display id to " + displayId + " for input event: " + event);
            ReflectedInputEvent rEvent = new ReflectedInputEvent(event);
            rEvent.setDisplayId(displayId);
            return injectInputEvent(event);
        } catch (ReflectionException e) {
            Log.e(TAG, "failed to set display id", e);
            throw new RuntimeException("failed to set display id for input event", e);
        }
    }

    @Override
    public int launchActivity(ComponentName component, int displayId) {
        Log.v(TAG, "launching activity on display " + displayId + ": " + component.flattenToShortString());

        if (ram != null) {
            try {
                Intent intent = new Intent()
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                        .setComponent(component);

                ActivityOptions opts = ActivityOptions.makeBasic();
                opts.setLaunchDisplayId(displayId);

                return ram.startActivity(intent, opts.toBundle());
            } catch (ReflectionException | SecurityException e) {
                Log.w(TAG, "failed to launch activity via IActivityManager", e);
            }
        }

        Log.w(TAG, "falling back to shell command for activity launch");
        try {
            return new ProcessBuilder("am", "start-activity", "--display", String.valueOf(displayId), component.flattenToShortString())
                    .start()
                    .waitFor();
        } catch (IOException e) {
            Log.e(TAG, "IOException while starting activity via shell", e);
            throw new RuntimeException("failed to launch activity");
        } catch (InterruptedException e) {
            Log.e(TAG, "interrupted");
            throw new RuntimeException("interrupted");
        }
    }

    @Override
    public IBinder asBinder() {
        return this;
    }
}
