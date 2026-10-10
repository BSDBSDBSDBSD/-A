package com.missedcalls.screen;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.os.Build;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/**
 * חלונות שקופים קטנים, אחד לכל מתקשר, שצפים בראש המסך מעל המסך הראשי.
 * אין שירות ברקע ואין טיימרים: החלונות הם תצוגה בלבד ולא צורכים סוללה כשהם מוצגים.
 *
 * מקשים: חצים - מעבר בין חלונות וכפתורים | OK או מקש ירוק - חיוג חוזר | חזור - הסתרה.
 * מגע: נגיעה בחלון - חיוג חוזר | ✉ - הודעה | ✕ - הסרת המתקשר הזה.
 */
final class OverlayManager {
    private static final int MAX_CARDS = 5;

    private static Root root;
    private static WindowManager.LayoutParams params;
    private static BroadcastReceiver unlockReceiver;

    private OverlayManager() {}

    static boolean isShowing() { return root != null; }

    static void show(Context ctx, List<CallLogReader.Entry> list) {
        Context c = ctx.getApplicationContext();
        if (list.isEmpty()) { hide(c); return; }
        if (!Notifier.canOverlay(c)) return;

        WindowManager wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
        if (wm == null) return;
        if (root == null) {
            root = new Root(c);
            params = buildParams(c);
            try {
                wm.addView(root, params);
            } catch (Exception e) {
                root = null;
                return;
            }
            listenForUnlock(c);
        }
        fill(c, list);
    }

    /** טעינה מחדש מיומן השיחות, אם החלונות מוצגים. */
    static void refresh(Context c) {
        if (root != null) show(c, CallLogReader.load(c));
    }

    /** הסתרה בלבד: השיחות נשארות ויוצגו שוב בשיחה הבאה שלא תיענה. */
    static void hide(Context ctx) {
        Context c = ctx.getApplicationContext();
        if (unlockReceiver != null) {
            try { c.unregisterReceiver(unlockReceiver); } catch (Exception ignored) { }
            unlockReceiver = null;
        }
        if (root == null) return;
        WindowManager wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
        try { if (wm != null) wm.removeView(root); } catch (Exception ignored) { }
        root = null;
        params = null;
    }

    // ---------- חלון ----------

    @SuppressWarnings("deprecation")
    private static WindowManager.LayoutParams buildParams(Context c) {
        // עד אנדרואיד 7: סוג חלון שמופיע גם מעל מסך הנעילה.
        // מאנדרואיד 8: מעל כל האפליקציות, ומעל מסך הנעילה המערכת מסתירה אותו עד לפתיחה.
        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_SYSTEM_ERROR;
        int flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL      // נגיעות מחוץ לחלונות עוברות למסך הראשי
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED;
        if (Notifier.isLocked(c)) {
            // בזמן נעילה החלונות לא תופסים מקשים, כדי לא להפריע לפתיחת הנעילה במקשים
            flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        }
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                type, flags, PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP;
        return lp;
    }

    /** אחרי פתיחת הנעילה - מאפשר ניווט במקשים בין החלונות. */
    private static void listenForUnlock(Context c) {
        if (unlockReceiver != null) return;
        unlockReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                if (root == null || params == null) return;
                params.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
                WindowManager wm = (WindowManager) context.getApplicationContext()
                        .getSystemService(Context.WINDOW_SERVICE);
                try { if (wm != null) wm.updateViewLayout(root, params); } catch (Exception ignored) { }
                root.focusFirst();
            }
        };
        c.registerReceiver(unlockReceiver, new IntentFilter(Intent.ACTION_USER_PRESENT));
    }

    // ---------- תוכן ----------

    private static void fill(final Context c, List<CallLogReader.Entry> list) {
        root.removeAllViews();
        LayoutInflater inf = LayoutInflater.from(c);

        // שורת כותרת: מספר השיחות, "נקה הכל" ו"הסתר"
        View header = inf.inflate(R.layout.overlay_header, root, false);
        int total = CallLogReader.totalCalls(list);
        ((TextView) header.findViewById(R.id.ov_title)).setText(total == 1
                ? c.getString(R.string.one_missed)
                : c.getString(R.string.n_missed, total));
        header.findViewById(R.id.ov_clear).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Prefs.clearAll(c);
                Notifier.cancel(c);
                hide(c);
            }
        });
        header.findViewById(R.id.ov_hide).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { hide(c); }
        });
        root.addView(header);

        int shown = Math.min(list.size(), MAX_CARDS);
        for (int i = 0; i < shown; i++) {
            root.addView(card(c, inf, list.get(i)));
        }

        if (list.size() > MAX_CARDS) {
            TextView more = (TextView) inf.inflate(R.layout.overlay_more, root, false);
            more.setText(c.getString(R.string.overlay_more, list.size() - MAX_CARDS));
            more.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    hide(c);
                    c.startActivity(Notifier.screenIntent(c));
                }
            });
            root.addView(more);
        }
        root.focusFirst();
    }

    private static View card(final Context c, LayoutInflater inf, final CallLogReader.Entry e) {
        View card = inf.inflate(R.layout.overlay_card, root, false);
        TextView name = card.findViewById(R.id.ov_name);
        TextView details = card.findViewById(R.id.ov_details);
        TextView count = card.findViewById(R.id.ov_count);
        View sms = card.findViewById(R.id.ov_sms);
        View close = card.findViewById(R.id.ov_close);

        name.setText(MissedCallsActivity.displayName(c, e));
        String time = MissedCallsActivity.formatTime(c, e.lastTime);
        // שם איש הקשר, או המספר אם אין שם - בלי כפילות. מתחת רק השעה
        details.setText(time);
        if (e.count > 1) {
            count.setVisibility(View.VISIBLE);
            count.setText("×" + e.count);
        } else {
            count.setVisibility(View.GONE);
        }
        if (Actions.isPrivate(e)) sms.setVisibility(View.GONE);

        card.setTag(e);
        card.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Actions.call(c, e);
                refresh(c);
            }
        });
        sms.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                hide(c);
                Actions.sms(c, e);
            }
        });
        close.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Prefs.dismissIds(c, e.ids);
                refresh(c);
                if (root == null) Notifier.cancel(c);
            }
        });
        return card;
    }

    /** המיכל של כל החלונות. מטפל במקשים של מכשירי המקשים. */
    private static final class Root extends LinearLayout {
        Root(Context c) {
            super(c);
            setOrientation(VERTICAL);
            int pad = (int) (6 * c.getResources().getDisplayMetrics().density);
            setPadding(pad, pad, pad, pad);
        }

        void focusFirst() {
            post(new Runnable() {
                @Override public void run() {
                    for (int i = 0; i < getChildCount(); i++) {
                        View v = getChildAt(i);
                        if (v.getTag() instanceof CallLogReader.Entry) { v.requestFocus(); return; }
                    }
                }
            });
        }

        private CallLogReader.Entry focusedEntry() {
            View v = findFocus();
            while (v != null) {
                if (v.getTag() instanceof CallLogReader.Entry) return (CallLogReader.Entry) v.getTag();
                ViewParent p = v.getParent();
                v = p instanceof View ? (View) p : null;
            }
            return null;
        }

        @Override
        public boolean dispatchKeyEvent(KeyEvent event) {
            final Context c = getContext();
            int k = event.getKeyCode();
            boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
            switch (k) {
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                    return super.dispatchKeyEvent(event);
                case KeyEvent.KEYCODE_CALL: {   // המקש הירוק
                    if (down) {
                        final CallLogReader.Entry e = focusedEntry();
                        if (e != null) {
                            post(new Runnable() {
                                @Override public void run() { Actions.call(c, e); refresh(c); }
                            });
                        }
                    }
                    return true;
                }
                case KeyEvent.KEYCODE_BACK:
                    if (!down) post(new Runnable() { @Override public void run() { hide(c); } });
                    return true;
                default:
                    // כל מקש אחר (ספרות, תפריט, בית) - מסתיר את החלונות ומחזיר את השליטה למסך הראשי
                    if (down) post(new Runnable() { @Override public void run() { hide(c); } });
                    return false;
            }
        }
    }
}
