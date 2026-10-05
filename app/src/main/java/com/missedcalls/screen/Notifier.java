package com.missedcalls.screen;

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

/** הקפצת המסך המרוכז, עם התראה כגיבוי. */
final class Notifier {
    private static final String CHANNEL = "missed_calls_popup";
    static final int NOTIF_ID = 6477;

    private Notifier() {}

    /** מציג את המסך אם יש שיחות שלא טופלו. מחזיר true אם נמצאו שיחות. */
    static boolean showIfAny(Context c) {
        List<CallLogReader.Entry> list = CallLogReader.load(c);
        if (list.isEmpty()) return false;
        int total = CallLogReader.totalCalls(list);

        wakeScreen(c);
        postNotification(c, total, list.get(0));

        // פתיחה ישירה של המסך. באנדרואיד 10+ זה דורש את הרשאת "הצגה מעל אפליקציות אחרות",
        // ובשיאומי גם "הצגת חלונות קופצים בזמן ריצה ברקע".
        if (Build.VERSION.SDK_INT < 29 || canOverlay(c)) {
            try {
                c.startActivity(screenIntent(c));
            } catch (Exception ignored) {
                // ההתראה (full-screen intent) תפתח את המסך במקום
            }
        }
        return true;
    }

    static boolean canOverlay(Context c) {
        return Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(c);
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

    @SuppressWarnings("deprecation")
    private static void wakeScreen(Context c) {
        try {
            PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
            if (pm == null) return;
            PowerManager.WakeLock wl = pm.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "missedcalls:wake");
            wl.acquire(5000);
        } catch (Exception ignored) {
        }
    }

    @SuppressWarnings("deprecation")
    private static void postNotification(Context c, int total, CallLogReader.Entry last) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL,
                    c.getString(R.string.channel_name), NotificationManager.IMPORTANCE_HIGH);
            ch.setSound(null, null);   // המכשיר כבר צלצל, אין צורך ברעש נוסף
            ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            nm.createNotificationChannel(ch);
            b = new Notification.Builder(c, CHANNEL);
        } else {
            b = new Notification.Builder(c);
            b.setPriority(Notification.PRIORITY_HIGH);
        }

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getActivity(c, 0, screenIntent(c), flags);

        String who = MissedCallsActivity.displayName(c, last);
        String title = total == 1
                ? c.getString(R.string.one_missed)
                : c.getString(R.string.n_missed, total);

        b.setSmallIcon(R.drawable.ic_stat_missed)
                .setContentTitle(title)
                .setContentText(who)
                .setContentIntent(pi)
                .setFullScreenIntent(pi, true)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true);
        if (Build.VERSION.SDK_INT >= 21) {
            b.setCategory(Notification.CATEGORY_CALL)
                    .setVisibility(Notification.VISIBILITY_PUBLIC);
        }
        try {
            nm.notify(NOTIF_ID, b.build());
        } catch (SecurityException ignored) {
            // אין הרשאת התראות - המסך עדיין ייפתח ישירות אם יש הרשאת הצגה מעל אפליקציות
        }
    }
}
