package io.benwiegand.projection.geargrinder.makeshiftbind;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.LinkedList;
import java.util.Queue;

// TODO: LocalBroadcastManager is deprecated
/**
 * a recreation of the binder api for services that don't support the binder api.
 * it excludes some features that aren't used in this app.
 */
public class MakeshiftBind {
    private static final String TAG = MakeshiftBind.class.getSimpleName();

    public static final String EXTRA_BINDER_INSTANCE = "binder";
    public static final String EXTRA_CONNECTION_ID = "id";
    public static final String EXTRA_COMPONENT_NAME = "component";

    private final BroadcastReceiver bindRequestReceiver = new BindRequestReceiver();
    private final Queue<String> connectionIds = new LinkedList<>();
    private final Context context;
    private final ComponentName component;
    private final MakeshiftBindCallback bindCallback;

    // service-side. attempts to mirror the general behavior of the normal onBind()

    public MakeshiftBind(Context context, ComponentName component, MakeshiftBindCallback bindCallback) {
        this.context = context;
        this.component = component;
        this.bindCallback = bindCallback;

        IntentFilter filter = new IntentFilter();
        filter.addAction(createBinderRequestIntentAction(component));
        LocalBroadcastManager
                .getInstance(context)
                .registerReceiver(bindRequestReceiver, filter);

        sendReadyIntent();
    }

    public void destroy() {
        LocalBroadcastManager
                .getInstance(context)
                .unregisterReceiver(bindRequestReceiver);

        while (!connectionIds.isEmpty()) {
            sendDeathIntent(connectionIds.poll());
        }
    }

    private void sendReadyIntent() {
        Log.d(TAG, "sending makeshift bind ready intent");
        Intent intent = new Intent(createServiceUpIntentAction(component));
        Bundle extras = new Bundle();
        extras.putString(EXTRA_COMPONENT_NAME, component.flattenToString());
        intent.putExtras(extras);

        LocalBroadcastManager
                .getInstance(context)
                .sendBroadcast(intent);
    }

    public void sendResponseIntent(String connectionId, IBinder binder) {
        Log.d(TAG, "sending makeshift bind response intent");
        Intent intent = new Intent(createBinderResponseIntentAction(component));
        Bundle extras = new Bundle();
        extras.putString(EXTRA_COMPONENT_NAME, component.flattenToString());
        extras.putBinder(EXTRA_BINDER_INSTANCE, binder);
        extras.putString(EXTRA_CONNECTION_ID, connectionId);
        intent.putExtras(extras);

        LocalBroadcastManager
                .getInstance(context)
                .sendBroadcast(intent);
    }

    public void sendDeathIntent(String connectionId) {
        Log.d(TAG, "sending makeshift bind death intent");
        Intent intent = new Intent(createServiceDownIntentAction(component));
        Bundle extras = new Bundle();
        extras.putString(EXTRA_COMPONENT_NAME, component.flattenToString());
        extras.putString(EXTRA_CONNECTION_ID, connectionId);
        intent.putExtras(extras);

        LocalBroadcastManager
                .getInstance(context)
                .sendBroadcast(intent);
    }

    public class BindRequestReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String connectionId = intent.getStringExtra(EXTRA_CONNECTION_ID);
            Log.d(TAG, "got binder request: " + connectionId);
            IBinder binder = bindCallback.onMakeshiftBind(intent);
            connectionIds.add(connectionId);
            sendResponseIntent(connectionId, binder);
        }
    }

    static String createServiceUpIntentAction(ComponentName component) {
        return component.getPackageName() + component.getClassName() + ".MAKESHIFT_BIND_READY";
    }

    static String createBinderRequestIntentAction(ComponentName component) {
        return component.getPackageName() + component.getClassName() + ".MAKESHIFT_BIND_REQUEST";
    }

    static String createServiceDownIntentAction(ComponentName component) {
        return component.getPackageName() + component.getClassName() + ".MAKESHIFT_BINDER_DEATH";
    }

    static String createBinderResponseIntentAction(ComponentName component) {
        return component.getPackageName() + component.getClassName() + ".MAKESHIFT_BINDER";
    }
}
