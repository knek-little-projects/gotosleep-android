package com.example.myapplication;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import org.w3c.dom.Text;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Preferences {
    static public final String defaultSafeTime = "05:00";
    static public final String defaultDangerTime = "18:00";
    static public final String defaultCriticalTime = "23:00";
    static public final String defaultTimeZone = "Europe/Moscow";
    static public final String defaultLogFileName = "log.txt";
    static public final String defaultFailsafePassword = "";
    static public final String defaultTERCActivityURL = "http://18.133.52.116:12345/check-allowed/activity";
    static public final Boolean defaultUseTERC = false;
    static public final Boolean defaultIsTERCActivityAllowed = false;
    static public final int defaultCurPeriod = 0;
    static public final int defaultPrevPeriod = 0;

    static private final String TERC_USE = "TERC_USE";
    static private final String TERC_IS_ACTIVITY_ALLOWED = "TERC_IS_ACTIVITY_ALLOWED";
    static private final String TERC_ACTIVITY_URL = "TERC_ACTIVITY_URL1";
    static private final String FAILSAFE_PASSWORD = "FAILSAFE_PASSWORD";
    static private final String SAFE_TIME = "safeTime";
    static private final String DANGER_TIME = "dangerTimer";
    static private final String CRIT_TIME = "criticalTime";
    static private final String SHOULD_TIMER_BE_RUNNING = "timer";
    static private final String TIMER_ID = "TIMER_ID";
    static private final String ENABLE_SMARTLOCK = "ENABLE_SMARTLOCK";
    static private final String DANGER_PROCESSES = "killProcessList";
    static private final String CRITICAL_PROCESSES = "CRITICAL_PROCESSES";
    static private final String LAST_CRITICAL_TIME = "LAST_CRITICAL_TIME";
    static private final String LAST_TERC_REQUEST_TIME = "LAST_TERC_REQUEST_TIME";
    static private final String CUR_PERIOD = "CUR_PERIOD";
    static private final String PREV_PERIOD = "PREV_PERIOD";
    static private final String PASSWORD_DISABLE_PERIOD_ENABLED = "PASSWORD_DISABLE_PERIOD_ENABLED";
    static private final String PASSWORD_DISABLE_PERIOD_START = "PASSWORD_DISABLE_PERIOD_START";
    static private final String PASSWORD_DISABLE_PERIOD_END = "PASSWORD_DISABLE_PERIOD_END";
    /** Per-package last launch time from Danger Zone list (epoch millis). Key = prefix + packageName. */
    static private final String DANGER_ZONE_LAST_LAUNCH_PREFIX = "DZ_LAST_";
    /**
     * Epoch-millis deadline while we are simulating whitelist (critical) zone on user demand (the
     * "Force whitelist zone for 2 min" debug button in ControlActivity). 0 means no override.
     * When {@code now >= forceCriticalUntilMillis} the override is considered expired and
     * must be torn down (smartlock disabled, timer stopped).
     */
    static private final String FORCE_CRITICAL_UNTIL = "FORCE_CRITICAL_UNTIL";
    /** Same pattern as {@link #FORCE_CRITICAL_UNTIL} — debug "Force blacklist zone for 2 min" button. */
    static private final String FORCE_DANGER_UNTIL = "FORCE_DANGER_UNTIL";
    static private final String LAST_BLOCK_NOTIFICATION_MILLIS = "LAST_BLOCK_NOTIFICATION_MILLIS";
    static private final String LAST_FALLBACK_LOCK_NOTIFICATION_MILLIS = "LAST_FALLBACK_LOCK_NOTIFICATION_MILLIS";

    private Context context;

    public Preferences(@NonNull Context context) {
        this.context = context;
    }

    public long getLastBlockNotificationMillis() {
        return getPreferences().getLong(LAST_BLOCK_NOTIFICATION_MILLIS, 0L);
    }

    public void setLastBlockNotificationMillis(long millis) {
        SharedPreferences.Editor editor = getPreferences().edit();
        editor.putLong(LAST_BLOCK_NOTIFICATION_MILLIS, millis);
        editor.apply();
    }

    public long getLastFallbackLockNotificationMillis() {
        return getPreferences().getLong(LAST_FALLBACK_LOCK_NOTIFICATION_MILLIS, 0L);
    }

    public void setLastFallbackLockNotificationMillis(long millis) {
        SharedPreferences.Editor editor = getPreferences().edit();
        editor.putLong(LAST_FALLBACK_LOCK_NOTIFICATION_MILLIS, millis);
        editor.apply();
    }

    public boolean doesPasswordHasDisablePeriod() {
        return getPreferences().getBoolean(PASSWORD_DISABLE_PERIOD_ENABLED, false);
    }

    public void setPasswordHasDisablePeriod(boolean b) {
        SharedPreferences.Editor editor = getPreferences().edit();
        editor.putBoolean(PASSWORD_DISABLE_PERIOD_ENABLED, b);
        editor.apply();
    }

    public boolean setPasswordDisablePeriodStart(@NonNull String val) {
        return setTimeLevel(PASSWORD_DISABLE_PERIOD_START, val);
    }

    public boolean setPasswordDisablePeriodEnd(@NonNull String val) {
        return setTimeLevel(PASSWORD_DISABLE_PERIOD_END, val);
    }

    public String getPasswordDisablePeriodStart() {
        return getPreferences().getString(PASSWORD_DISABLE_PERIOD_START, "00:00");
    }

    public String getPasswordDisablePeriodEnd() {
        return getPreferences().getString(PASSWORD_DISABLE_PERIOD_END, "00:00");
    }

    public long getForceCriticalUntilMillis() {
        return getPreferences().getLong(FORCE_CRITICAL_UNTIL, 0L);
    }

    public void setForceCriticalUntilMillis(long epochMillis) {
        SharedPreferences.Editor editor = getPreferences().edit();
        editor.putLong(FORCE_CRITICAL_UNTIL, epochMillis);
        editor.apply();
    }

    public long getForceDangerUntilMillis() {
        return getPreferences().getLong(FORCE_DANGER_UNTIL, 0L);
    }

    public void setForceDangerUntilMillis(long epochMillis) {
        SharedPreferences.Editor editor = getPreferences().edit();
        editor.putLong(FORCE_DANGER_UNTIL, epochMillis);
        editor.apply();
    }

    public long getLastTERCRequestTime() {
        return getPreferences().getLong(LAST_TERC_REQUEST_TIME, 0);
    }

    public void setLastTercRequestTime(long val) {
        SharedPreferences.Editor editor = getPreferences().edit();
        editor.putLong(LAST_TERC_REQUEST_TIME, val);
        editor.apply();
    }

    public void recordDangerZoneLaunch(@NonNull String packageName) {
        SharedPreferences.Editor editor = getPreferences().edit();
        editor.putLong(DANGER_ZONE_LAST_LAUNCH_PREFIX + packageName, System.currentTimeMillis());
        editor.apply();
    }

    public long getDangerZoneLastLaunchMillis(@NonNull String packageName) {
        return getPreferences().getLong(DANGER_ZONE_LAST_LAUNCH_PREFIX + packageName, 0L);
    }

    public int getCurPeriod() {
        return getPreferences().getInt(CUR_PERIOD, defaultCurPeriod);
    }

    public int getPrevPeriod() {
        return getPreferences().getInt(PREV_PERIOD, defaultCurPeriod);
    }

    public void updatePeriod(int period) {
        SharedPreferences.Editor editor = getPreferences().edit();
        editor.putInt(PREV_PERIOD, getCurPeriod());
        editor.putInt(CUR_PERIOD, period);
        editor.apply();
    }

    public Boolean isTERCActivityAllowed() {
        return getPreferences().getBoolean(TERC_IS_ACTIVITY_ALLOWED, defaultIsTERCActivityAllowed);
    }

    public void setTercActivityAllowed(Boolean b) {
        SharedPreferences.Editor editor = getPreferences().edit();
        editor.putBoolean(TERC_IS_ACTIVITY_ALLOWED, b);
        editor.apply();
    }

    public void setTercUse(Boolean use) {
        SharedPreferences.Editor editor = getPreferences().edit();
        editor.putBoolean(TERC_USE, use);
        editor.apply();
    }

    public Boolean isTercUse() {
        return getPreferences().getBoolean(TERC_USE, defaultUseTERC);
    }

    public void setFailsafePassword(String password) {
        SharedPreferences.Editor editor = getPreferences().edit();
        editor.putString(FAILSAFE_PASSWORD, password);
        editor.apply();
    }

    public String getFailsafePassword() {
        return getPreferences().getString(FAILSAFE_PASSWORD, defaultFailsafePassword);
    }

    public void setTERCACtivityURL(String url) {
        SharedPreferences.Editor editor = getPreferences().edit();
        editor.putString(TERC_ACTIVITY_URL, url);
        editor.apply();
    }

    public String getTERCActivityURL() {
        return getPreferences().getString(TERC_ACTIVITY_URL, defaultTERCActivityURL);
    }

    public Boolean checkFailsafePassword(String password) {
        return password.trim().equals(getFailsafePassword().trim());
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

    public void setDangerProcesses(String s) {
        SharedPreferences.Editor editor = getPreferences().edit();
        editor.putString(DANGER_PROCESSES, s);
        editor.apply();
    }

    public void setDangerProcesses(ArrayList<String> items) {
        SharedPreferences.Editor editor = getPreferences().edit();
        editor.putString(DANGER_PROCESSES, TextUtils.join("\n", items));
        editor.apply();
    }

    public void setCriticalProcesses(String s) {
        SharedPreferences.Editor editor = getPreferences().edit();
        editor.putString(CRITICAL_PROCESSES, s);
        editor.apply();
    }

    public void setCriticalProcesses(ArrayList<String> items) {
        SharedPreferences.Editor editor = getPreferences().edit();
        editor.putString(CRITICAL_PROCESSES, TextUtils.join("\n", items));
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
        if (hhmm.length() == 4) {
            hhmm = "0" + hhmm;
        }

        if (!hhmm.matches("[0-9]{2}:[0-9]{2}")) {
            return false;
        }

        String[] items = hhmm.split(":");
        int hh = Integer.parseInt(items[0]);
        int mm = Integer.parseInt(items[1]);

        if (!(0 <= hh && hh <= 23 && 0 <= mm && mm <= 59)) {
            return false;
        }

        SharedPreferences.Editor editor = getPreferences().edit();
        editor.putString(level, hhmm);
        editor.apply();
        return true;
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
