package com.caleb.adbonboot;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

/**
 * Switches Wireless debugging on (adb_wifi_enabled=1). The projector turns it off at every start.
 * Runs only once Wi-Fi is up, because Android switches wireless debugging back off without Wi-Fi;
 * the job then keeps checking once a minute (see schedule).
 */
public class Wake extends JobService {
    static final String TAG = "AdbOnBoot";
    static final String KEY = "adb_wifi_enabled";
    private final Handler handler = new Handler(Looper.getMainLooper());

    static final String ACTION_WIFI = "com.caleb.adbonboot.WIFI";

    /**
     * Asks Android to send ACTION_WIFI to BootReceiver every time Wi-Fi connects: wireless debugging goes off with
     * the network (11-02: "Network disconnected. Disabling adbwifi.", the projector up 17 h, no restart), so a
     * start-up switch alone left it off. Lasts until the next restart; BootReceiver registers it again then.
     */
    static void watchWifi(Context c) {
        Intent i = new Intent(c, BootReceiver.class).setAction(ACTION_WIFI);
        int mutable = Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0;   // Android adds the network
        PendingIntent pi = PendingIntent.getBroadcast(c, 0, i, PendingIntent.FLAG_UPDATE_CURRENT | mutable);
        c.getSystemService(ConnectivityManager.class).registerNetworkCallback(new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(), pi);
        Log.i(TAG, "watching Wi-Fi");
    }

    static void schedule(Context c) {
        schedule(c, 0);
    }

    /**
     * The check runs once Wi-Fi is up, at least {@code delayMs} from now, and survives restarts. Each run queues the
     * next one a minute later: Android's Wi-Fi watch (watchWifi) went missing on this projector (11-02, the
     * PendingIntent request vanished and Wi-Fi's return woke nothing), so a minute's poll is the fallback.
     */
    static void schedule(Context c, long delayMs) {
        JobInfo job = new JobInfo.Builder(1, new ComponentName(c, Wake.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                .setMinimumLatency(delayMs)
                .setPersisted(true)
                .build();
        c.getSystemService(JobScheduler.class).schedule(job);
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
        if (Settings.Global.getInt(cr, KEY, 0) == 1) {   // already on: nothing to do, check again in a minute
            schedule(this, 60_000);
            return false;
        }
        if (!turnOn(this)) return false;   // retrying cannot help until the grant
        handler.postDelayed(() -> {
            boolean on = Settings.Global.getInt(cr, KEY, 0) == 1;
            Log.i(TAG, on ? "wireless debugging was off; switched it on" : "wireless debugging did not stick; again in 30 s");
            jobFinished(params, false);
            schedule(this, on ? 60_000 : 30_000);
        }, 5_000);
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        handler.removeCallbacksAndMessages(null);
        return true;
    }
}
