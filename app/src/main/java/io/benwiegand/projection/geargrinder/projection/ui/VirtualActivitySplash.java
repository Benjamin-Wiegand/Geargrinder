package io.benwiegand.projection.geargrinder.projection.ui;

import android.content.pm.PackageManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import io.benwiegand.projection.geargrinder.R;
import io.benwiegand.projection.geargrinder.pm.AppRecord;
import io.benwiegand.projection.geargrinder.projection.ui.preset.ButtonPreset;

public class VirtualActivitySplash extends BaseSplash {

    public VirtualActivitySplash(View rootView, VirtualActivity virtualActivity) {
        super(rootView);

        PackageManager pm = getContext().getPackageManager();
        AppRecord app = virtualActivity.getAppRecord();

        TextView titleView = rootView.findViewById(R.id.virtual_activity_title);
        titleView.setText(app.label(pm));

        ImageView iconView = rootView.findViewById(R.id.virtual_activity_icon);
        iconView.setImageDrawable(app.icon(pm));
    }

    public void inflateButtons(ButtonPreset... presets) {
        ViewGroup contextButtonsLayout = getRootView().findViewById(R.id.virtual_activity_context_buttons);
        contextButtonsLayout.removeAllViews();

        ButtonPreset.inflate(contextButtonsLayout, R.layout.layout_virtual_activity_context_button,
                new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT), presets);
    }

    public static VirtualActivitySplash inflate(LayoutInflater inflater, ViewGroup parent, VirtualActivity activity) {
        return new VirtualActivitySplash(inflater.inflate(R.layout.layout_virtual_activity_splash, parent, false), activity);
    }

    public static VirtualActivitySplash inflate(ViewGroup parent, VirtualActivity activity) {
        return inflate(LayoutInflater.from(parent.getContext()), parent, activity);
    }

}
