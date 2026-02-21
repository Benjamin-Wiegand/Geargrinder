package io.benwiegand.projection.geargrinder;

import static io.benwiegand.projection.libprivd.ipc.IPCConstants.INTENT_ACTION_BIND_PRIVD;
import static io.benwiegand.projection.libprivd.ipc.IPCConstants.INTENT_EXTRA_BINDER;
import static io.benwiegand.projection.libprivd.ipc.IPCConstants.INTENT_EXTRA_TOKEN;
import static io.benwiegand.projection.libprivd.ipc.IPCConstants.TOKEN_LENGTH;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import java.io.IOException;
import java.util.Arrays;

import io.benwiegand.projection.geargrinder.privileged.RootPrivdLauncher;

public class PrivdConnectionReceiver extends BroadcastReceiver {
    private static final String TAG = PrivdConnectionReceiver.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!INTENT_ACTION_BIND_PRIVD.equals(intent.getAction())) return;

        Bundle bundle = intent.getExtras();
        if (bundle == null) return;

        byte[] suppliedToken = bundle.getByteArray(INTENT_EXTRA_TOKEN);
        if (suppliedToken == null) return;
        if (suppliedToken.length != TOKEN_LENGTH) return;

        try {
            byte[] token = RootPrivdLauncher.readToken(context);
            if (token == null) return;
            if (!Arrays.equals(token, suppliedToken)) return;
        } catch (IOException e) {
            Log.w(TAG, "failed to read token", e);
            return;
        }

        Log.i(TAG, "got broadcast from privd: " + intent);

        IBinder binder = bundle.getBinder(INTENT_EXTRA_BINDER);
        if (binder == null) {
            Log.wtf(TAG, "binder is null");
            return;
        }

        bundle.clear();
        bundle.putBinder(INTENT_EXTRA_BINDER, binder);

        context.startService(new Intent(context, PrivdService.class)
                .setAction(INTENT_ACTION_BIND_PRIVD)
                .putExtras(bundle));

    }
}
