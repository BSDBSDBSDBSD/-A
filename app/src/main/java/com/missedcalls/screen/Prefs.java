package com.missedcalls.screen;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/** שמירת מצב קטנה: מצב הצלצול האחרון, ושיחות שהמשתמש כבר ניקה. */
final class Prefs {
    private static final String FILE = "missed_calls";
    private static final String KEY_WAS_RINGING = "was_ringing";
    private static final String KEY_CLEARED_UNTIL = "cleared_until";
    private static final String KEY_DISMISSED_IDS = "dismissed_ids";
    private static final String KEY_RESPECT_SEEN = "respect_seen";

    private Prefs() {}

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    static boolean wasRinging(Context c) { return sp(c).getBoolean(KEY_WAS_RINGING, false); }

    static void setRinging(Context c, boolean v) {
        sp(c).edit().putBoolean(KEY_WAS_RINGING, v).commit();
    }

    /** כל שיחה שלא נענתה לפני הזמן הזה נחשבת כמטופלת. בהתקנה ראשונה - מעכשיו. */
    static long clearedUntil(Context c) {
        SharedPreferences p = sp(c);
        if (!p.contains(KEY_CLEARED_UNTIL)) {
            long now = System.currentTimeMillis();
            p.edit().putLong(KEY_CLEARED_UNTIL, now).commit();
            return now;
        }
        return p.getLong(KEY_CLEARED_UNTIL, 0);
    }

    /** "נקה הכל": מסמן את כל מה שהיה עד עכשיו כמטופל. */
    static void clearAll(Context c) {
        sp(c).edit()
                .putLong(KEY_CLEARED_UNTIL, System.currentTimeMillis())
                .remove(KEY_DISMISSED_IDS)
                .commit();
    }

    static Set<String> dismissedIds(Context c) {
        return new HashSet<>(sp(c).getStringSet(KEY_DISMISSED_IDS, new HashSet<String>()));
    }

    /** הסרת שיחות של מספר מסוים מהרשימה (למשל אחרי שחזרו אליו). */
    static void dismissIds(Context c, Set<Long> ids) {
        Set<String> set = dismissedIds(c);
        for (Long id : ids) set.add(String.valueOf(id));
        sp(c).edit().putStringSet(KEY_DISMISSED_IDS, set).commit();
    }

    /** אם השיחה כבר נצפתה ביומן השיחות של המכשיר - לא להציג אותה שוב. */
    static boolean respectSeen(Context c) { return sp(c).getBoolean(KEY_RESPECT_SEEN, true); }

    static void setRespectSeen(Context c, boolean v) {
        sp(c).edit().putBoolean(KEY_RESPECT_SEEN, v).commit();
    }
}
