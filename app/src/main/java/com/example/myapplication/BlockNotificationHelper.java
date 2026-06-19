package com.example.myapplication;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

/**
 * Rate-limited notifications when Smartlock ejects a forbidden app or locks the device as fallback.
 */
public final class BlockNotificationHelper {

    private static final String CHANNEL_ID = "smartlock_blocked_apps";
    private static final int BLOCK_NOTIFICATION_ID = 1001;
    private static final int FALLBACK_LOCK_NOTIFICATION_ID = 1002;
    private static final int TEST_NOTIFICATION_ID = 1003;
    /** At most one notification per minute per type — avoids spam during the 500 ms timer loop. */
    private static final long COOLDOWN_MS = 60_000L;

    private BlockNotificationHelper() {
    }

    public static void notifyAppBlockedIfDue(
            @NonNull Context context,
            @NonNull String packageName,
            int period) {
        Context appContext = context.getApplicationContext();
        Preferences preferences = new Preferences(appContext);
        String appLabel = resolveAppLabel(appContext, packageName);
        String periodLabel = periodLabel(appContext, period);
        String text = appContext.getString(R.string.block_notification_text, appLabel, packageName, periodLabel);
        notifyIfDue(
                appContext,
                preferences.getLastBlockNotificationMillis(),
                preferences::setLastBlockNotificationMillis,
                BLOCK_NOTIFICATION_ID,
                appContext.getString(R.string.block_notification_title),
                text);
    }

    public static void notifyFallbackLockIfDue(
            @NonNull Context context,
            @NonNull String packageName,
            int period) {
        Context appContext = context.getApplicationContext();
        Preferences preferences = new Preferences(appContext);
        String appLabel = resolveAppLabel(appContext, packageName);
        String periodLabel = periodLabel(appContext, period);
        String text = appContext.getString(
                R.string.fallback_lock_notification_text, appLabel, packageName, periodLabel);
        notifyIfDue(
                appContext,
                preferences.getLastFallbackLockNotificationMillis(),
                preferences::setLastFallbackLockNotificationMillis,
                FALLBACK_LOCK_NOTIFICATION_ID,
                appContext.getString(R.string.fallback_lock_notification_title),
                text);
    }

    /** Debug: always shows immediately, bypasses the block/fallback cooldown. */
    public static void showTestNotification(@NonNull Context context) {
        Context appContext = context.getApplicationContext();
        ensureChannel(appContext);
        String title = appContext.getString(R.string.test_notification_title);
        String text = appContext.getString(R.string.test_notification_text);
        postNotification(appContext, TEST_NOTIFICATION_ID, title, text);
    }

    private interface MillisWriter {
        void write(long millis);
    }

    private static void notifyIfDue(
            @NonNull Context appContext,
            long lastNotifiedMillis,
            @NonNull MillisWriter millisWriter,
            int notificationId,
            @NonNull String title,
            @NonNull String text) {
        long now = System.currentTimeMillis();
        if (now - lastNotifiedMillis < COOLDOWN_MS) {
            return;
        }

        ensureChannel(appContext);

        postNotification(appContext, notificationId, title, text);
        millisWriter.write(now);
    }

    private static void postNotification(
            @NonNull Context appContext,
            int notificationId,
            @NonNull String title,
            @NonNull String text) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(appContext, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);

        NotificationManager nm = (NotificationManager) appContext.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(notificationId, builder.build());
        }
    }

    private static void ensureChannel(@NonNull Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null || nm.getNotificationChannel(CHANNEL_ID) != null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.block_notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription(context.getString(R.string.block_notification_channel_description));
        nm.createNotificationChannel(channel);
    }

    @NonNull
    private static String resolveAppLabel(@NonNull Context context, @NonNull String packageName) {
        try {
            PackageManager pm = context.getPackageManager();
            ApplicationInfo info = pm.getApplicationInfo(packageName, 0);
            return pm.getApplicationLabel(info).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return packageName;
        }
    }

    @NonNull
    private static String periodLabel(@NonNull Context context, int period) {
        if (period == Kernel.CRITICAL_PERIOD) {
            return context.getString(R.string.block_notification_period_critical);
        }
        if (period == Kernel.DANGER_PERIOD) {
            return context.getString(R.string.block_notification_period_danger);
        }
        return context.getString(R.string.block_notification_period_other);
    }
}
