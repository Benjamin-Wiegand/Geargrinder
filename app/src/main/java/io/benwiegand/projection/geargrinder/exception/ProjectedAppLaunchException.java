package io.benwiegand.projection.geargrinder.exception;

import android.content.Context;

import io.benwiegand.projection.geargrinder.R;

public class ProjectedAppLaunchException extends UserFriendlyException {

    public ProjectedAppLaunchException(Context c, int message, Throwable cause) {
        super(c, R.string.projected_app_launch_error_title, message, cause);
    }

    public ProjectedAppLaunchException(Context c, int message) {
        super(c, R.string.projected_app_launch_error_title, message);
    }

}
