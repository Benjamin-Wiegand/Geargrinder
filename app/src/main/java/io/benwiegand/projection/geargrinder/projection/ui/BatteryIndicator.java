package io.benwiegand.projection.geargrinder.projection.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import io.benwiegand.projection.geargrinder.R;
import io.benwiegand.projection.geargrinder.projection.ui.indicator.BatteryIndicatorIcon;

public class BatteryIndicator {
    private static final String TAG = BatteryIndicator.class.getSimpleName();

    private final View rootView;
    private final Context context;

    private final TextView percentageTextView;
    private final BatteryIndicatorIcon batteryIndicatorIcon;

    private record BatteryState(int level, int scale, int status) {
        public boolean valid() {
            return level() >= 0 && scale() > 0;
        }

        public int percent() {
            if (!valid()) return -1;
            return level() * 100 / scale();
        }

        public boolean charging() {
            return status() == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status() == BatteryManager.BATTERY_STATUS_FULL;
        }

        public String statusString() {
            return switch (status()) {
                case BatteryManager.BATTERY_STATUS_FULL -> "FULL";
                case BatteryManager.BATTERY_STATUS_DISCHARGING -> "DISCHARGING";
                case BatteryManager.BATTERY_STATUS_CHARGING -> "CHARGING";
                case BatteryManager.BATTERY_STATUS_UNKNOWN -> "UNKNOWN";
                case BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "NOT_CHARGING";
                default -> "UNKNOWN_VALUE(" + status() + ")";
            };
        }

        @Override
        public String toString() {
            return "BatteryState{" +
                    "valid=" + valid() +
                    ", percent=" + percent() +
                    ", status=" + statusString() +
                    ", level=" + level() +
                    ", scale=" + scale() +
                    '}';
        }
    }

    private BatteryState batteryState = new BatteryState(-1, -1, BatteryManager.BATTERY_STATUS_UNKNOWN);

    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            BatteryState newState = new BatteryState(
                    intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1),
                    intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1),
                    intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
            );

            if (newState.equals(batteryState)) return;
            batteryState = newState;
            onBatteryStateUpdated();
        }
    };

    public BatteryIndicator(View rootView) {
        this.rootView = rootView;
        context = rootView.getContext();

        percentageTextView = rootView.findViewById(R.id.battery_percent_text);
        batteryIndicatorIcon = new BatteryIndicatorIcon(context);

        ImageView iconView = rootView.findViewById(R.id.battery_icon);
        iconView.setImageDrawable(batteryIndicatorIcon);

        setShowing(false);
        context.registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    }

    public void destroy() {
        context.unregisterReceiver(batteryReceiver);
    }

    private void setShowing(boolean showing) {
        rootView.setVisibility(showing ? View.VISIBLE : View.GONE);
    }

    private void onBatteryStateUpdated() {
        Log.d(TAG, "battery state updated: " + batteryState);

        setShowing(batteryState.valid());
        if (!batteryState.valid()) return;

        percentageTextView.setText(context.getString(R.string.battery_percent_format, batteryState.percent()));
        batteryIndicatorIcon.update(batteryState.percent(), batteryState.charging());
    }

}
