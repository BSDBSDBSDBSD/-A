package com.missedcalls.screen;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * קריאת השיחות שלא נענו מיומן השיחות.
 * נלקחות רק שיחות מסוג MISSED - שיחות חסומות (BLOCKED) ושיחות שנדחו ידנית (REJECTED)
 * הן סוגים אחרים ביומן ולכן לא מופיעות.
 */
final class CallLogReader {

    /** שורה אחת במסך: מספר אחד, עם כל השיחות שלא נענו ממנו. */
    static final class Entry {
        String number;      // המספר כפי שהופיע ביומן
        String name;        // שם איש הקשר, או null
        int count;          // כמה שיחות שלא נענו מהמספר
        long lastTime;      // זמן השיחה האחרונה
        final Set<Long> ids = new HashSet<>();
    }

    private CallLogReader() {}

    static boolean canRead(Context c) {
        return Build.VERSION.SDK_INT < 23
                || c.checkSelfPermission(Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED;
    }

    static List<Entry> load(Context c) {
        List<Entry> out = new ArrayList<>();
        if (!canRead(c)) return out;

        long since = Prefs.clearedUntil(c);
        Set<String> dismissed = Prefs.dismissedIds(c);
        boolean respectSeen = Prefs.respectSeen(c);

        String sel = CallLog.Calls.TYPE + "=" + CallLog.Calls.MISSED_TYPE
                + " AND " + CallLog.Calls.DATE + ">" + since;
        String[] proj = {
                CallLog.Calls._ID,
                CallLog.Calls.NUMBER,
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.DATE,
                CallLog.Calls.NEW
        };

        // מקובץ לפי מספר, לפי סדר השיחה האחרונה
        Map<String, Entry> byNumber = new LinkedHashMap<>();
        Cursor cur = null;
        try {
            cur = c.getContentResolver().query(CallLog.Calls.CONTENT_URI, proj, sel, null,
                    CallLog.Calls.DATE + " DESC");
            if (cur == null) return out;
            while (cur.moveToNext()) {
                long id = cur.getLong(0);
                if (dismissed.contains(String.valueOf(id))) continue;
                if (respectSeen && !cur.isNull(4) && cur.getInt(4) == 0) continue;

                String number = cur.getString(1);
                if (number == null) number = "";
                String key = normalize(number);
                Entry e = byNumber.get(key);
                if (e == null) {
                    e = new Entry();
                    e.number = number;
                    e.name = cur.getString(2);
                    e.lastTime = cur.getLong(3);
                    byNumber.put(key, e);
                }
                e.count++;
                e.ids.add(id);
            }
        } catch (SecurityException ignored) {
            return out;
        } finally {
            if (cur != null) cur.close();
        }

        for (Entry e : byNumber.values()) {
            if (TextUtils.isEmpty(e.name) && !TextUtils.isEmpty(e.number)) {
                e.name = lookupContact(c, e.number);
            }
            out.add(e);
        }
        return out;
    }

    static int totalCalls(List<Entry> list) {
        int n = 0;
        for (Entry e : list) n += e.count;
        return n;
    }

    /** השוואת מספרים: 0501234567 ו-+972501234567 נחשבים לאותו מספר. */
    private static String normalize(String number) {
        String digits = number.replaceAll("[^0-9]", "");
        if (digits.length() > 9) digits = digits.substring(digits.length() - 9);
        return digits.isEmpty() ? number : digits;
    }

    private static String lookupContact(Context c, String number) {
        if (Build.VERSION.SDK_INT >= 23
                && c.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return null;
        }
        Cursor cur = null;
        try {
            Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number));
            cur = c.getContentResolver().query(uri,
                    new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME}, null, null, null);
            if (cur != null && cur.moveToFirst()) return cur.getString(0);
        } catch (Exception ignored) {
        } finally {
            if (cur != null) cur.close();
        }
        return null;
    }
}
