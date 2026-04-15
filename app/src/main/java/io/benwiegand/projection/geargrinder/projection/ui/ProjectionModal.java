package io.benwiegand.projection.geargrinder.projection.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.StringRes;

import io.benwiegand.projection.geargrinder.R;

public class ProjectionModal {

    private final ViewGroup parent;
    private final View rootView;

    public ProjectionModal(ViewGroup parent, boolean attachToParent) {
        assert parent instanceof FrameLayout;
        this.parent = parent;

        Context context = parent.getContext();
        LayoutInflater inflater = LayoutInflater.from(context);
        rootView = inflater.inflate(R.layout.layout_projection_modal, parent, false);

        if (attachToParent)
            parent.addView(rootView);
    }

    public View getRootView() {
        return rootView;
    }

    private void inflateText(TextView textView, @StringRes int text) {
        textView.setText(text);
        textView.setVisibility(View.VISIBLE);
    }

    private void inflateButton(Button button, @StringRes int text, View.OnClickListener onClick) {
        button.setText(text);
        button.setOnClickListener(v -> {
            close();
            onClick.onClick(v);
        });
        button.setVisibility(View.VISIBLE);
    }

    public ProjectionModal setTitle(@StringRes int title) {
        inflateText(rootView.findViewById(R.id.title_text), title);
        return this;
    }

    public ProjectionModal setMessage(@StringRes int body) {
        inflateText(rootView.findViewById(R.id.body_text), body);
        return this;
    }

    public ProjectionModal setPositiveButton(@StringRes int text, View.OnClickListener onClick) {
        inflateButton(rootView.findViewById(R.id.positive_button), text, onClick);
        return this;
    }

    public ProjectionModal setNeutralButton(@StringRes int text, View.OnClickListener onClick) {
        inflateButton(rootView.findViewById(R.id.neutral_button), text, onClick);
        return this;
    }

    public ProjectionModal setNegativeButton(@StringRes int text, View.OnClickListener onClick) {
        inflateButton(rootView.findViewById(R.id.negative_button), text, onClick);
        return this;
    }

    public void close() {
        parent.removeView(rootView);
    }

}
