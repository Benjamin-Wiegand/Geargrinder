package io.benwiegand.projection.geargrinder;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
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

import rikka.shizuku.Shizuku;

public class ConnectionRequestActivity extends AppCompatActivity {
    private static final String TAG = ConnectionRequestActivity.class.getSimpleName();

    public static final String INTENT_ACTION_REQUEST_MEDIA_PROJECTION = "io.benwiegand.projection.geargrinder.REQUEST_MEDIA_PROJECTION";
    public static final String INTENT_ACTION_REQUEST_SHIZUKU = "io.benwiegand.projection.geargrinder.REQUEST_SHIZUKU";

    private static final String INTENT_ACTION_USB_PERMISSION_RESULT = "io.benwiegand.projection.geargrinder.USB_PERMISSION";

    private static final String SHIZUKU_PACKAGE_NAME = "moe.shizuku.privileged.api";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(new View(this));

        Shizuku.addRequestPermissionResultListener(this::onShizukuPermissionResult);

        onIntent(getIntent());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Shizuku.removeRequestPermissionResultListener(this::onShizukuPermissionResult);
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
            case INTENT_ACTION_REQUEST_SHIZUKU -> {
                if (Shizuku.isPreV11()) {
                    Log.e(TAG, "shizuku app too old");
                    new AlertDialog.Builder(this)
                            .setTitle(R.string.shizuku_permission_request)
                            .setMessage(R.string.shizuku_too_old)
                            .setPositiveButton(R.string.close_button, (d, i) -> finish())
                            .setCancelable(false)
                            .show();
                    return;
                }

                if (Shizuku.getBinder() == null) {
                    Log.e(TAG, "shizuku binder is null. is shizuku running?");
                    AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this)
                            .setTitle(R.string.shizuku_permission_request)
                            .setNegativeButton(R.string.close_button, (d, i) -> finish())
                            .setPositiveButton(R.string.settings_button, (d, i) -> {
                                startActivity(new Intent(this, SettingsActivity.class));
                                finish();
                            })
                            .setCancelable(false);

                    Intent shizukuLaunchIntent = getPackageManager().getLaunchIntentForPackage(SHIZUKU_PACKAGE_NAME);
                    if (shizukuLaunchIntent != null) dialogBuilder
                            .setMessage(R.string.shizuku_not_running)
                            .setNeutralButton(R.string.launch_shizuku_button, (d, i) -> {
                                startActivity(shizukuLaunchIntent);
                                finish();
                            });
                    else dialogBuilder
                            .setMessage(R.string.shizuku_not_installed);

                    dialogBuilder.show();
                    return;
                }

                if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, "shizuku already granted: uid = " + Shizuku.getUid());
                    finish();
                } else if (Shizuku.shouldShowRequestPermissionRationale()) {
                    new AlertDialog.Builder(this)
                            .setTitle(R.string.shizuku_permission_request)
                            .setMessage(R.string.shizuku_permission_rationale)
                            .setPositiveButton(R.string.grant_permission_button, (d, i) ->
                                    Shizuku.requestPermission(69))
                            .setNegativeButton(R.string.not_now_button, (d, i) -> finish())
                            .setCancelable(false)
                            .show();
                } else {
                    Shizuku.requestPermission(69);
                }
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

    private void onShizukuPermissionResult(int requestCode, int result) {
        if (result != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "shizuku permission denied");
            finish();
            return;
        }

        Log.i(TAG, "shizuku granted: uid = " + Shizuku.getUid());

        finish();
    }


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
