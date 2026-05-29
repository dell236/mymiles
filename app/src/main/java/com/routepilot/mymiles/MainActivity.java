package com.routepilot.mymiles;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;
import org.json.*;

public class MainActivity extends Activity {
    static final String PREFS = "mymiles_prefs";
    static final String ACTION_UPDATE = "com.routepilot.mymiles.UPDATE";
    SharedPreferences prefs;
    TextView milesView, speedView, accuracyView, acceptedView, rejectedView, statusView, recordsView, logView;
    EditText startOdoInput, endOdoInput, notesInput;
    Spinner tripTypeSpinner;
    BroadcastReceiver receiver;

    int bg = Color.rgb(246,248,245);
    int card = Color.WHITE;
    int text = Color.rgb(20,47,56);
    int muted = Color.rgb(102,123,131);
    int teal = Color.rgb(32,123,137);
    int green = Color.rgb(90,159,117);
    int red = Color.rgb(193,95,95);

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        buildUi();
        render();

        receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) { render(); }
        };
        IntentFilter filter = new IntentFilter(ACTION_UPDATE);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(receiver, filter);
    }

    @Override public void onDestroy() {
        super.onDestroy();
        if (receiver != null) unregisterReceiver(receiver);
    }

    void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(28));
        root.setBackgroundColor(bg);
        scroll.addView(root);
        setContentView(scroll);

        TextView title = tv("MyMiles", 30, true, text);
        TextView sub = tv("Android live GPS mileage test", 14, false, muted);
        root.addView(title);
        root.addView(sub);

        LinearLayout odoCard = panel(Color.rgb(23,42,66));
        odoCard.addView(tv("GPS TRACKED MILES", 12, true, Color.rgb(183,202,213)));
        milesView = tv("000000.00 mi", 42, true, Color.WHITE);
        odoCard.addView(milesView);
        root.addView(odoCard);

        LinearLayout controls = panel(card);
        statusView = tv("Idle", 16, true, muted);
        controls.addView(statusView);

        startOdoInput = input("Start odometer optional");
        controls.addView(startOdoInput);

        tripTypeSpinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Business", "Personal", "Commute", "Needs Review"});
        tripTypeSpinner.setAdapter(adapter);
        controls.addView(tripTypeSpinner);

        LinearLayout r1 = row();
        Button start = btn("Start Tracking", teal, Color.WHITE);
        Button pause = btn("Pause", Color.rgb(222,244,246), teal);
        r1.addView(start);
        r1.addView(pause);
        controls.addView(r1);

        LinearLayout r2 = row();
        Button resume = btn("Resume", green, Color.WHITE);
        Button stop = btn("Stop & Save", Color.rgb(255,241,241), red);
        r2.addView(resume);
        r2.addView(stop);
        controls.addView(r2);

        root.addView(controls);

        LinearLayout stats = panel(card);
        stats.addView(tv("Live diagnostics", 20, true, text));
        LinearLayout s1 = row();
        speedView = metric("Speed", "0.0 mph");
        accuracyView = metric("Accuracy", "—");
        s1.addView(speedView);
        s1.addView(accuracyView);
        stats.addView(s1);
        LinearLayout s2 = row();
        acceptedView = metric("Accepted", "0");
        rejectedView = metric("Rejected", "0");
        s2.addView(acceptedView);
        s2.addView(rejectedView);
        stats.addView(s2);
        root.addView(stats);

        LinearLayout save = panel(card);
        save.addView(tv("Save details", 20, true, text));
        endOdoInput = input("End odometer optional");
        notesInput = input("Notes optional");
        save.addView(endOdoInput);
        save.addView(notesInput);

        LinearLayout r3 = row();
        Button saveNow = btn("Save Current", teal, Color.WHITE);
        Button export = btn("Export CSV", Color.rgb(222,244,246), teal);
        r3.addView(saveNow);
        r3.addView(export);
        save.addView(r3);
        root.addView(save);

        LinearLayout recPanel = panel(card);
        recPanel.addView(tv("Saved records", 20, true, text));
        recordsView = tv("No records yet.", 13, false, muted);
        recPanel.addView(recordsView);
        root.addView(recPanel);

        LinearLayout logs = panel(card);
        logs.addView(tv("Point log", 20, true, text));
        logView = tv("Waiting for GPS...", 11, false, muted);
        logs.addView(logView);
        root.addView(logs);

        start.setOnClickListener(v -> startTracking());
        pause.setOnClickListener(v -> sendService("PAUSE"));
        resume.setOnClickListener(v -> sendService("RESUME"));
        stop.setOnClickListener(v -> { sendService("STOP"); saveRecord(); });
        saveNow.setOnClickListener(v -> saveRecord());
        export.setOnClickListener(v -> exportCsv());
    }

    void startTracking() {
        if (!hasFineLocation()) {
            requestPermissions(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            }, 5);
            toast("Allow location, then press Start again.");
            return;
        }

        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 6);
        }

        if (Build.VERSION.SDK_INT >= 29 && checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            new AlertDialog.Builder(this)
                    .setTitle("Background location")
                    .setMessage("For locked-screen testing, open Android app settings and allow location all the time. The app can still start now, but background mileage may be limited until this is allowed.")
                    .setPositiveButton("Open Settings", (d,w) -> openAppSettings())
                    .setNegativeButton("Start Anyway", (d,w) -> actuallyStart())
                    .show();
        } else {
            actuallyStart();
        }
    }

    void actuallyStart() {
        prefs.edit()
                .putFloat("totalMeters", 0)
                .putInt("accepted", 0)
                .putInt("rejected", 0)
                .putFloat("speedMph", 0)
                .putFloat("accuracy", 0)
                .putString("startOdo", startOdoInput.getText().toString())
                .putString("tripType", tripTypeSpinner.getSelectedItem().toString())
                .putString("log", time() + " — Trip started.\\n")
                .putBoolean("running", true)
                .apply();
        Intent i = new Intent(this, MileageTrackingService.class);
        i.setAction("START");
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
        else startService(i);
        toast("Tracking started");
        render();
    }

    void sendService(String action) {
        Intent i = new Intent(this, MileageTrackingService.class);
        i.setAction(action);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
        else startService(i);
    }

    void saveRecord() {
        float totalMeters = prefs.getFloat("totalMeters", 0);
        double gpsMiles = totalMeters * 0.000621371;
        String startOdo = prefs.getString("startOdo", "");
        String endOdo = endOdoInput.getText().toString();
        String status = "GPS-estimated";
        double finalMiles = gpsMiles;

        try {
            if (!startOdo.isEmpty() && !endOdo.isEmpty()) {
                double s = Double.parseDouble(startOdo);
                double e = Double.parseDouble(endOdo);
                if (e >= s) {
                    finalMiles = e - s;
                    status = "Odometer-confirmed";
                }
            } else if (!endOdo.isEmpty()) {
                status = "GPS-estimated / end odometer saved";
            }
        } catch(Exception ignored) {}

        try {
            JSONArray arr = new JSONArray(prefs.getString("records", "[]"));
            JSONObject r = new JSONObject();
            r.put("savedAt", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()));
            r.put("miles", finalMiles);
            r.put("gpsMiles", gpsMiles);
            r.put("status", status);
            r.put("tripType", prefs.getString("tripType", "Business"));
            r.put("startOdo", startOdo);
            r.put("endOdo", endOdo);
            r.put("accepted", prefs.getInt("accepted", 0));
            r.put("rejected", prefs.getInt("rejected", 0));
            r.put("notes", notesInput.getText().toString());
            arr.put(r);
            prefs.edit().putString("records", arr.toString())
                    .putFloat("totalMeters", 0)
                    .putBoolean("running", false)
                    .apply();
            toast("Saved. Counter reset.");
            endOdoInput.setText("");
            notesInput.setText("");
            render();
        } catch(Exception e) {
            toast("Save failed: " + e.getMessage());
        }
    }

    void exportCsv() {
        try {
            JSONArray arr = new JSONArray(prefs.getString("records", "[]"));
            StringBuilder csv = new StringBuilder("savedAt,miles,gpsMiles,status,tripType,startOdo,endOdo,accepted,rejected,notes\\n");
            for (int i=0;i<arr.length();i++) {
                JSONObject r = arr.getJSONObject(i);
                csv.append(q(r.optString("savedAt"))).append(',')
                        .append(r.optDouble("miles")).append(',')
                        .append(r.optDouble("gpsMiles")).append(',')
                        .append(q(r.optString("status"))).append(',')
                        .append(q(r.optString("tripType"))).append(',')
                        .append(q(r.optString("startOdo"))).append(',')
                        .append(q(r.optString("endOdo"))).append(',')
                        .append(r.optInt("accepted")).append(',')
                        .append(r.optInt("rejected")).append(',')
                        .append(q(r.optString("notes"))).append('\\n');
            }
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/csv");
            send.putExtra(Intent.EXTRA_SUBJECT, "MyMiles mileage CSV");
            send.putExtra(Intent.EXTRA_TEXT, csv.toString());
            startActivity(Intent.createChooser(send, "Export CSV"));
        } catch(Exception e) { toast("Export failed"); }
    }

    String q(String s) { return "\"" + s.replace("\"", "\"\"") + "\""; }

    void render() {
        float meters = prefs.getFloat("totalMeters", 0);
        milesView.setText(String.format(Locale.US, "%09.2f mi", meters * 0.000621371));
        speedView.setText(String.format(Locale.US, "Speed\\n%.1f mph", prefs.getFloat("speedMph", 0)));
        float acc = prefs.getFloat("accuracy", 0);
        accuracyView.setText("Accuracy\\n" + (acc > 0 ? Math.round(acc) + " m" : "—"));
        acceptedView.setText("Accepted\\n" + prefs.getInt("accepted", 0));
        rejectedView.setText("Rejected\\n" + prefs.getInt("rejected", 0));
        statusView.setText(prefs.getBoolean("running", false) ? "Tracking active" : "Idle");
        logView.setText(prefs.getString("log", "Waiting for GPS..."));

        try {
            JSONArray arr = new JSONArray(prefs.getString("records", "[]"));
            if (arr.length() == 0) { recordsView.setText("No records yet."); return; }
            StringBuilder sb = new StringBuilder();
            for (int i=arr.length()-1;i>=0;i--) {
                JSONObject r = arr.getJSONObject(i);
                sb.append(r.optString("savedAt"))
                        .append("\\n")
                        .append(String.format(Locale.US, "%.2f mi • %s • %s", r.optDouble("miles"), r.optString("tripType"), r.optString("status")))
                        .append("\\nGPS: ").append(String.format(Locale.US, "%.2f", r.optDouble("gpsMiles")))
                        .append(" mi • accepted ").append(r.optInt("accepted"))
                        .append(" • rejected ").append(r.optInt("rejected"))
                        .append("\\n\\n");
            }
            recordsView.setText(sb.toString());
        } catch(Exception ignored) {}
    }

    boolean hasFineLocation() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    void openAppSettings() {
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()));
        startActivity(i);
    }

    TextView tv(String s, int sp, boolean bold, int color) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(sp);
        v.setTextColor(color);
        v.setPadding(0, dp(4), 0, dp(4));
        if (bold) v.setTypeface(null, 1);
        return v;
    }

    EditText input(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setSingleLine(false);
        e.setTextColor(text);
        e.setHintTextColor(muted);
        e.setBackgroundColor(Color.rgb(248,252,251));
        e.setPadding(dp(12), dp(10), dp(12), dp(10));
        e.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        return e;
    }

    Button btn(String s, int bgColor, int txtColor) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextColor(txtColor);
        b.setBackgroundColor(bgColor);
        b.setAllCaps(false);
        b.setPadding(dp(8), dp(8), dp(8), dp(8));
        b.setLayoutParams(new LinearLayout.LayoutParams(0, dp(56), 1));
        return b;
    }

    LinearLayout panel(int color) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(16), dp(16), dp(16), dp(16));
        l.setBackgroundColor(color);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, dp(12), 0, 0);
        l.setLayoutParams(p);
        return l;
    }

    LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER);
        r.setPadding(0, dp(8), 0, 0);
        return r;
    }

    TextView metric(String label, String value) {
        TextView v = tv(label + "\\n" + value, 16, true, text);
        v.setGravity(Gravity.CENTER);
        v.setBackgroundColor(Color.rgb(248,252,251));
        v.setLayoutParams(new LinearLayout.LayoutParams(0, dp(78), 1));
        return v;
    }

    int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }
    String time() { return new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date()); }
    void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }
}
