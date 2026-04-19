package io.benwiegand.projection.geargrinder.projection.ui.task;

import static io.benwiegand.projection.geargrinder.projection.ui.task.ProjectionTask.SPLIT_SCREEN_LAYOUT_PARAMS;

import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import io.benwiegand.projection.geargrinder.R;
import io.benwiegand.projection.geargrinder.projection.ui.BaseSplash;
import io.benwiegand.projection.geargrinder.projection.ui.VirtualActivity;
import io.benwiegand.projection.geargrinder.projection.ui.VirtualActivitySplash;
import io.benwiegand.projection.geargrinder.projection.ui.preset.ButtonPreset;

public class ProjectionTaskSplash extends BaseSplash {

    private final LinearLayout virtualActivitySplashLayout;

    private record VirtualActivityWithSplash(VirtualActivity virtualActivity, VirtualActivitySplash splash) { }

    private final List<VirtualActivityWithSplash> virtualActivitiesWithSplashes = new ArrayList<>();

    private Function<VirtualActivity, ButtonPreset[]> virtualActivityContextButtonGenerator = a -> new ButtonPreset[0];

    public ProjectionTaskSplash(View rootView) {
        super(rootView);
        virtualActivitySplashLayout = rootView.findViewById(R.id.projection_task_virtual_activity_splash_layout);
    }

    public void addVirtualActivity(VirtualActivity activity, int index) {
        VirtualActivitySplash splash = VirtualActivitySplash.inflate(virtualActivitySplashLayout, activity);
        splash.inflateButtons(virtualActivityContextButtonGenerator.apply(activity));
        splash.show(false);

        virtualActivitiesWithSplashes.add(index, new VirtualActivityWithSplash(activity, splash));
        virtualActivitySplashLayout.addView(splash.getRootView(), index, SPLIT_SCREEN_LAYOUT_PARAMS);
    }

    public void addVirtualActivity(VirtualActivity activity) {
        addVirtualActivity(activity, virtualActivitiesWithSplashes.size());
    }

    public void removeVirtualActivity(VirtualActivity activity) {
        for (VirtualActivityWithSplash e : virtualActivitiesWithSplashes) {
            if (e.virtualActivity() != activity) continue;
            virtualActivitySplashLayout.removeView(e.splash().getRootView());
            return;
        }
    }

    public void inflateButtons(ButtonPreset... presets) {
        ViewGroup taskContextButtonsLayout = getRootView().findViewById(R.id.projection_task_context_buttons);
        taskContextButtonsLayout.removeAllViews();

        ButtonPreset.inflate(taskContextButtonsLayout, R.layout.layout_virtual_activity_context_button,
                new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT), presets);
    }

    public void setVirtualActivityContextButtonGenerator(Function<VirtualActivity, ButtonPreset[]> virtualActivityContextButtonGenerator) {
        this.virtualActivityContextButtonGenerator = virtualActivityContextButtonGenerator;
        for (VirtualActivityWithSplash e : virtualActivitiesWithSplashes)
            e.splash().inflateButtons(virtualActivityContextButtonGenerator.apply(e.virtualActivity()));
    }
}
