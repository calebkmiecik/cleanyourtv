package com.caleb.adbonboot;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

/**
 * Switches Wireless debugging on (adb_wifi_enabled=1). The projector turns it off at every start.
 * Runs only once Wi-Fi is up, because Android switches wireless debugging back off without Wi-Fi;
 * if it still did not stick, the job retries with a growing wait.
 */
public class Wake extends JobService {
    static final String TAG = "AdbOnBoot";
    static final String KEY = "adb_wifi_enabled";
    private final Handler handler = new Handler(Looper.getMainLooper());

    static void schedule(Context c) {
        JobInfo job = new JobInfo.Builder(1, new ComponentName(c, Wake.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                .setBackoffCriteria(30_000, JobInfo.BACKOFF_POLICY_LINEAR)
                .build();
        c.getSystemService(JobScheduler.class).schedule(job);
        Log.i(TAG, "queued until Wi-Fi is up");
    }

    /** Sets adb_wifi_enabled=1. False when WRITE_SECURE_SETTINGS has not been granted. */
    static boolean turnOn(Context c) {
        try {
            Settings.Global.putInt(c.getContentResolver(), KEY, 1);
            return true;
        } catch (SecurityException e) {
            Log.e(TAG, "WRITE_SECURE_SETTINGS is not granted; run: pm grant " + c.getPackageName()
                    + " android.permission.WRITE_SECURE_SETTINGS", e);
            return false;
        }
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        ContentResolver cr = getContentResolver();
        if (!turnOn(this)) return false; // retrying cannot help until the grant
        handler.postDelayed(() -> {
            boolean on = Settings.Global.getInt(cr, KEY, 0) == 1;
            Log.i(TAG, on ? "wireless debugging is on" : "wireless debugging did not stick; retrying");
            jobFinished(params, !on);
        }, 5_000);
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        handler.removeCallbacksAndMessages(null);
        return true;
    }
}
