package io.benwiegand.projection.geargrinder.util;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Rect;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import io.benwiegand.projection.geargrinder.R;

public class UiUtil {

    public static void getViewBoundsInDisplay(View view, Rect bounds) {
        int[] coords = new int[2];
        view.getLocationOnScreen(coords);
        bounds.set(
                coords[0], coords[1],
                coords[0] + view.getWidth(),
                coords[1] + view.getHeight()
        );
    }

    public static int getDisplayId(Context context) {
        WindowManager wm = context.getSystemService(WindowManager.class);
        return wm.getDefaultDisplay().getDisplayId();   // actually the current display, not default
    }

    public static AlertDialog createActivityPickerDialog(Context context, @StringRes int title, Consumer<ComponentName> onResult) {
        PackageManager pm = context.getPackageManager();
        List<ActivityInfo> activities = new ArrayList<>();

        Intent launcherIntent = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER);

        for (ResolveInfo info : pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)) {
            ActivityInfo activity = info.activityInfo;
            if (!activity.enabled || !activity.exported) continue;
            activities.add(activity);
        }

        LayoutInflater inflater = LayoutInflater.from(context);
        View rootView = inflater.inflate(R.layout.layout_app_picker, null);
        TextView titleView = rootView.findViewById(R.id.app_picker_title);
        RecyclerView recycler = rootView.findViewById(R.id.app_picker_recycler);

        AlertDialog alertDialog = new AlertDialog.Builder(context)
                .setView(rootView)
                .setNegativeButton(R.string.cancel_button, null)
                .create();

        titleView.setText(title);

        recycler.setAdapter(new RecyclerView.Adapter<>() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View view = inflater.inflate(R.layout.layout_app_picker_entry, parent, false);
                return new RecyclerView.ViewHolder(view) {};
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                ActivityInfo activity = activities.get(position);
                View view = holder.itemView;
                ComponentName componentName = new ComponentName(activity.applicationInfo.packageName, activity.name);
                TextView nameView = view.findViewById(R.id.app_name);
                ImageView iconView = view.findViewById(R.id.app_icon);

                nameView.setText(pm.getApplicationLabel(activity.applicationInfo));
                iconView.setImageDrawable(activity.loadIcon(pm));

                view.setOnClickListener(v -> {
                    alertDialog.dismiss();
                    onResult.accept(componentName);
                });
            }

            @Override
            public int getItemCount() {
                return activities.size();
            }
        });

        recycler.setLayoutManager(new LinearLayoutManager(context));

        return alertDialog;
    }
}
