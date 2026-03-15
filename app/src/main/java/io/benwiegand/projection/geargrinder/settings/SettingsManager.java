package io.benwiegand.projection.geargrinder.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.StringRes;

import java.util.List;

import io.benwiegand.projection.geargrinder.R;

public class SettingsManager {
    private static final String TAG = SettingsManager.class.getSimpleName();
    private static final String PREFERENCE_NAME = "io.benwiegand.projection.geargrinder_preferences";

    private final Context context;
    private final SharedPreferences prefs;

    public SettingsManager(Context context) {
        this.context = context;
        prefs = context.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE);
    }

    public OperationalMode getOperationalMode() {
        return OperationalMode.read(context, prefs);
    }

    public PrivilegeMode getPrivilegeMode() {
        return PrivilegeMode.read(context, prefs);
    }

    public boolean allowsStartProjectionWhenLocked() {
        return prefs.getBoolean(context.getString(R.string.key_start_projection_when_locked), false);
    }

    public int getProjectionResumeGracePeriod() {
        return castInt(R.string.key_projection_resume_grace_period, 30);
    }

    private int castInt(@StringRes int key, int defaultValue) {
        String stringValue = prefs.getString(context.getString(key), null);
        if (stringValue == null) return defaultValue;

        try {
            return Integer.parseInt(stringValue);
        } catch (NumberFormatException e) {
            Log.wtf(TAG, "failed to cast preference value to integer", e);
            assert false;
            return defaultValue;
        }
    }

    public static <T> T enumForPref(Context context, SharedPreferences prefs, int key, int defaultValue, List<Pair<Integer, T>> mapping) {
        String value = prefs.getString(
                context.getString(key),
                context.getString(defaultValue));

        T defaultMapping = null;
        for (Pair<Integer, T> entry : mapping) {
            if (entry.first == defaultValue) defaultMapping = entry.second;
            if (!context.getString(entry.first).equals(value)) continue;
            return entry.second;
        }

        if (defaultMapping == null)
            Log.wtf(TAG, "default value not present in mappings");

        Log.wtf(TAG, "unhandled value for pref " + context.getString(key) + ": " + value);
        assert false;
        return defaultMapping;
    }

}
