package com.example.myapplication;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.util.Log;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

public class MyUtils {

    static public String defaultSafeTime = "05:00";
    static public String defaultDangerTime = "18:00";
    static public String defaultCriticalTime = "23:00";
    static public String defaultTimeZone = "Europe/Moscow";
    static public final String defaultLogFileName = "log.txt";

    private final String SAFE_TIME = "safeTime";
    private final String DANGER_TIME = "dangerTimer";
    private final String CRIT_TIME = "criticalTime";
    private final String SHOULD_TIMER_BE_RUNNING = "timer";
    private final String TIMER_ID = "TIMER_ID";
    private final String ENABLE_SMARTLOCK = "ENABLE_SMARTLOCK";
    private final String HOME_LAUNCHER = "HOME_LAUNCHER";
    private final String DANGER_PROCESSES = "killProcessList";
    private final String CRITICAL_PROCESSES = "CRITICAL_PROCESSES";

    public boolean runAnotherHomeLauncher() {
        String homeLauncher = getHomeLauncher();
        Log.i("qwe", "Launching " + homeLauncher);

        if (homeLauncher == null) {
            return false;
        }

        if (context.getPackageName().equals(homeLauncher)) {
            showAppSelector();
            return true;
        }

        if (getDangerProcessesSet().contains(homeLauncher)) {
            return false;
        }

        final PackageManager packageManager = context.getPackageManager();
        for (final ResolveInfo resolveInfo : packageManager.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)) {
            if (resolveInfo.activityInfo.packageName.equals(homeLauncher)) {
                Log.i("Launch App", resolveInfo.activityInfo.packageName);
                Intent intent = new Intent().addCategory(Intent.CATEGORY_HOME).setAction(Intent.ACTION_MAIN).setClassName(resolveInfo.activityInfo.packageName, resolveInfo.activityInfo.name);
                context.startActivity(intent);
                return true;
            }
        }

        return false;
    }

    public void showAppSelector() {
        context.startActivity(new Intent(context, DangerZone.class));
    }


    public Set<String> getDangerProcessesSet() {
        return new HashSet<>(Arrays.asList(getDangerProcessesString().split("\\s+")));
    }

    public Set<String> getCriticalProcessesSet() {
        return new HashSet<>(Arrays.asList(getCriticalProcessesString().split("\\s+")));
    }

    public String getCriticalProcessesString() {
        return getPreferences().getString(CRITICAL_PROCESSES, "");
    }

    public String getDangerProcessesString() {
        return getPreferences().getString(DANGER_PROCESSES, "");
    }

    public void setDangerProcessesString(String s) {
        SharedPreferences.Editor editor = getPreferences().edit();
        editor.putString(DANGER_PROCESSES, s);
        editor.apply();
    }

    public void setCriticalProcessesString(String s) {
        SharedPreferences.Editor editor = getPreferences().edit();
        editor.putString(CRITICAL_PROCESSES, s);
        editor.apply();
    }

    public boolean isNowSafe() {
//        return false;
        return isTimeSeq(getSafeTime(), getNow(), getDangerTime());
    }

    public boolean isNowDanger() {
//        return false;
        return isTimeSeq(getDangerTime(), getNow(), getCriticalTime());
    }

    public boolean isNowCritical() {
//        return true;
        return isTimeSeq(getCriticalTime(), getNow(), getSafeTime());
    }

    public String getHomeLauncher() {
        return getPreferences().getString(HOME_LAUNCHER, null);
    }

    public void setHomeLauncher(String s) {
        SharedPreferences.Editor editor = getPreferences().edit();
        editor.putString(HOME_LAUNCHER, s);
        editor.apply();
    }

    public boolean isSmartLockEnabled() {
        return getPreferences().getBoolean(ENABLE_SMARTLOCK, false);
    }

    public void setSmartLockEnabled(boolean value) {
        SharedPreferences.Editor editor = getPreferences().edit();
        editor.putBoolean(ENABLE_SMARTLOCK, value);
        editor.apply();
    }

    private Context context;

    public long getTimerId() {
        return getPreferences().getLong(TIMER_ID, (long) 0);
    }

    public void setTimerId(long id) {
        SharedPreferences.Editor editor = getPreferences().edit();
        editor.putLong(TIMER_ID, id);
        editor.apply();
    }

    public boolean getShouldTimerBeRunning() {
        return getPreferences().getBoolean(SHOULD_TIMER_BE_RUNNING, false);
    }

    public void setShouldTimerBeRunning(boolean value) {
        SharedPreferences.Editor editor = getPreferences().edit();
        editor.putBoolean(SHOULD_TIMER_BE_RUNNING, value);
        editor.apply();
    }

    private SharedPreferences getPreferences() {
        return context.getSharedPreferences(MyUtils.class.getName(), 0);
    }

    public MyUtils(@NonNull Context context) {
        this.context = context;
    }

    public String getCriticalTime() {
        return getPreferences().getString(CRIT_TIME, defaultCriticalTime);
    }

    public String getDangerTime() {
        return getPreferences().getString(DANGER_TIME, defaultDangerTime);
    }

    public String getSafeTime() {
        return getPreferences().getString(SAFE_TIME, defaultSafeTime);
    }

    public boolean setTimeLevel(String level, String hhmm) {
        if (hhmm.matches("[0-9]{2}:[0-9]{2}")) {
            SharedPreferences.Editor editor = getPreferences().edit();
            editor.putString(level, hhmm);
            editor.apply();
            return true;
        } else {
            return false;
        }
    }

    public boolean setCriticalTime(String hhmm) {
        return setTimeLevel(CRIT_TIME, hhmm);
    }

    public boolean setDangerTime(String hhmm) {
        return setTimeLevel(DANGER_TIME, hhmm);
    }

    public boolean setSafeTime(String hhmm) {
        return setTimeLevel(SAFE_TIME, hhmm);
    }

    static private boolean isTimeSeq(String a, String b, String c) {
        if (a.compareTo(c) <= 0) {
            return a.compareTo(b) <= 0 && b.compareTo(c) < 0;
        } else {
            return a.compareTo(b) <= 0 || b.compareTo(c) < 0;
        }
    }

    public String getLogFileName() {
        return defaultLogFileName;
    }

    public File getLogFile() {
        File path = context.getApplicationContext().getExternalFilesDir(null);
        return new File(path, getLogFileName());
    }

    public void log(String text) {
        Log.i("MyUtils", text);
        File file = getLogFile();

        text = "[" + getNow() + "] " + text + "\n";

        try {
            FileOutputStream fileOutputStream = new FileOutputStream(file, true);
            fileOutputStream.write(text.getBytes());
            fileOutputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static private Calendar getAfter(Calendar now, int hours, int minutes) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.US);

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

    public String getTimeZone() {
        return defaultTimeZone;
    }

    public String getNow() {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.US);
        Date currentTime = Calendar.getInstance().getTime();
        sdf.setTimeZone(TimeZone.getTimeZone(getTimeZone()));
        return sdf.format(currentTime);
    }

    public long getNextSafeTimeInMillis(Calendar now) {
        return getAfter(now, getSafeTime()).getTimeInMillis();
    }

    public long getNextDangerTimeInMillis(Calendar now) {
        return getAfter(now, getDangerTime()).getTimeInMillis();
    }

    public long getNextCriticalTimeInMillis(Calendar now) {
        return getAfter(now, getCriticalTime()).getTimeInMillis();
    }

    public void smartLock(String caller) {
//        if (caller == null) {
//            log("CALL FROM ???");
//        } else {
//            log("CALL FROM " + caller);
//        }
//
//        if (isNowSafe()) {
//            log("Time is SAFE");
//        }
//
//        if (isNowDanger()) {
//            log("Time is DANGEROUS");
//        }
//
//        if (isNowCritical()) {
//            log("Time is CRITICAL");
//            if (MyAdmin.isEnabled(context)) {
//                MyAdmin.lockNow(context);
//            } else {
//                log("ERROR: ADMIN IS DISABLED");
//            }
//        }
    }
}