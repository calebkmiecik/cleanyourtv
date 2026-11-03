package com.caleb.adbonboot;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/**
 * After every start: as soon as Wi-Fi is up, switch Wireless debugging on, right here in the
 * broadcast. A job alone is not enough: an app with no screen sits in the "never used" standby
 * bucket, where its jobs may not run (seen on the first restart test, 11-02).
 */
public class BootReceiver extends BroadcastReceiver {
    private static final long WAIT_MS = 50_000; // a background broadcast may take about 60 s

    @Override
    public void onReceive(Context context, Intent intent) {
        Context app = context.getApplicationContext();
        PendingResult result = goAsync();
        if (Wake.ACTION_WIFI.equals(intent.getAction())) {   // Wi-Fi (re)connected: debugging may have gone off with it
            new Handler(Looper.getMainLooper()).postDelayed(() -> {   // let the connection settle first
                if (android.provider.Settings.Global.getInt(app.getContentResolver(), Wake.KEY, 0) == 0 && Wake.turnOn(app)) {
                    Log.i(Wake.TAG, "Wi-Fi back; switched wireless debugging on");
                }
                result.finish();
            }, 3_000);
            return;
        }
        Wake.watchWifi(app);   // a start or an update of this app: watch Wi-Fi from now on
        ConnectivityManager cm = app.getSystemService(ConnectivityManager.class);
        Handler main = new Handler(Looper.getMainLooper());
        ConnectivityManager.NetworkCallback[] cb = new ConnectivityManager.NetworkCallback[1];
        boolean[] finished = {false};
        Runnable finish = () -> {
            if (finished[0]) return;
            finished[0] = true;
            cm.unregisterNetworkCallback(cb[0]);
            Wake.schedule(app); // second chance: re-checks and retries if it did not stick
            result.finish();
        };
        cb[0] = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                main.post(() -> {
                    if (finished[0]) return;
                    if (Wake.turnOn(app)) Log.i(Wake.TAG, "Wi-Fi is up; switched wireless debugging on");
                    finish.run();
                });
            }
        };
        main.postDelayed(() -> {
            if (!finished[0]) Log.w(Wake.TAG, "no Wi-Fi within " + WAIT_MS / 1000 + " s; leaving it to the job");
            finish.run();
        }, WAIT_MS);
        cm.registerNetworkCallback(new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(), cb[0]);
    }
}
