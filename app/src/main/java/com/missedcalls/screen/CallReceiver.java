package com.missedcalls.screen;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.telephony.TelephonyManager;

/**
 * מקשיב לשינויי מצב השיחה.
 * צלצול (RINGING) שאחריו חזרה למנוחה (IDLE) בלי מענה (OFFHOOK) = אולי שיחה שלא נענתה.
 * ההחלטה הסופית נעשית מול יומן השיחות, כדי לא להציג שיחות חסומות או כאלה שנדחו ידנית.
 */
public class CallReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!TelephonyManager.ACTION_PHONE_STATE_CHANGED.equals(intent.getAction())) return;
        String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
        if (state == null) return;
        final Context app = context.getApplicationContext();

        if (TelephonyManager.EXTRA_STATE_RINGING.equals(state)) {
            Prefs.setRinging(app, true);
        } else if (TelephonyManager.EXTRA_STATE_OFFHOOK.equals(state)) {
            Prefs.setRinging(app, false);
        } else if (TelephonyManager.EXTRA_STATE_IDLE.equals(state)) {
            if (!Prefs.wasRinging(app)) return;   // גם מונע טיפול כפול כשהשידור מגיע פעמיים
            Prefs.setRinging(app, false);

            // יומן השיחות נכתב שנייה-שתיים אחרי סיום הצלצול, לכן מחכים ובודקים שוב אם צריך
            final PendingResult pending = goAsync();
            final Handler h = new Handler(Looper.getMainLooper());
            h.postDelayed(new Runnable() {
                int attempt = 0;
                @Override public void run() {
                    attempt++;
                    boolean shown = Notifier.showIfAny(app);
                    if (!shown && attempt < 3) {
                        h.postDelayed(this, 2000);
                    } else {
                        pending.finish();
                    }
                }
            }, 1500);
        }
    }
}
