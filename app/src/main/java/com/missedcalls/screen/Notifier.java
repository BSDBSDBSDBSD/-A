package com.missedcalls.screen;

import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

import java.util.List;

/**
 * מציג את החלונות השקופים על המסך. התראה רגילה משמשת רק כגיבוי:
 * כשאין הרשאת "הצגה מעל אפליקציות", או כשהמכשיר נעול באנדרואיד 8+ (שם החלונות מופיעים אחרי פתיחת הנעילה).
 */
final class Notifier {
    private static final String CHANNEL = "missed_calls_popup";
    static final int NOTIF_ID = 6477;

    private Notifier() {}

    /** מציג את השיחות אם יש כאלה שלא טופלו. מחזיר true אם נמצאו שיחות. */
    static boolean showIfAny(Context c) {
        List<CallLogReader.Entry> list = CallLogReader.load(c);
        if (list.isEmpty()) return false;

        if (canOverlay(c)) {
            OverlayManager.show(c, list);
            boolean hiddenByLock = Build.VERSION.SDK_INT >= 26 && isLocked(c);
            if (hiddenByLock) postNotification(c, list); else cancel(c);
        } else {
            postNotification(c, list);
        }
        wakeScreenBriefly(c);
        return true;
    }

    static boolean canOverlay(Context c) {
        return Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(c);
    }

    static boolean isLocked(Context c) {
        KeyguardManager km = (KeyguardManager) c.getSystemService(Context.KEYGUARD_SERVICE);
        return km != null && km.inKeyguardRestrictedInputMode();
    }

    static Intent screenIntent(Context c) {
        Intent i = new Intent(c, MissedCallsActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        return i;
    }

    static void cancel(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(NOTIF_ID);
    }

    /**
     * מדליק את המסך ל-3 שניות בלבד אם הוא כבוי, כדי שיראו את השיחה.
     * אחרי זה המסך נכבה לפי זמן הכיבוי הרגיל של המכשיר.
     */
    @SuppressWarnings("deprecation")
    private static void wakeScreenBriefly(Context c) {
        try {
            PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
            if (pm == null || pm.isScreenOn()) return;
            PowerManager.WakeLock wl = pm.newWakeLock(
                    PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "missedcalls:wake");
            wl.setReferenceCounted(false);
            wl.acquire(3000);
        } catch (Exception ignored) {
        }
    }

    @SuppressWarnings("deprecation")
    private static void postNotification(Context c, List<CallLogReader.Entry> list) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        int total = CallLogReader.totalCalls(list);

        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL,
                    c.getString(R.string.channel_name), NotificationManager.IMPORTANCE_DEFAULT);
            ch.setSound(null, null);   // המכשיר כבר צלצל, אין צורך ברעש נוסף
            ch.enableVibration(false);
            ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            nm.createNotificationChannel(ch);
            b = new Notification.Builder(c, CHANNEL);
        } else {
            b = new Notification.Builder(c);
        }

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getActivity(c, 0, screenIntent(c), flags);

        String who = MissedCallsActivity.displayName(c, list.get(0));
        String title = total == 1
                ? c.getString(R.string.one_missed)
                : c.getString(R.string.n_missed, total);

        b.setSmallIcon(R.drawable.ic_stat_missed)
                .setContentTitle(title)
                .setContentText(who)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true);
        if (Build.VERSION.SDK_INT >= 21) {
            b.setCategory("missed_call")   // Notification.CATEGORY_MISSED_CALL
                    .setVisibility(Notification.VISIBILITY_PUBLIC);
        }
        try {
            nm.notify(NOTIF_ID, b.build());
        } catch (SecurityException ignored) {
        }
    }
}
