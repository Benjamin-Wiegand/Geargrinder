package io.benwiegand.projection.geargrinder.privd;

import android.content.Context;
import android.os.Looper;
import android.util.Log;

import io.benwiegand.projection.geargrinder.privd.reflection.ReflectionException;
import io.benwiegand.projection.geargrinder.privd.reflection.reflected.ReflectedActivityThread;

public class Main {

    public static void main(String[] args) {
        Log.wtf("Testing123", "hello from privd");

        Looper.prepareMainLooper();

        try {
            ReflectedActivityThread activityThread = new ReflectedActivityThread();

            Context context = activityThread.getSystemContext();
            System.out.println("got a system context: " + context);

        } catch (ReflectionException e) {
            throw new RuntimeException(e);
        }

    }
}
