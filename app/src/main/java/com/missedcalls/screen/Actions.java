package com.missedcalls.screen;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;

/** פעולות על שיחה: חיוג חוזר ושליחת הודעה. משותף לחלונות השקופים ולמסך הרשימה. */
final class Actions {
    private Actions() {}

    static boolean isPrivate(CallLogReader.Entry e) {
        return TextUtils.isEmpty(e.number) || e.number.startsWith("-");
    }

    static boolean canCallDirect(Context c) {
        return Build.VERSION.SDK_INT < 23
                || c.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED;
    }

    /** חיוג חוזר. השיחה יורדת מהרשימה. */
    static void call(Context c, CallLogReader.Entry e) {
        if (isPrivate(e)) return;
        Intent i = new Intent(canCallDirect(c) ? Intent.ACTION_CALL : Intent.ACTION_DIAL,
                Uri.fromParts("tel", e.number, null));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Prefs.dismissIds(c, e.ids);
        try { c.startActivity(i); } catch (Exception ignored) { }
    }

    static void sms(Context c, CallLogReader.Entry e) {
        if (isPrivate(e)) return;
        Intent i = new Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", e.number, null));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { c.startActivity(i); } catch (Exception ignored) { }
    }
}
