package io.benwiegand.projection.geargrinder;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.media.projection.MediaProjectionConfig;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class ConnectionRequestActivity extends AppCompatActivity {
    private static final String TAG = ConnectionRequestActivity.class.getSimpleName();

    public static final String INTENT_ACTION_REQUEST_MEDIA_PROJECTION = "io.benwiegand.projection.geargrinder.REQUEST_MEDIA_PROJECTION";

    private static final String INTENT_ACTION_USB_PERMISSION_RESULT = "io.benwiegand.projection.geargrinder.USB_PERMISSION";


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(new View(this));
        onIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        onIntent(intent);
    }

    private void onIntent(Intent intent) {
        if (intent == null) {
            Log.e(TAG, "intent is null");
            finish();
            return;
        }

        String action = intent.getAction();
        Log.v(TAG, "Intent get: " + action);
        if (action == null) {
            finish();
            return;
        }


        UsbAccessory accessory = intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);

        switch (action) {
            case INTENT_ACTION_USB_PERMISSION_RESULT -> {
                if (!intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                    Log.e(TAG, "user denied permission for accessory");
                    break;
                }

                Log.i(TAG, "permission granted for accessory: " + accessory);
                connectToUsbHeadunit();
            }
            case UsbManager.ACTION_USB_ACCESSORY_ATTACHED -> {
                Log.v(TAG, "accessory attached");

                if (accessory == null) {
                    Log.e(TAG, "accessory is null");
                    break;
                }

                UsbManager usbManager = getSystemService(UsbManager.class);
                if (!usbManager.hasPermission(accessory)) {
                    requestUsbPermission(usbManager, accessory);
                    return; // not done yet
                }

                connectToUsbHeadunit();
            }
            case INTENT_ACTION_REQUEST_MEDIA_PROJECTION -> {
                Log.v(TAG, "request for media projection");
                requestMediaProjection();
                return; // not done yet
            }
        }

        finish();
    }

    private final ActivityResultLauncher<Intent> startMediaProjection = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() != Activity.RESULT_OK) {
                    Log.e(TAG, "media projection permission result not ok:" + result.getResultCode());
                    finish();
                    return;
                }

                startForegroundService(new Intent(this, ConnectionService.class)
                        .setAction(ConnectionService.INTENT_ACTION_START_MEDIA_PROJECTION)
                        .putExtra(ConnectionService.INTENT_EXTRA_MEDIA_PROJECTION_PERMISSION_RESULT, result));

                finish();
            }
    );

    private void connectToUsbHeadunit() {
        Log.i(TAG, "starting connection service");
        startService(new Intent(this, ConnectionService.class)
                .setAction(ConnectionService.INTENT_ACTION_CONNECT_USB));
    }

    private void requestUsbPermission(UsbManager usbManager, UsbAccessory accessory) {
        Log.v(TAG, "requesting permission for accessory: " + accessory);
        PendingIntent permissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(INTENT_ACTION_USB_PERMISSION_RESULT), PendingIntent.FLAG_IMMUTABLE);
        usbManager.requestPermission(accessory, permissionIntent);
    }

    private void requestMediaProjection() {
        MediaProjectionManager mediaProjectionManager = getSystemService(MediaProjectionManager.class);
        Intent intent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            intent = mediaProjectionManager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay());
        } else {
            intent = mediaProjectionManager.createScreenCaptureIntent();
        }
        startMediaProjection.launch(intent);
    }
}
