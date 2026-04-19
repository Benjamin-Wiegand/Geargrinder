package io.benwiegand.projection.geargrinder.projection.ui.preset;

import static io.benwiegand.projection.geargrinder.util.UiUtil.dpToPx;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.LayoutRes;
import androidx.annotation.StringRes;
import androidx.core.content.res.ResourcesCompat;

import java.util.function.Supplier;

public record ButtonPreset(@StringRes int textRes, String textRaw, @DrawableRes int drawableRes, View.OnClickListener onClickListener) {

    private static final int INVALID_RESOURCE = 0;

    private static final int BUTTON_DRAWABLE_SIZE_DP = 24;

    public ButtonPreset(@StringRes int text, View.OnClickListener onClickListener) {
        this(text, null, INVALID_RESOURCE, onClickListener);
    }

    public ButtonPreset(@StringRes int text, @DrawableRes int drawableRes, View.OnClickListener onClickListener) {
        this(text, null, drawableRes, onClickListener);
    }

    public ButtonPreset(String text, View.OnClickListener onClickListener) {
        this(INVALID_RESOURCE, text, INVALID_RESOURCE, onClickListener);
    }

    public CharSequence text(Context context) {
        if (textRes() == INVALID_RESOURCE) return textRaw;
        return context.getText(textRes());
    }

    public Drawable drawable(Context context) {
        if (drawableRes() == INVALID_RESOURCE) return null;
        return ResourcesCompat.getDrawable(context.getResources(), drawableRes(), context.getTheme());
    }

    public void apply(TextView button) {
        Context context = button.getContext();
        button.setText(text(context));

        Drawable drawable = drawable(context);
        if (drawable != null) {
            int size = (int) dpToPx(button.getContext(), BUTTON_DRAWABLE_SIZE_DP);
            drawable.setBounds(0, 0, size, size);
            drawable.setTintList(button.getTextColors());
            button.setCompoundDrawablesRelative(drawable, null, null, null);
        }

        button.setOnClickListener(onClickListener());
    }

    public static TextView[] inflate(ViewGroup parent, Supplier<TextView> buttonSupplier, ViewGroup.LayoutParams layoutParams, ButtonPreset... presets) {
        TextView[] buttons = new TextView[presets.length];

        int i = 0;
        for (ButtonPreset preset : presets) {
            TextView button = buttonSupplier.get();
            preset.apply(button);
            buttons[i++] = button;
            if (layoutParams != null) parent.addView(button, layoutParams);
        }

        return buttons;
    }

    public static TextView[] inflate(ViewGroup parent, @LayoutRes int buttonLayout, ViewGroup.LayoutParams layoutParams, ButtonPreset... presets) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        return inflate(parent, () -> (TextView) inflater.inflate(buttonLayout, parent, false), layoutParams, presets);
    }

    public static TextView[] inflate(ViewGroup parent, Supplier<TextView> buttonSupplier, ButtonPreset... presets) {
        return inflate(parent, buttonSupplier, null, presets);
    }

    public static TextView[] inflate(ViewGroup parent, @LayoutRes int buttonLayout, ButtonPreset... presets) {
        return inflate(parent, buttonLayout, null, presets);
    }

}
