package com.caleb.adbonboot;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** After every start: queue the job that switches Wireless debugging on once Wi-Fi is up. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Wake.schedule(context);
    }
}
