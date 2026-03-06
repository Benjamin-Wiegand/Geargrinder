package io.benwiegand.projection.geargrinder.projection.ui.indicator;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.DrawableWrapper;
import android.graphics.drawable.LayerDrawable;

import androidx.appcompat.content.res.AppCompatResources;

import io.benwiegand.projection.geargrinder.R;

public class BatteryIndicatorIcon extends DrawableWrapper {

    private final Drawable batteryLevels;
    private final Drawable chargingIndicator;

    public BatteryIndicatorIcon(Context context) {
        super(AppCompatResources.getDrawable(context, R.drawable.battery_indicator));
        assert getDrawable() != null;

        LayerDrawable root = (LayerDrawable) getDrawable();
        batteryLevels = root.findDrawableByLayerId(R.id.battery_levels);
        chargingIndicator = root.findDrawableByLayerId(R.id.charging_indicator);
    }

    public void update(int percent, boolean charging) {
        assert percent >= 0 && percent <= 100;
        percent = Math.clamp(percent, 0, 100);

        chargingIndicator.setAlpha(charging ? 255 : 0);
        batteryLevels.setLevel(percent);
    }
}
