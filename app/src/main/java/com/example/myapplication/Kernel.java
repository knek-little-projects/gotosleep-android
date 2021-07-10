package com.example.myapplication;

import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

public class Kernel {
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
        context.startActivity(new Intent(context, DangerZone.class));
    }

    public Preferences getPreferences() {
        return preferences;
    }

    public boolean isNowSafe() {
//        return false;
        return isTimeSeq(preferences.getSafeTime(), getNow(), preferences.getDangerTime());
    }

    public boolean isNowDanger() {
//        return false;
        return isTimeSeq(preferences.getDangerTime(), getNow(), preferences.getCriticalTime());
    }

    public boolean isNowCritical() {
//        return true;
        return isTimeSeq(preferences.getCriticalTime(), getNow(), preferences.getSafeTime());
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

    public void smartLock(String caller) {
        log("Smartlock: call from " + (caller == null ? "NULL" : caller));

        boolean doLock = false;

        if (isNowCritical()) {
            log("Smartlock: Time is CRITICAL");

            if (preferences.isFirstCriticalTimeToday()) {
                log("Smartlock: First critical time today");
                preferences.updateLastCriticalTime();
                doLock = true;
            } else {
                if (isForbiddenAppRunning()) {
                    log("Smartlock: forbidden app running: locking!");
                    doLock = true;
                }
            }
        }

        if (doLock) {
            runAnotherHomeLauncher();

            if (DeviceAdmin.isEnabled(context)) {
                DeviceAdmin.lockNow(context);
            } else {
                log("ERROR: ADMIN IS DISABLED");
            }
        }
    }
}