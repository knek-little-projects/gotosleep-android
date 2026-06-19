package com.example.myapplication;

import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
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

        // Debug/test override: the "Force critical for 2 min" button in ControlActivity sets
        // a deadline in prefs. While it's active we short-circuit straight to CRITICAL_PERIOD;
        // once it expires we tear everything down (disable smartlock, stop timer) so the app
        // doesn't stay "locked" forever if the UI process died.
        long forceCritUntil = preferences.getForceCriticalUntilMillis();
        if (forceCritUntil > 0) {
            long nowMillis = System.currentTimeMillis();
            if (nowMillis < forceCritUntil) {
                if (!preferences.isSmartLockEnabled()) {
                    // User disabled smartlock manually during the 2-min window - honour that
                    // and clear the override so we don't re-enable it implicitly.
                    preferences.setForceCriticalUntilMillis(0L);
                    return SAFE_PERIOD;
                }
                return CRITICAL_PERIOD;
            }
            // Expired: perform the documented teardown exactly once.
            Log.i("kernel", "Force critical window expired - disabling smartlock");
            log("Force critical expired - smartlock disabled");
            preferences.setForceCriticalUntilMillis(0L);
            preferences.setSmartLockEnabled(false);
            preferences.setShouldTimerBeRunning(false);
            return SAFE_PERIOD;
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

    public boolean isPasswordDisabled() {
        if (preferences.doesPasswordHasDisablePeriod()) {
            return isTimeSeq(preferences.getPasswordDisablePeriodStart(), getNow(), preferences.getPasswordDisablePeriodEnd());
        }
        return false;
    }

    static public boolean isTimeSeq(String a, String b, String c) {
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

    /**
     * Returns the package name of the app most recently brought to the foreground, or null if
     * UsageStats is unavailable / permission is not granted. Extracted so that we can reuse it
     * both for forbidden-app detection and for the bringToFront() fallback verification.
     */
    public String getTopPackage() {
        UsageStatsManager mUsageStatsManager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        if (mUsageStatsManager == null) {
            return null;
        }
        long time = System.currentTimeMillis();
        long millisec = 60000;
        List<UsageStats> stats = mUsageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, time - millisec, time);
        if (stats == null || stats.isEmpty()) {
            return null;
        }
        stats.sort(new Comparator<UsageStats>() {
            @Override
            public int compare(UsageStats a, UsageStats b) {
                return -Long.compare(a.getLastTimeUsed(), b.getLastTimeUsed());
            }
        });
        return stats.get(0).getPackageName();
    }

    public boolean isForbiddenAppRunning() {
        return getForbiddenTopPackage() != null;
    }

    /**
     * Returns the package name currently on top IF and ONLY IF it is considered forbidden in the
     * current period (via {@link StaticProcessList}). Otherwise returns null. Our own package is
     * always treated as allowed to avoid feedback loops.
     */
    public String getForbiddenTopPackage() {
        String pkgName = getTopPackage();
        if (pkgName == null) {
            Log.d("Timer", "Empty usage stats");
            return null;
        }
        if (pkgName.equals(context.getPackageName())) {
            Log.d("Timer", "Last is our own package");
            return null;
        }
        if (StaticProcessList.fromPreferences(this, preferences).isPackageAllowed(pkgName)) {
            Log.d("Timer", "Last is allowed " + pkgName);
            return null;
        }
        Log.w("Timer", "Last is forbidden " + pkgName);
        return pkgName;
    }

    private static int installedApplicationsQueryFlags() {
        int flags = PackageManager.GET_META_DATA;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            flags |= PackageManager.MATCH_DISABLED_COMPONENTS
                    | PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS;
        }
        return flags;
    }

    /**
     * Same package visibility as {@link #getPackageNameList()} (QUERY_ALL_PACKAGES + flags).
     */
    @NonNull
    static List<ApplicationInfo> getInstalledApplicationsCompat(@NonNull Context context) {
        PackageManager pm = context.getPackageManager();
        int flags = installedApplicationsQueryFlags();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(flags));
        }
        return pm.getInstalledApplications(flags);
    }

    public ArrayList<String> getPackageNameList() {
        final ArrayList<String> apps = new ArrayList<>();
        for (ApplicationInfo applicationInfo : getInstalledApplicationsCompat(context)) {
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

        if (newPeriod != SAFE_PERIOD) {
            String offending = getForbiddenTopPackage();
            if (offending != null) {
                bringToFront(offending);
            }
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

    /** Convenience overload preserved for callers that don't track the offending package. */
    public void bringToFront() {
        String offending = getForbiddenTopPackage();
        bringToFront(offending);
    }

    /**
     * Three-layer strategy for ejecting the user out of a forbidden foreground app:
     *
     *   (1) AccessibilityService performGlobalAction(HOME) - works from any thread/context,
     *       exempt from Android 10+ BAL restrictions, requires the user to enable our
     *       MinimizeAccessibilityService. This is the path we want to hit in practice.
     *
     *   (2) startActivity(MainActivity) - only succeeds from background if the app has
     *       SYSTEM_ALERT_WINDOW ("Display over other apps") granted. Otherwise the OS
     *       silently blocks the launch and logs "Background activity launch blocked".
     *
     *   (3) Fallback: 1500ms later, re-check UsageStats. If the same forbidden package is
     *       still on top, both (1) and (2) failed - so we lock the device via DeviceAdmin.
     *       Comparing against the *specific* previously-offending package (not just
     *       "any forbidden app") avoids false-positive locks when (1) correctly sent the
     *       user to the home launcher and the home launcher itself isn't whitelisted.
     *
     * @param offendingPackage the package seen on top before this call, or null if unknown.
     *                         When null we skip the fallback check, since we wouldn't know
     *                         what "success" looks like.
     */
    public void bringToFront(final String offendingPackage) {
        if (offendingPackage != null) {
            BlockNotificationHelper.notifyAppBlockedIfDue(context, offendingPackage, getPeriod());
        }

        boolean a11yFired = MinimizeAccessibilityService.goHome();
        if (a11yFired) {
            Log.d("kernel", "bringToFront: GLOBAL_ACTION_HOME dispatched via accessibility");
        } else {
            Log.d("kernel", "bringToFront: a11y service not available, trying startActivity");
            try {
                Intent intent = new Intent(context.getApplicationContext(), MainActivity.class);
                // startActivity() from a non-Activity (ApplicationContext) context requires NEW_TASK,
                // otherwise Android throws AndroidRuntimeException. Note: from API 29+ this call is
                // also subject to BAL and may silently fail unless SYSTEM_ALERT_WINDOW is granted.
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.getApplicationContext().startActivity(intent);
                Log.d("kernel", "bringToFront: startActivity(MainActivity) fired");
            } catch (Throwable t) {
                Log.w("kernel", "bringToFront: startActivity threw", t);
            }
        }

        if (offendingPackage == null) {
            return;
        }

        scheduleEnforceFallback(offendingPackage);
    }

    /**
     * Post a delayed check on the main looper: if the forbidden app is STILL on top after the
     * grace period, it means neither accessibility nor startActivity succeeded - escalate to
     * a device lock via DeviceAdmin, which always works (no BAL restriction applies to lockNow).
     */
    private void scheduleEnforceFallback(final String expectedOffendingPackage) {
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                String nowTop = getTopPackage();
                if (nowTop == null) {
                    Log.d("kernel", "bringToFront fallback: no usage stats, skipping");
                    return;
                }
                if (!expectedOffendingPackage.equals(nowTop)) {
                    Log.d("kernel", "bringToFront fallback OK: top changed " + expectedOffendingPackage + " -> " + nowTop);
                    return;
                }
                Log.w("kernel", "bringToFront fallback: " + nowTop + " still on top - LOCKING");
                log("bringToFront fallback: locking (" + nowTop + " would not minimize)");
                BlockNotificationHelper.notifyFallbackLockIfDue(context, nowTop, getPeriod());
                if (DeviceAdmin.isEnabled(context)) {
                    DeviceAdmin.lockNow(context);
                } else {
                    Log.e("kernel", "bringToFront fallback: admin disabled, cannot lock");
                }
            }
        }, 1500);
    }

    /**
     * Whether the OS has granted this app the overlay permission, which is what lets our
     * background startActivity() calls bypass BAL restrictions on Android 10+. Prior to M this
     * permission is granted at install time and always true.
     */
    public boolean canDrawOverlays() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        return Settings.canDrawOverlays(context);
    }


}