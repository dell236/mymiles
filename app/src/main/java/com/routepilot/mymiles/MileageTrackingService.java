package com.routepilot.mymiles;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.location.*;
import android.os.*;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MileageTrackingService extends Service implements LocationListener {
    static final String PREFS = "mymiles_prefs";
    static final String CHANNEL = "mymiles_tracking";
    SharedPreferences prefs;
    LocationManager locationManager;
    Location lastAccepted;
    boolean paused = false;

    @Override public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        locationManager = (LocationManager)getSystemService(LOCATION_SERVICE);
        createChannel();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : "START";
        if ("PAUSE".equals(action)) { paused = true; log("Paused."); return START_STICKY; }
        if ("RESUME".equals(action)) { paused = false; log("Resumed."); return START_STICKY; }
        if ("STOP".equals(action)) { stopTracking(); stopSelf(); return START_NOT_STICKY; }
        startForeground(10, notification("Tracking mileage"));
        startUpdates();
        return START_STICKY;
    }

    void startUpdates() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            log("No fine location permission.");
            return;
        }
        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 5, this);
            try {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000, 10, this);
            } catch(Exception ignored) {}
            prefs.edit().putBoolean("running", true).apply();
            log("GPS updates requested.");
        } catch(Exception e) { log("GPS start error: " + e.getMessage()); }
    }

    void stopTracking() {
        try { locationManager.removeUpdates(this); } catch(Exception ignored) {}
        prefs.edit().putBoolean("running", false).apply();
        log("Stopped.");
    }

    @Override public void onLocationChanged(Location loc) {
        if (loc == null) return;
        float acc = loc.hasAccuracy() ? loc.getAccuracy() : 9999;
        prefs.edit().putFloat("accuracy", acc).apply();

        if (paused) {
            reject("Paused point ignored.");
            return;
        }

        if (acc > 80) {
            reject("Rejected weak accuracy " + Math.round(acc) + "m.");
            return;
        }

        if (lastAccepted == null) {
            lastAccepted = loc;
            accept("Accepted first GPS point.");
            return;
        }

        float dist = lastAccepted.distanceTo(loc);
        long dtMs = Math.max(1000, loc.getTime() - lastAccepted.getTime());
        double mph = (dist / (dtMs / 1000.0)) * 2.23693629;

        if (loc.hasSpeed()) {
            mph = loc.getSpeed() * 2.23693629;
        }

        prefs.edit().putFloat("speedMph", (float)mph).apply();

        if (dist < 8) {
            reject("Rejected small move " + String.format(Locale.US, "%.1f", dist) + "m.");
            return;
        }

        if (mph > 105) {
            reject("Rejected GPS jump " + String.format(Locale.US, "%.1f", mph) + " mph.");
            return;
        }

        if (mph < 1.5 && dist < 25) {
            reject("Rejected parked drift.");
            return;
        }

        float total = prefs.getFloat("totalMeters", 0) + dist;
        int accepted = prefs.getInt("accepted", 0) + 1;
        lastAccepted = loc;
        prefs.edit()
                .putFloat("totalMeters", total)
                .putInt("accepted", accepted)
                .putFloat("speedMph", (float)mph)
                .apply();

        log("Accepted +" + String.format(Locale.US, "%.3f", dist * 0.000621371) + " mi • " + String.format(Locale.US, "%.1f", mph) + " mph.");
        sendBroadcast(new Intent(MainActivity.ACTION_UPDATE));
    }

    void accept(String msg) {
        prefs.edit().putInt("accepted", prefs.getInt("accepted", 0) + 1).apply();
        log(msg);
        sendBroadcast(new Intent(MainActivity.ACTION_UPDATE));
    }

    void reject(String msg) {
        prefs.edit().putInt("rejected", prefs.getInt("rejected", 0) + 1).apply();
        log(msg);
        sendBroadcast(new Intent(MainActivity.ACTION_UPDATE));
    }

    void log(String msg) {
        String old = prefs.getString("log", "");
        String line = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date()) + " — " + msg + "\\n";
        String combined = line + old;
        if (combined.length() > 4000) combined = combined.substring(0, 4000);
        prefs.edit().putString("log", combined).apply();
    }

    Notification notification(String text) {
        Intent i = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        return b.setContentTitle("MyMiles")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setOngoing(true)
                .setContentIntent(pi)
                .build();
    }

    void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Mileage Tracking", NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = getSystemService(NotificationManager.class);
            nm.createNotificationChannel(ch);
        }
    }

   @Override public IBinder onBind(Intent intent) { return null; }
}
