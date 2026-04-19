package io.benwiegand.projection.geargrinder.projection.ui;

import android.content.Context;
import android.view.View;

public abstract class BaseSplash {

    private static final long SPLASH_ANIMATION_DURATION = 300;

    private final View rootView;

    private boolean visible;

    public BaseSplash(View rootView) {
        this.rootView = rootView;
        visible = rootView.getVisibility() == View.VISIBLE;
    }

    public View getRootView() {
        return rootView;
    }

    public Context getContext() {
        return rootView.getContext();
    }

    public boolean isVisible() {
        return visible;
    }

    public void show(boolean animate) {
        if (visible) return;
        rootView.animate()
                .setStartDelay(0)
                .setDuration(animate ? SPLASH_ANIMATION_DURATION : 0)
                .alpha(1f)
                .withStartAction(() -> rootView.setVisibility(View.VISIBLE));
        visible = true;
    }

    public void show() {
        show(true);
    }

    public void hide(boolean animate) {
        if (!visible) return;
        rootView.animate()
                .setStartDelay(0)
                .setDuration(animate ? SPLASH_ANIMATION_DURATION : 0)
                .alpha(0f)
                .withEndAction(() -> rootView.setVisibility(View.GONE));
        visible = false;
    }

    public void hide() {
        hide(true);
    }

    public void toggle() {
        if (visible) hide();
        else show();
    }
}
