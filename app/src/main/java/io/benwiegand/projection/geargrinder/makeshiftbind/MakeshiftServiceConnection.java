package io.benwiegand.projection.geargrinder.makeshiftbind;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.Objects;
import java.util.UUID;

/**
 * @see MakeshiftBind
 */
public abstract class MakeshiftServiceConnection implements ServiceConnection {
    private static final String TAG = MakeshiftServiceConnection.class.getSimpleName();

    private final String connectionId = UUID.randomUUID().toString();
    private final BroadcastReceiver binderReceiver = new BinderReceiver();
    private final IntentFilter filter = new IntentFilter();
    private Context context = null;

    protected MakeshiftServiceConnection() {}

    @Override
    public abstract void onServiceConnected(ComponentName name, IBinder service);
    @Override
    public abstract void onServiceDisconnected(ComponentName name);

    public void destroy() {
        if (context == null) return; // never used

        LocalBroadcastManager
                .getInstance(context)
                .unregisterReceiver(binderReceiver);
    }

    private void sendBindIntent(ComponentName component) {
        Log.d(TAG, "sending makeshift bind intent");
        Intent intent = new Intent(MakeshiftBind.createBinderRequestIntentAction(component));
        Bundle extras = new Bundle();
        extras.putString(MakeshiftBind.EXTRA_CONNECTION_ID, connectionId);
        intent.putExtras(extras);

        LocalBroadcastManager
                .getInstance(context)
                .sendBroadcast(intent);
    }

    private void bindFor(ComponentName componentName) {
        filter.addAction(MakeshiftBind.createServiceUpIntentAction(componentName));
        filter.addAction(MakeshiftBind.createServiceDownIntentAction(componentName));
        filter.addAction(MakeshiftBind.createBinderResponseIntentAction(componentName));

        LocalBroadcastManager
                .getInstance(context)
                .registerReceiver(binderReceiver, filter);

        sendBindIntent(componentName);
    }

    private class BinderReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            // these intents should always have extras
            Bundle extras = intent.getExtras();
            assert extras != null;

            // should always have component name
            String componentNameString = extras.getString(MakeshiftBind.EXTRA_COMPONENT_NAME);
            assert componentNameString != null;
            ComponentName component = ComponentName.unflattenFromString(componentNameString);
            assert component != null;

            // the connection id must be checked if present
            String connectionId = extras.getString(MakeshiftBind.EXTRA_CONNECTION_ID);
            if (connectionId != null && !connectionId.equals(MakeshiftServiceConnection.this.connectionId)) return; // not for this connection

            if (Objects.equals(intent.getAction(), MakeshiftBind.createBinderResponseIntentAction(component))) {
                // a binder has arrived
                assert connectionId != null;
                IBinder binder = extras.getBinder(MakeshiftBind.EXTRA_BINDER_INSTANCE);
                if (binder == null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        onNullBinding(component);
                    }
                } else {
                    onServiceConnected(component, binder);
                }

            } else if (Objects.equals(intent.getAction(), MakeshiftBind.createServiceUpIntentAction(component))) {
                // a service previously sought after is now up, so a bind request should be resent
                sendBindIntent(component);

            } else if (Objects.equals(intent.getAction(), MakeshiftBind.createServiceDownIntentAction(component))) {
                // a bound service has died
                assert connectionId != null;
                onServiceDisconnected(component);
                onBindingDied(component);

            } else {
                Log.wtf(TAG, "got unexpected intent for makeshift bind: " + intent);

            }
        }
    }

    public static void bindService(Context context, ComponentName componentName, MakeshiftServiceConnection serviceConnection) {
        if (serviceConnection.context != null && serviceConnection.context != context)
                throw new IllegalArgumentException("a service connection cannot be used across multiple different contexts!");

        serviceConnection.context = context;
        serviceConnection.bindFor(componentName);
    }
}
