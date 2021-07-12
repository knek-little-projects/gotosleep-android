package com.example.myapplication;

import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import javax.net.ssl.HttpsURLConnection;

public class Kernel {

    static private final Boolean DEBUG = false;
    static private final Boolean DEBUG_TERC_AVAILABLE_ACTIVITY = false;
    static private final int DEBUG_PERIOD = 0;

    private Context context;
    private Preferences preferences;

    public Kernel(@NonNull Context context) {
        this.context = context;
        this.preferences = new Preferences(context);
    }

    public boolean runAnotherHomeLauncher() {
        String homeLauncher = preferences.getHomeLauncher();

        if (homeLauncher == null) {
            return false;
        }

        if (context.getPackageName().equals(homeLauncher)) {
            showAppSelector();
            return true;
        }

        if (preferences.getDangerProcessesSet().contains(homeLauncher)) {
            return false;
        }

        return new HomeLauncher(context).runHomeLauncher(homeLauncher);
    }

    public void showAppSelector() {
        context.startActivity(new Intent(context, DangerZoneActivity.class));
    }

    public Preferences getPreferences() {
        return preferences;
    }

    //
//    private String httpRequestString(URL url) {
//        try {
//            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
//            try {
//                InputStream inputStream = new BufferedInputStream(urlConnection.getInputStream());
//                return inputStream.toString();
//            } finally {
//                urlConnection.disconnect();
//            }
//        } catch (java.io.IOException e) {
//        }
//        return null;
//    }
//
//    private String httpsRequestString(URL url) {
//        try {
//            HttpsURLConnection urlConnection = (HttpsURLConnection) url.openConnection();
//            try {
//                InputStream inputStream = new BufferedInputStream(urlConnection.getInputStream());
//                return inputStream.toString();
//            } finally {
//                urlConnection.disconnect();
//            }
//        } catch (java.io.IOException e) {
//        }
//        return null;
//    }
//
//    private String requestString(String url) {
//        if (url.startsWith("http:")) {
//            return httpRequestString(new URL(url));
//        } else {
//            return httpsRequestString(new URL(url));
//        }
//    }
    private void updateTercActivityAvailable() {
        updateTercActivityAvailable(5);
    }

    private void updateTercActivityAvailable(final int retries) {
        String url = preferences.getTERCActivityURL();
        Log.d("updateTercActivityAvailable", "Request " + url);
        RequestQueue requestQueue = Volley.newRequestQueue(context);
        StringRequest stringRequest = new StringRequest(
                Request.Method.GET,
                url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        Log.d("updateTercActivityAvailable", response);
                        preferences.setTercActivityAllowed(response.equals("true"));
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        if (retries > 0) {
                            updateTercActivityAvailable(retries - 1);
                        } else {
                            Log.e("updateTercActivityAvailable", error.toString());
                            preferences.setTercActivityAllowed(false);
                        }
                    }
                }
        );
        stringRequest.setRetryPolicy(new DefaultRetryPolicy(
                5000,
                5,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));

        requestQueue.add(stringRequest);
    }

    public boolean getAndUpdateTercAvailableActivity() {
        if (DEBUG) {
            return DEBUG_TERC_AVAILABLE_ACTIVITY;
        }

        long now = System.currentTimeMillis();
        if (now - preferences.getLastTERCRequestTime() > 10000) {  // TODO
            preferences.setLastTercRequestTime(now);
            updateTercActivityAvailable();
        }

        return preferences.isTERCActivityAllowed();
    }

    static public int SAFE_PERIOD = 0;
    static public int DANGER_PERIOD = 1;
    static public int CRITICAL_PERIOD = 2;

    public int getPeriod() {
        if (DEBUG) {
            return DEBUG_PERIOD;
        }

        String now = getNow();

        if (!preferences.isSmartLockEnabled()) {
            return SAFE_PERIOD;
        }

        if (isTimeSeq(preferences.getSafeTime(), now, preferences.getDangerTime())) {
            return SAFE_PERIOD;
        }

        if (preferences.isTercUse()) {
            Log.d("kernel", "Using TERC");
            if (isTimeSeq(preferences.getCriticalTime(), now, preferences.getSafeTime())) {
                Log.d("kernel", "Critical time period: returning crit");
                return CRITICAL_PERIOD;
            } else {
                if (getAndUpdateTercAvailableActivity()) {
                    Log.d("kernel", "Relax is available: returning danger");
                    return DANGER_PERIOD;
                } else {
                    Log.d("kernel", "Relax is forbidden: returning crit");
                    return CRITICAL_PERIOD;
                }
            }
        } else {
            Log.d("kernel", "No TERC");

            if (isTimeSeq(preferences.getDangerTime(), now, preferences.getCriticalTime())) {
                return DANGER_PERIOD;
            }
            if (isTimeSeq(preferences.getCriticalTime(), now, preferences.getSafeTime())) {
                return CRITICAL_PERIOD;
            }
        }

        return SAFE_PERIOD;
    }

    public boolean isNowSafe() {
        return getPeriod() == SAFE_PERIOD;
    }

    public boolean isNowDanger() {
        return getPeriod() == DANGER_PERIOD;
    }

    public boolean isNowCritical() {
        return getPeriod() == CRITICAL_PERIOD;
    }

    static private boolean isTimeSeq(String a, String b, String c) {
        if (a.compareTo(c) <= 0) {
            return a.compareTo(b) <= 0 && b.compareTo(c) < 0;
        } else {
            return a.compareTo(b) <= 0 || b.compareTo(c) < 0;
        }
    }

    public void log(String text) {
        Log.i("MyUtils", text);
        File file = preferences.getLogFile();

        text = "[" + getNow() + "] " + text + "\n";

        try {
            FileOutputStream fileOutputStream = new FileOutputStream(file, true);
            fileOutputStream.write(text.getBytes());
            fileOutputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public long getNextCriticalTimeInMillis(Calendar now) {
        return getAfter(now, preferences.getCriticalTime()).getTimeInMillis();
    }

    static private Calendar getAfter(Calendar now, int hours, int minutes) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, hours);
        cal.set(Calendar.MINUTE, minutes);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        if (!cal.after(now)) {
            cal.add(Calendar.DAY_OF_MONTH, 1);
        }

        return cal;
    }

    static private Calendar getAfter(Calendar now, String hhmm) {
        String[] a = hhmm.split(":");
        int h = Integer.parseInt(a[0]);
        int m = Integer.parseInt(a[1]);
        return getAfter(now, h, m);
    }

    public String getNow() {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.US);
        Date currentTime = Calendar.getInstance().getTime();
        sdf.setTimeZone(TimeZone.getTimeZone(preferences.getTimeZone()));
        return sdf.format(currentTime);
    }

    public boolean isForbiddenAppRunning() {
        UsageStatsManager mUsageStatsManager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        long time = System.currentTimeMillis();
        long millisec = 60000;
        List<UsageStats> stats = mUsageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, time - millisec, time);

        stats.sort(new Comparator<UsageStats>() {
            @Override
            public int compare(UsageStats a, UsageStats b) {
                return -Long.compare(a.getLastTimeUsed(), b.getLastTimeUsed());
            }
        });

        if (stats.isEmpty()) {
            Log.d("Timer", "Empty usage stats");
        } else {
            String pkgName = stats.get(0).getPackageName();
            if (pkgName != null && !pkgName.equals(context.getPackageName()) && !StaticProcessList.fromPreferences(this, preferences).isPackageAllowed(pkgName)) {
                Log.w("Timer", "Last is " + pkgName);
                Log.w("Timer", "BRINGING TO FRONT");
                return true;
            } else {
                Log.d("Timer", "Last is " + pkgName);
            }
        }

        return false;
    }

    public ArrayList<String> getPackageNameList() {
        final PackageManager pm = context.getPackageManager();
        final ArrayList<String> apps = new ArrayList<>();
        for (ApplicationInfo applicationInfo : pm.getInstalledApplications(0)) {
            apps.add(applicationInfo.packageName);
        }
        return apps;
    }

    public void smartLock(String caller) {
        Log.d("kernel", "Smartlock: call from " + (caller == null ? "NULL" : caller));

        boolean doLock = false;

//        int period = getPeriod();

//        if (period == CRITICAL_PERIOD) {
//            log("Smartlock: Time is CRITICAL");
//
//            if (preferences.isFirstCriticalTimeToday()) {
//                log("Smartlock: First critical time today");
//                preferences.updateLastCriticalTime();
//                doLock = true;
//            } else {
//                if (isForbiddenAppRunning()) {
//                    log("Smartlock: forbidden app running: locking!");
//                    doLock = true;
//                }
//            }
//        }
        int newPeriod = getPeriod();
        Log.d("kernel", "Period: " + Integer.toString(newPeriod));

        if (newPeriod == CRITICAL_PERIOD) {
            Log.d("kernel", "Smartlock: Time is CRITICAL");

            if (preferences.isFirstCriticalTimeToday()) {
                Log.d("kernel", "Smartlock: First critical time today");
                preferences.updateLastCriticalTime();
                doLock = true;
            }
        }

        int lastPeriod = preferences.getCurPeriod();
        if (lastPeriod != newPeriod) {
            preferences.updatePeriod(newPeriod);
            if (lastPeriod == SAFE_PERIOD) {
                Log.d("kernel", "Smartlock: change of period: locking now: " + Integer.toString(lastPeriod) + Integer.toString(newPeriod));
                doLock = true;
            } else {
                Log.d("kernel", "Smartlock: change of period to SAFE. Doing nothing");
            }
        } else {
            Log.d("kernel", "Smartlock: same period: " + Integer.toString(lastPeriod));
        }

        if (newPeriod != SAFE_PERIOD && isForbiddenAppRunning()) {
            bringToFront();
        }

        if (doLock) {
            runAnotherHomeLauncher();

            if (DeviceAdmin.isEnabled(context)) {
                DeviceAdmin.lockNow(context);
            } else {
                Log.e("kernel", "ERROR: ADMIN IS DISABLED");
            }
        }
    }

    public void bringToFront() {
        Intent intent = new Intent(context.getApplicationContext(), MainActivity.class);
        context.getApplicationContext().startActivity(intent);
    }


}