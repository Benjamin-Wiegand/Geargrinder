package io.benwiegand.projection.geargrinder;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Path;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import java.util.concurrent.CountDownLatch;

import io.benwiegand.projection.geargrinder.makeshiftbind.MakeshiftBind;
import io.benwiegand.projection.geargrinder.makeshiftbind.MakeshiftBindCallback;
import io.benwiegand.projection.geargrinder.proto.data.readable.input.event.TouchEvent;

@SuppressLint("AccessibilityPolicy")
public class AccessibilityInputService extends AccessibilityService implements MakeshiftBindCallback {
    private static final String TAG = AccessibilityInputService.class.getSimpleName();

    private final Handler handler = new Handler(Looper.getMainLooper());

    private final ServiceBinder binder = new ServiceBinder();
    private MakeshiftBind makeshiftBind = null;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.i(TAG, "service connected");
        makeshiftBind = new MakeshiftBind(this, new ComponentName(this, AccessibilityInputService.class), this);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.i(TAG, "service death");
        makeshiftBind.destroy();
        return super.onUnbind(intent);
    }

    @Override
    public IBinder onMakeshiftBind(Intent intent) {
        return binder;
    }

    private final GestureResultCallback gestureResultCallback = new GestureResultCallback() {
        @Override
        public void onCompleted(GestureDescription gestureDescription) {
            Log.i(TAG, "Gesture completed");
        }

        @Override
        public void onCancelled(GestureDescription gestureDescription) {
            Log.w(TAG, "Gesture cancelled");
        }
    };

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {

    }

    @Override
    public void onInterrupt() {

    }



    private record TouchPointer(
            GestureDescription.StrokeDescription stroke,
            int x, int y
    ) { }

    private TouchPointer primaryPointer = null;

    private boolean emulateTouchEventInternal(TouchEvent event) {
        // doesn't work on virtual displays, sadly. should be useful for screen mirroring mode.
        GestureDescription.Builder builder = new GestureDescription.Builder();

        // TODO: multitouch should be possible

        TouchEvent.PointerLocation pointerLocation = event.pointerLocations()[0];
        int x = pointerLocation.x(), y = pointerLocation.y();

        GestureDescription.StrokeDescription strokeDescription = null;

        switch (event.action()) {
            case DOWN -> {
                Path path = new Path();
                path.moveTo(x, y);
                strokeDescription = new GestureDescription.StrokeDescription(path, 0, 1, true);
                primaryPointer = new TouchPointer(strokeDescription, x, y);
            }
            case UP -> {
                if (primaryPointer == null) break;
                Path path = new Path();
                path.moveTo(x, y);
                strokeDescription = primaryPointer.stroke().continueStroke(path, 0, 1, false);
                primaryPointer = null;
            }
            case MOVE -> {
                if (primaryPointer == null) break;
                Path path = new Path();
                path.moveTo(primaryPointer.x(), primaryPointer.y());
                path.lineTo(x, y);
                strokeDescription = primaryPointer.stroke().continueStroke(path, 0, 1, true);
                primaryPointer = new TouchPointer(strokeDescription, x, y);
            }
            case CANCEL -> {
                //TODO
            }
            case OUTSIDE -> {
                //TODO
            }
            case POINTER_DOWN -> {
                //TODO: multitouch
            }
            case POINTER_UP -> {
                //TODO: multitouch
            }
        }

        if (strokeDescription == null) return false;
        builder.addStroke(strokeDescription);
        return dispatchGesture(builder.build(), gestureResultCallback, null);
    }

    public class ServiceBinder extends Binder {

        public boolean emulateTouchEvent(TouchEvent event) {
            if (Looper.getMainLooper().isCurrentThread()) {
                emulateTouchEventInternal(event);
            }

            CountDownLatch latch = new CountDownLatch(1);
            boolean[] result = new boolean[] {false};
            boolean posted = handler.post(() -> {
                try {
                    result[0] = emulateTouchEventInternal(event);
                } finally {
                    latch.countDown();
                }
            });

            if (!posted) return false;
            try {
                latch.await(/* TODO */);
            } catch (InterruptedException e) {
                Log.e(TAG, "emulateTouchEvent() interrupted");
            }
            return result[0];
        }
    }
}
