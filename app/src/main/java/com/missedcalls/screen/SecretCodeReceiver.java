package com.missedcalls.screen;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** חיוג *#*#6477#*#* פותח את מסך ההגדרות גם כשהאייקון מוסתר. */
public class SecretCodeReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Intent i = new Intent(context, SetupActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(i);
    }
}
