package com.example.myapplication;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import java.io.File;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;

public class Preferences {
    static public final String defaultSafeTime = "05:00";
    static public final String defaultDangerTime = "18:00";
    static public final String defaultCriticalTime = "23:00";
    static public final String defaultTimeZone = "Europe/Moscow";
    static public final String defaultLogFileName = "log.txt";

    static private final String SAFE_TIME = "safeTime";
    static private final String DANGER_TIME = "dangerTimer";
    static private final String CRIT_TIME = "criticalTime";
    static private final String SHOULD_TIMER_BE_RUNNING = "timer";
    static private final String TIMER_ID = "TIMER_ID";
    static private final String ENABLE_SMARTLOCK = "ENABLE_SMARTLOCK";
    static private final String HOME_LAUNCHER = "HOME_LAUNCHER";
    static private final String DANGER_PROCESSES = "killProcessList";
    static private final String CRITICAL_PROCESSES = "CRITICAL_PROCESSES";
    static private final String LAST_CRITICAL_TIME = "LAST_CRITICAL_TIME";

    private Context context;

    public Preferences(@NonNull Context context) {
        this.context = context;
    }

    public Set<String> getDangerProcessesSet() {
        Set<String> hs = new HashSet<>(Arrays.asList(getDangerProcessesString().split("\\s+")));
        hs.remove("");
        return hs;
    }

    public Set<String> getCriticalProcessesSet() {
        Set<String> hs = new HashSet<>(Arrays.asList(getCriticalProcessesString().split("\\s+")));
        hs.remove("");
        return hs;
    }

    private SharedPreferences getPreferences() {
        return context.getSharedPreferences(Kernel.class.getName(), 0);
    }

    public String getTimeZone() {
        return defaultTimeZone;
    }

    public String getLogFileName() {
        return defaultLogFileName;
    }

    public File getLogFile() {
        File path = context.getApplicationContext().getExternalFilesDir(null);
        return new File(path, getLogFileName());
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

    public void updateLastCriticalTime() {
        SharedPreferences.Editor editor = getPreferences().edit();
        editor.putLong(LAST_CRITICAL_TIME, Calendar.getInstance().getTimeInMillis());
        editor.apply();
    }

    public boolean isFirstCriticalTimeToday() {
        long lastMillis = getPreferences().getLong(LAST_CRITICAL_TIME, 0);
        Calendar lastInstance = Calendar.getInstance();
        lastInstance.setTimeInMillis(lastMillis);

        Calendar today = Calendar.getInstance();

        return today.get(Calendar.DAY_OF_MONTH) == lastInstance.get(Calendar.DAY_OF_MONTH)
                &&
                today.get(Calendar.MONTH) == lastInstance.get(Calendar.MONTH)
                &&
                today.get(Calendar.YEAR) == lastInstance.get(Calendar.YEAR)
                ;
    }
}
