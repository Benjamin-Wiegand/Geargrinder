package io.benwiegand.projection.geargrinder.util;

import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.util.Log;

import io.benwiegand.projection.geargrinder.R;

public class UsbUtil {
    private static final String TAG = UsbUtil.class.getSimpleName();

    private static final String TARGET_ACCESSORY_MODEL = "Android Auto";

    public static UsbAccessory findUsbHeadunit(UsbManager usbManager) {
        UsbAccessory[] accessories = usbManager.getAccessoryList();
        if (accessories == null) {
            Log.e(TAG, "no accessories");
            return null;
        }

        for (UsbAccessory usbAccessory : accessories){
            Log.d(TAG, "accessory: " + usbAccessory);
            if (!TARGET_ACCESSORY_MODEL.equals(usbAccessory.getModel())) continue;
            return usbAccessory;
        }

        Log.w(TAG, "no usb headunit found");
        return null;
    }
}
