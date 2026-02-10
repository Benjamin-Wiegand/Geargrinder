package io.benwiegand.projection.geargrinder;

import static io.benwiegand.projection.geargrinder.util.UsbUtil.findUsbHeadunit;

import android.app.Activity;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.activity.result.ActivityResult;
import androidx.annotation.Nullable;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import javax.net.ssl.KeyManager;
import javax.net.ssl.TrustManager;

import io.benwiegand.projection.geargrinder.crypto.KeystoreManager;
import io.benwiegand.projection.geargrinder.crypto.TLSService;
import io.benwiegand.projection.geargrinder.message.AAFrame;
import io.benwiegand.projection.geargrinder.message.MessageBroker;
import io.benwiegand.projection.geargrinder.protocol.AAConstants;
import io.benwiegand.projection.geargrinder.callback.ControlListener;
import io.benwiegand.projection.geargrinder.channel.ControlChannel;
import io.benwiegand.projection.geargrinder.transfer.UsbTransferInterface;

public class ConnectionService extends Service implements ControlListener {
    private static final String TAG = ConnectionService.class.getSimpleName();

    public static final String INTENT_ACTION_CONNECT_USB = "io.benwiegand.projection.geargrinder.USB_HEADUNIT_CONNECTED";
    public static final String INTENT_ACTION_START_MEDIA_PROJECTION = "io.benwiegand.projection.geargrinder.START_MEDIA_PROJECTION";

    public static final String INTENT_EXTRA_MEDIA_PROJECTION_PERMISSION_RESULT = "projection_result";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ServiceBinder binder = new ServiceBinder();

    private final Object lock = new Object();
    private Thread connectionThread = null;

    private KeyManager[] keyManagers;
    private TrustManager[] trustManagers;
    private ConnectionNotificationService notificationService;

    private MediaProjection mediaProjection = null;
    private MediaProjectionRequestCallback mediaProjectionRequestCallback = null;


    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "on create");

        notificationService = new ConnectionNotificationService(this);

        try {
            KeystoreManager keystoreManager = new KeystoreManager(this);
            keystoreManager.loadKeystore();
            keystoreManager.initKeypair("deez");    // TODO
            keystoreManager.saveKeystore();

            keyManagers = keystoreManager.getKeyManagers();
            trustManagers = keystoreManager.getTrustManagers();

        } catch (Throwable t) {
            // TODO: properly handle this
            throw new RuntimeException(t);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "on destroy");
        notificationService.destroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            Log.e(TAG, "intent is null");
            return START_NOT_STICKY;
        }

        Log.d(TAG, "start intent: " + intent);
        switch (intent.getAction()) {
            case INTENT_ACTION_CONNECT_USB -> connectUsb();
            case INTENT_ACTION_START_MEDIA_PROJECTION -> startMediaProjection(intent);
            case null -> Log.e(TAG, "no intent action");
            default -> Log.wtf(TAG, "intent action not handled: " + intent.getAction());
        }

        return START_NOT_STICKY;
    }

    private final MediaProjection.Callback mediaProjectionCallback = new MediaProjection.Callback() {
        @Override
        public void onStop() {
            synchronized (lock) {
                Log.i(TAG, "media projection stopped");
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    notificationService.removeForegroundFlag(ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
                mediaProjection = null;
            }
        }
    };

    private void startMediaProjection(Intent intent) {
        ActivityResult startResult;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            startResult = intent.getParcelableExtra(INTENT_EXTRA_MEDIA_PROJECTION_PERMISSION_RESULT, ActivityResult.class);
        } else {
            if (intent.getExtras() == null) {
                Log.wtf(TAG, "intent has no extras");
                return;
            }
            startResult = (ActivityResult) intent.getExtras().get(INTENT_EXTRA_MEDIA_PROJECTION_PERMISSION_RESULT);
        }

        if (startResult == null || startResult.getResultCode() != Activity.RESULT_OK || startResult.getData() == null) {
            Log.wtf(TAG, "media projection intent without successful media projection permission result: " + startResult);
            return;
        }

        if (mediaProjection != null)
            mediaProjection.stop();

        synchronized (lock) {
            Log.i(TAG, "starting media projection");

            // need this context to start the media projection
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                notificationService.addForegroundFlag(ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);

            MediaProjectionManager mediaProjectionManager = getSystemService(MediaProjectionManager.class);
            mediaProjection = mediaProjectionManager.getMediaProjection(startResult.getResultCode(), startResult.getData());
            if (mediaProjection == null) {
                // this probably won't happen
                Log.wtf(TAG, "failed to start media projection");
                return;
            }

            mediaProjection.registerCallback(mediaProjectionCallback, handler);

            if (mediaProjectionRequestCallback != null) {
                mediaProjectionRequestCallback.onAccepted(mediaProjection);
                mediaProjectionRequestCallback = null;
            }
        }
    }

    private void connectUsb() {
        Thread thread = new Thread(this::usbConnectionLoop, "Geargrinder USB connection loop");

        synchronized (lock) {
            if (connectionThread != null) {
                Log.e(TAG, "connection thread already active");
                return;
            }

            Log.i(TAG, "trying to connect over USB");
            connectionThread = thread;
        }

        connectionThread.start();

    }

    @Override
    public void onCarNameDiscovered(String carName) {
        notificationService.setCarName(carName);
    }

    private void usbConnectionLoop() {
        assert !Looper.getMainLooper().isCurrentThread();   // never run on main thread

        try {
            notificationService.setConnectionStatusText(R.string.looking_for_car);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                notificationService.addForegroundFlag(ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);

            UsbManager usbManager = getSystemService(UsbManager.class);

            UsbAccessory headunit = findUsbHeadunit(usbManager);
            if (headunit == null) {
                Log.e(TAG, "no headunit found");
                notificationService.postError(R.string.car_connection_error, R.string.error_no_usb_headunit);
                return;
            }

            if (!usbManager.hasPermission(headunit)) {
                Log.e(TAG, "no permission for usb accessory");
                notificationService.postError(R.string.car_connection_error, R.string.error_grant_usb_permission);
                return;
            }


            Log.i(TAG, "headunit found");
            notificationService.setConnectionStatusText(R.string.connecting_to_car);

            // TODO: open accessory more efficiently (see openAccessory()
            try (ParcelFileDescriptor pfd = usbManager.openAccessory(headunit);
                 FileInputStream is = new FileInputStream(pfd.getFileDescriptor());
                 FileOutputStream os = new FileOutputStream(pfd.getFileDescriptor())) {

                Log.d(TAG, "opened usb file descriptor [" + pfd.getFd() + "]: " + pfd);
                notificationService.setConnectionStatusText(R.string.connected_to_car);
                notificationService.clearError();

                Log.d(TAG, "starting services");
                TLSService tlsService = new TLSService(trustManagers, keyManagers);
                UsbTransferInterface usbTransferInterface = new UsbTransferInterface(pfd, is, os, AAFrame.MAX_LENGTH);
                MessageBroker messageBroker = new MessageBroker(usbTransferInterface, tlsService);
                ControlChannel controlChannel = new ControlChannel(this, messageBroker, tlsService, this, binder);
                try {
                    messageBroker.registerForChannel(AAConstants.CHANNEL_CONTROL, controlChannel);
                    messageBroker.loop();
                } finally {
                    controlChannel.destroy();
                    messageBroker.destroy();
                }

            } catch (IOException e) {
                Log.e(TAG, "IOException in car connection", e);
                notificationService.postError(R.string.car_connection_unexpected_error, R.string.error_car_io_usb_generic);
            }

        } finally {
            // die
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                notificationService.removeForegroundFlag(ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
            notificationService.setConnectionStatusText(R.string.looking_for_car);
            connectionThread = null;
        }
    }

    public interface MediaProjectionRequestCallback {

        void onAccepted(MediaProjection mediaProjection);

    }

    public class ServiceBinder extends Binder {

        public void requestMediaProjection(MediaProjectionRequestCallback callback) {
            synchronized (lock) {
                if (mediaProjection != null) {
                    callback.onAccepted(mediaProjection);
                    return;
                }

                Log.i(TAG, "requesting to start media projection");
                mediaProjectionRequestCallback = callback;
                startActivity(new Intent(ConnectionService.this, ConnectionRequestActivity.class)
                        .setAction(ConnectionRequestActivity.INTENT_ACTION_REQUEST_MEDIA_PROJECTION)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
        }

    }

}
