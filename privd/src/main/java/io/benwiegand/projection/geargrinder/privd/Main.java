package io.benwiegand.projection.geargrinder.privd;

import static io.benwiegand.projection.libprivd.ipc.IPCConstants.APP_PKG_NAME;
import static io.benwiegand.projection.libprivd.ipc.IPCConstants.ENV_TOKEN;
import static io.benwiegand.projection.libprivd.ipc.IPCConstants.ENV_TOKEN_PATH;
import static io.benwiegand.projection.libprivd.ipc.IPCConstants.INTENT_ACTION_BIND_PRIVD;
import static io.benwiegand.projection.libprivd.ipc.IPCConstants.INTENT_EXTRA_BINDER;
import static io.benwiegand.projection.libprivd.ipc.IPCConstants.INTENT_EXTRA_TOKEN;
import static io.benwiegand.projection.libprivd.ipc.IPCConstants.TOKEN_LENGTH;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;

import java.io.FileInputStream;
import java.io.IOException;

import io.benwiegand.projection.geargrinder.privd.reflection.ReflectionException;
import io.benwiegand.projection.geargrinder.privd.reflected.ReflectedActivityThread;

public class Main {
    private static final String TAG = "privd-" + Main.class.getSimpleName();

    public static void main(String[] args) {
        Log.i(TAG, "Geargrinder privd");

        // env
        byte[] token;
        try {
            String tokenPath = System.getenv(ENV_TOKEN_PATH);
            String tokenString = System.getenv(ENV_TOKEN);

            if (tokenString != null) {
                token = Base64.decode(tokenString, 0);
            } else if (tokenPath != null) {

                Log.i(TAG, "reading token from " + tokenPath);
                token = new byte[TOKEN_LENGTH];
                int offset = 0;
                int len;
                try (FileInputStream is = new FileInputStream(tokenPath)) {
                    do {
                        len = is.read(token, offset, token.length - offset);
                        if (len < 0) {
                            Log.e(TAG, "token file missing " + (token.length - offset) + " bytes");
                            throw new IOException("unexpected end of stream");
                        }

                        offset += len;
                    } while (offset < token.length);
                }

            } else {
                throw new AssertionError("missing required environment variable(s)");
            }

        } catch (Throwable t) {
            Log.e(TAG, "failed to parse environment", t);
            System.exit(1);
            return;
        }


        // context
        Looper.prepareMainLooper();
        ReflectedActivityThread activityThread;
        Context context;
        try {
            Log.i(TAG, "starting activity thread");
            activityThread = ReflectedActivityThread.systemMain();

            Context systemContext = activityThread.getSystemContext();
            Log.i(TAG, "got a system context: " + systemContext);

            context = new FakeContext(systemContext);

        } catch (ReflectionException e) {
            Log.e(TAG, "failed to get system context", e);
            System.exit(1);
            return;
        }

        // binder
        PackageManager pm = context.getPackageManager();
        int appUid;
        try {
            appUid = pm.getPackageUid(APP_PKG_NAME, 0);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "failed to get app UID", e);
            System.exit(1);
            return;
        }

        Privd privd = new Privd(context, appUid);

        // bind broadcast intent
        Bundle bundle = new Bundle();
        bundle.putByteArray(INTENT_EXTRA_TOKEN, token);
        bundle.putBinder(INTENT_EXTRA_BINDER, privd);

        Intent intent = new Intent(INTENT_ACTION_BIND_PRIVD)
                .setPackage(APP_PKG_NAME)
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                .putExtras(bundle);

        try {
            Log.i(TAG, "sending bind broadcast");
            activityThread.getApplication().sendBroadcast(intent);
        } catch (ReflectionException e) {
            Log.e(TAG, "failed to send bind broadcast", e);
            System.exit(1);
            return;
        }

        // loop until death
        Looper.loop();
    }
}
