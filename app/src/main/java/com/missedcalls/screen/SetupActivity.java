package com.missedcalls.screen;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** מסך הגדרה: אישור הרשאות, בדיקה והסתרת האייקון. */
public class SetupActivity extends Activity {

    private static final String PERM_NOTIF = "android.permission.POST_NOTIFICATIONS";

    private TextView status;
    private Button hideBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup);
        status = findViewById(R.id.status);
        hideBtn = findViewById(R.id.btn_hide);

        findViewById(R.id.btn_perms).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { requestRuntimePermissions(); }
        });
        findViewById(R.id.btn_overlay).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openOverlaySettings(); }
        });
        findViewById(R.id.btn_xiaomi).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openXiaomiPermissions(); }
        });
        findViewById(R.id.btn_autostart).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openAutostart(); }
        });
        findViewById(R.id.btn_test).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(Notifier.screenIntent(SetupActivity.this));
            }
        });
        hideBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { toggleIcon(); }
        });

        CheckBox seen = findViewById(R.id.chk_seen);
        seen.setChecked(Prefs.respectSeen(this));
        seen.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean checked) {
                Prefs.setRespectSeen(SetupActivity.this, checked);
            }
        });

        Prefs.clearedUntil(this);   // קובע את נקודת ההתחלה: שיחות ישנות לא יוצגו
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    private String[] neededPermissions() {
        List<String> p = new ArrayList<>();
        p.add(Manifest.permission.READ_PHONE_STATE);
        p.add(Manifest.permission.READ_CALL_LOG);
        p.add(Manifest.permission.READ_CONTACTS);
        p.add(Manifest.permission.CALL_PHONE);
        if (Build.VERSION.SDK_INT >= 33) p.add(PERM_NOTIF);
        return p.toArray(new String[0]);
    }

    private boolean has(String perm) {
        return Build.VERSION.SDK_INT < 23 || checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED;
    }

    private void refreshStatus() {
        StringBuilder sb = new StringBuilder();
        line(sb, has(Manifest.permission.READ_PHONE_STATE), R.string.st_phone);
        line(sb, has(Manifest.permission.READ_CALL_LOG), R.string.st_calllog);
        line(sb, has(Manifest.permission.READ_CONTACTS), R.string.st_contacts);
        line(sb, has(Manifest.permission.CALL_PHONE), R.string.st_call);
        if (Build.VERSION.SDK_INT >= 33) line(sb, has(PERM_NOTIF), R.string.st_notif);
        line(sb, Notifier.canOverlay(this), R.string.st_overlay);
        status.setText(sb.toString().trim());
        hideBtn.setText(isIconVisible() ? R.string.btn_hide_icon : R.string.btn_show_icon);
    }

    private void line(StringBuilder sb, boolean ok, int label) {
        sb.append(ok ? "✓ " : "✗ ").append(getString(label)).append('\n');
    }

    private void requestRuntimePermissions() {
        if (Build.VERSION.SDK_INT >= 23) requestPermissions(neededPermissions(), 1);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        refreshStatus();
    }

    private void openOverlaySettings() {
        if (Build.VERSION.SDK_INT < 23) return;
        Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        if (!tryStart(i)) openAppDetails();
    }

    /** בשיאומי יש הרשאות נוספות: "הצגה על מסך נעילה" ו"הצגת חלונות קופצים ברקע". */
    private void openXiaomiPermissions() {
        Intent i = new Intent("miui.intent.action.APP_PERM_EDITOR");
        i.setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity");
        i.putExtra("extra_pkgname", getPackageName());
        if (tryStart(i)) return;
        i.setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.AppPermissionsEditorActivity");
        if (tryStart(i)) return;
        openAppDetails();
    }

    /** בלי הפעלה אוטומטית, מכשירי שיאומי עלולים לחסום את זיהוי השיחה. */
    private void openAutostart() {
        Intent i = new Intent();
        i.setComponent(new ComponentName("com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"));
        if (tryStart(i)) return;
        new AlertDialog.Builder(this)
                .setMessage(R.string.autostart_missing)
                .setPositiveButton(android.R.string.ok, null)
                .show();
        openAppDetails();
    }

    private void openAppDetails() {
        tryStart(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName())));
    }

    private boolean tryStart(Intent i) {
        try {
            startActivity(i);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ---------- הסתרת האייקון ----------

    private ComponentName launcher() {
        return new ComponentName(this, getPackageName() + ".LauncherAlias");
    }

    private boolean isIconVisible() {
        int s = getPackageManager().getComponentEnabledSetting(launcher());
        return s != PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
    }

    private void toggleIcon() {
        final boolean hide = isIconVisible();
        if (!hide) {
            setIcon(true);
            return;
        }
        new AlertDialog.Builder(this)
                .setMessage(R.string.hide_warning)
                .setPositiveButton(R.string.btn_hide_icon, new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) { setIcon(false); }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void setIcon(boolean visible) {
        getPackageManager().setComponentEnabledSetting(launcher(),
                visible ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
        refreshStatus();
    }
}
