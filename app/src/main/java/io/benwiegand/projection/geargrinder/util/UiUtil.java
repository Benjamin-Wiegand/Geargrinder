package io.benwiegand.projection.geargrinder.util;

import android.content.Context;
import android.content.pm.PackageManager;
import android.database.DataSetObserver;
import android.graphics.Rect;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.TextView;

import androidx.annotation.LayoutRes;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.function.Consumer;
import java.util.function.Function;

import io.benwiegand.projection.geargrinder.R;
import io.benwiegand.projection.geargrinder.exception.UserFriendlyException;
import io.benwiegand.projection.geargrinder.pm.AppRecord;

public class UiUtil {

    public static AlertDialog.Builder errorDialog(Context context, UserFriendlyException e) {
        String title = e.getFriendlyTitle();
        if (title == null) title = context.getString(R.string.default_error_title);
        return new AlertDialog.Builder(context)
                .setTitle(title)
                .setMessage(e.getFriendlyMessage())
                .setNeutralButton(R.string.close_button, null);
    }

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

    public static float dpToPx(Context context, float dp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.getResources().getDisplayMetrics());
    }

    public static abstract class SingleTypeListAdapter<T> implements ListAdapter {

        private final List<T> list;
        private final Function<ViewGroup, View> viewInflater;
        private final Queue<DataSetObserver> observers = new LinkedList<>();

        public SingleTypeListAdapter(List<T> list, Function<ViewGroup, View> viewInflater) {
            this.list = list;
            this.viewInflater = viewInflater;
        }

        public SingleTypeListAdapter(List<T> list, LayoutInflater inflater, @LayoutRes int layout) {
            this(list, parent -> inflater.inflate(layout, parent, false));
        }

        private void callObservers(Consumer<DataSetObserver> consumer) {
            for (DataSetObserver observer : observers)
                consumer.accept(observer);
        }

        public void notifyListChanged() {
            callObservers(DataSetObserver::onChanged);
        }

        public void notifyListInvalidated() {
            callObservers(DataSetObserver::onInvalidated);
        }

        protected abstract void inflateItem(int index, T data, View view);

        @Override
        public boolean areAllItemsEnabled() {
            return true;
        }

        @Override
        public boolean isEnabled(int position) {
            return true;
        }

        @Override
        public int getCount() {
            return list.size();
        }

        @Override
        public T getItem(int position) {
            return list.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public int getItemViewType(int position) {
            return 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) convertView = viewInflater.apply(parent);
            inflateItem(position, list.get(position), convertView);
            return convertView;
        }

        @Override
        public int getViewTypeCount() {
            return 1;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        @Override
        public boolean isEmpty() {
            return list.isEmpty();
        }

        @Override
        public void registerDataSetObserver(DataSetObserver observer) {
            observers.add(observer);
        }

        @Override
        public void unregisterDataSetObserver(DataSetObserver observer) {
            observers.remove(observer);
        }
    }

    public static AlertDialog createAppRecordPickerDialog(Context context, @StringRes int title, List<AppRecord> appRecords, Consumer<AppRecord> onResult) {
        PackageManager pm = context.getPackageManager();
        return new AlertDialog.Builder(context)
                .setTitle(title)
                .setAdapter(new SingleTypeListAdapter<>(appRecords, LayoutInflater.from(context), R.layout.layout_app_picker_entry) {
                    @Override
                    protected void inflateItem(int index, AppRecord app, View view) {
                        TextView nameView = view.findViewById(R.id.app_name);
                        ImageView iconView = view.findViewById(R.id.app_icon);
                        nameView.setText(app.label(pm));
                        iconView.setImageDrawable(app.icon(pm));
                    }
                }, (d, i) -> onResult.accept(appRecords.get(i)))
                .setNegativeButton(R.string.cancel_button, null)
                .create();
    }
}
