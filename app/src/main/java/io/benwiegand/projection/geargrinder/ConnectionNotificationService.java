package io.benwiegand.projection.geargrinder;

import static android.app.Service.STOP_FOREGROUND_DETACH;
import static android.app.Service.STOP_FOREGROUND_REMOVE;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.annotation.StringRes;

public class ConnectionNotificationService {
    private static final String TAG = ConnectionNotificationService.class.getSimpleName();

    private static final String FOREGROUND_NOTIFICATION_CHANNEL = "connection";
    private static final String ERROR_NOTIFICATION_CHANNEL = "connection_error";

    private static final int FOREGROUND_NOTIFICATION_ID = 1;
    private static final int ERROR_NOTIFICATION_ID = 2;

    private final Service context;
    private final NotificationManager nm;

    private @StringRes int connectionStatusText = R.string.looking_for_car;
    private String carName = null;
    private int foregroundFlags = 0;

    public ConnectionNotificationService(Service context) {
        this.context = context;
        nm = context.getSystemService(NotificationManager.class);
    }

    public void destroy() {
        context.stopForeground(STOP_FOREGROUND_REMOVE);
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    public void addForegroundFlag(int flag) {
        if ((foregroundFlags & flag) != 0) return;
        foregroundFlags |= flag;
        updateForegroundNotification();
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    public void removeForegroundFlag(int flag) {
        if ((foregroundFlags & flag) == 0) return;
        foregroundFlags -= flag;
        updateForegroundNotification();
        if (foregroundFlags == 0)
            context.stopForeground(STOP_FOREGROUND_DETACH);
    }

    public void setCarName(String carName) {
        this.carName = carName;
        updateForegroundNotification();
    }

    public void setConnectionStatusText(int connectionStatusText) {
        this.connectionStatusText = connectionStatusText;
        updateForegroundNotification();
    }

    private void initForegroundNotificationChannel() {
        if (nm.getNotificationChannel(FOREGROUND_NOTIFICATION_CHANNEL) != null) return;
        nm.createNotificationChannel(new NotificationChannel(
                FOREGROUND_NOTIFICATION_CHANNEL,
                context.getString(R.string.connection_foreground_notification_channel_name),
                android.app.NotificationManager.IMPORTANCE_LOW
        ));
    }

    private void initErrorNotificationChannel() {
        if (nm.getNotificationChannel(ERROR_NOTIFICATION_CHANNEL) != null) return;
        nm.createNotificationChannel(new NotificationChannel(
                ERROR_NOTIFICATION_CHANNEL,
                context.getString(R.string.connection_error_notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
        ));
    }


    public void clearError() {
        nm.cancel(ERROR_NOTIFICATION_ID);
    }

    public void postError(@StringRes int title, @StringRes int content) {
        initErrorNotificationChannel();

        // TODO: error activity
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new Notification.Builder(context, ERROR_NOTIFICATION_CHANNEL)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentIntent(pendingIntent)
                .setContentTitle(context.getString(title))
                .setContentText(context.getString(content))
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .build();

        nm.notify(ERROR_NOTIFICATION_ID, notification);
    }

    private void updateForegroundNotification() {
        initForegroundNotificationChannel();

        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(context, FOREGROUND_NOTIFICATION_CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_map)
                .setOngoing(true)
                .setContentIntent(pendingIntent)
                .setContentTitle(context.getString(connectionStatusText))
                .setSubText(carName)
                .setVisibility(Notification.VISIBILITY_PUBLIC)  // can't see notification with media projection active without this
                .build();

        if (foregroundFlags == 0) {
            Log.w(TAG, "no foreground justification set");
            nm.notify(FOREGROUND_NOTIFICATION_ID, notification);
            return;
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.startForeground(FOREGROUND_NOTIFICATION_ID, notification, foregroundFlags);
            } else {
                context.startForeground(FOREGROUND_NOTIFICATION_ID, notification);
            }
        } catch (SecurityException e) {
            // android system doesn't think the justification is valid at this time. not much that can be done.
            Log.wtf(TAG, "unable to start foreground context", e);
        }
    }
}
