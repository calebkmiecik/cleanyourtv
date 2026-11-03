package com.caleb.adbonboot;

import android.app.Activity;
import android.os.Bundle;

/** Same as a boot, on demand: lets adb test the job without restarting the projector. */
public class RunNow extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Wake.watchWifi(this);
        Wake.schedule(this);
        finish();
    }
}
