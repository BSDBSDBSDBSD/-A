package com.missedcalls.screen;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * מסך אחד שמרכז את כל השיחות שלא נענו.
 * מוצג גם מעל מסך הנעילה. בנוי לניווט במקשים:
 *   חצים - מעבר בין שורות | OK או מקש ירוק - חיוג חוזר | החזקה / מקש תפריט - אפשרויות
 */
public class MissedCallsActivity extends Activity {

    private final List<CallLogReader.Entry> items = new ArrayList<>();
    private Adapter adapter;
    private ListView list;
    private TextView title;
    private TextView empty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showOverLockScreen();
        setContentView(R.layout.activity_missed);

        title = findViewById(R.id.title);
        empty = findViewById(R.id.empty);
        list = findViewById(R.id.list);
        adapter = new Adapter();
        list.setAdapter(adapter);
        list.setEmptyView(empty);

        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                callBack(items.get(pos));
            }
        });
        list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override public boolean onItemLongClick(AdapterView<?> p, View v, int pos, long id) {
                showOptions(items.get(pos));
                return true;
            }
        });

        Button clear = findViewById(R.id.btn_clear);
        Button close = findViewById(R.id.btn_close);
        clear.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Prefs.clearAll(MissedCallsActivity.this);
                Notifier.cancel(MissedCallsActivity.this);
                finish();
            }
        });
        close.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        reload();   // שיחה חדשה בזמן שהמסך פתוח - מתווספת לאותה רשימה
    }

    @Override
    protected void onPause() {
        super.onPause();
        OverlayManager.refresh(this);   // שינויים כאן מתעדכנים גם בחלונות השקופים
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    private void reload() {
        items.clear();
        items.addAll(CallLogReader.load(this));
        adapter.notifyDataSetChanged();

        int total = CallLogReader.totalCalls(items);
        if (total == 0) {
            title.setText(R.string.no_missed);
            Notifier.cancel(this);
        } else if (total == 1) {
            title.setText(R.string.one_missed);
        } else {
            title.setText(getString(R.string.n_missed, total));
        }

        if (!items.isEmpty()) {
            list.requestFocus();
            if (list.getSelectedItemPosition() == AdapterView.INVALID_POSITION) list.setSelection(0);
        }
    }

    // ---------- מסך נעילה ----------

    /** המסך נפתח רק בלחיצה של המשתמש (מההתראה או מההגדרות), ולכן לא מדליק את המסך ולא משאיר אותו דולק. */
    @SuppressWarnings("deprecation")
    private void showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        }
    }

    private boolean isLocked() {
        KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        return km != null && km.isKeyguardLocked();
    }

    /** פתיחת מסך הנעילה (אם יש קוד - המשתמש יתבקש להקיש), ואז הרצת הפעולה. */
    @SuppressWarnings("deprecation")
    private void unlockThen(final Runnable action) {
        if (!isLocked()) { action.run(); return; }
        if (Build.VERSION.SDK_INT >= 26) {
            KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            km.requestDismissKeyguard(this, new KeyguardManager.KeyguardDismissCallback() {
                @Override public void onDismissSucceeded() { action.run(); }
            });
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
            action.run();
        }
    }

    // ---------- פעולות ----------

    private static boolean isPrivate(CallLogReader.Entry e) {
        return Actions.isPrivate(e);
    }

    private void callBack(CallLogReader.Entry e) {
        if (isPrivate(e)) return;
        boolean canCall = Build.VERSION.SDK_INT < 23
                || checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED;
        Intent i = new Intent(canCall ? Intent.ACTION_CALL : Intent.ACTION_DIAL,
                Uri.fromParts("tel", e.number, null));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Prefs.dismissIds(this, e.ids);   // חזרת אליו - יורד מהרשימה
        try {
            if (canCall) {
                startActivity(i);            // שיחה יוצאת עובדת גם כשהמכשיר נעול
            } else {
                final Intent fi = i;
                unlockThen(new Runnable() { @Override public void run() { startActivity(fi); } });
            }
        } catch (Exception ignored) {
        }
        afterAction();
    }

    private void sendSms(CallLogReader.Entry e) {
        if (isPrivate(e)) return;
        final Intent i = new Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", e.number, null));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        unlockThen(new Runnable() {
            @Override public void run() {
                try { startActivity(i); } catch (Exception ignored) { }
            }
        });
    }

    private void remove(CallLogReader.Entry e) {
        Prefs.dismissIds(this, e.ids);
        reload();
        afterAction();
    }

    /** אם הרשימה התרוקנה - אין סיבה להשאיר את המסך פתוח. */
    private void afterAction() {
        if (CallLogReader.load(this).isEmpty()) {
            Notifier.cancel(this);
            finish();
        }
    }

    private void showOptions(final CallLogReader.Entry e) {
        String[] opts = {
                getString(R.string.opt_call),
                getString(R.string.opt_sms),
                getString(R.string.opt_remove)
        };
        new AlertDialog.Builder(this)
                .setTitle(displayName(this, e))
                .setItems(opts, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int which) {
                        if (which == 0) callBack(e);
                        else if (which == 1) sendSms(e);
                        else remove(e);
                    }
                })
                .show();
    }

    private CallLogReader.Entry selected() {
        if (items.isEmpty()) return null;
        int pos = list.getSelectedItemPosition();
        if (pos == AdapterView.INVALID_POSITION || pos >= items.size()) pos = 0;
        return items.get(pos);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        CallLogReader.Entry e = selected();
        if (keyCode == KeyEvent.KEYCODE_CALL && e != null) {   // המקש הירוק
            callBack(e);
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_MENU && e != null) {   // מקש התפריט
            showOptions(e);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    // ---------- תצוגה ----------

    static String displayName(Context c, CallLogReader.Entry e) {
        if (!TextUtils.isEmpty(e.name)) return e.name;
        if (isPrivate(e)) return c.getString(R.string.private_number);
        return e.number;
    }

    static String formatTime(Context c, long t) {
        Calendar now = Calendar.getInstance();
        Calendar then = Calendar.getInstance();
        then.setTimeInMillis(t);
        String hm = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(t));
        if (now.get(Calendar.YEAR) == then.get(Calendar.YEAR)) {
            int diff = now.get(Calendar.DAY_OF_YEAR) - then.get(Calendar.DAY_OF_YEAR);
            if (diff == 0) return hm;
            if (diff == 1) return c.getString(R.string.yesterday) + " " + hm;
        }
        return new SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(new Date(t));
    }

    private class Adapter extends BaseAdapter {
        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int p) { return items.get(p); }
        @Override public long getItemId(int p) { return p; }

        @Override
        public View getView(int pos, View v, ViewGroup parent) {
            if (v == null) {
                v = LayoutInflater.from(MissedCallsActivity.this).inflate(R.layout.row_call, parent, false);
            }
            CallLogReader.Entry e = items.get(pos);
            TextView name = v.findViewById(R.id.name);
            TextView details = v.findViewById(R.id.details);
            TextView count = v.findViewById(R.id.count);

            name.setText(displayName(MissedCallsActivity.this, e));
            String time = formatTime(MissedCallsActivity.this, e.lastTime);
            // שם איש הקשר, או המספר אם אין שם - בלי כפילות. מתחת רק השעה
            details.setText(time);
            if (e.count > 1) {
                count.setVisibility(View.VISIBLE);
                count.setText("×" + e.count);
            } else {
                count.setVisibility(View.GONE);
            }
            return v;
        }
    }
}
