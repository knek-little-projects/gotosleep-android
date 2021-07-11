package com.example.myapplication;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import android.app.ActivityManager;
import android.app.admin.DevicePolicyManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    public static final int RESULT_ENABLE = 11;
    public Timer timer = null;
    public Kernel kernel = null;
    public Preferences preferences = null;

    @Override
    protected void onResume() {
        super.onResume();

        if (preferences.getShouldTimerBeRunning() && timer == null) {
            runTimer();
        }

        View mainActivityContainer = (View) findViewById(R.id.mainActivityContainer);

        boolean isAdmin = DeviceAdmin.isEnabled(this);
        boolean isSmart = preferences.isSmartLockEnabled();
        boolean isSafe = kernel.isNowSafe();
        boolean isDanger = kernel.isNowDanger();

        TextView status = (TextView) findViewById(R.id.status);
        StringBuilder sb = new StringBuilder();
        sb.append("Admin: " + Boolean.toString(isAdmin) + "\n");
        sb.append("Smartlock: " + Boolean.toString(isSmart) + "\n");
        sb.append("Safe time: " + Boolean.toString(isSafe) + "\n");
        sb.append("Danger time: " + Boolean.toString(isDanger) + "\n");
        sb.append("Critical time: " + Boolean.toString(!isSafe && !isDanger) + "\n");
        status.setText(sb.toString());

        if (!isAdmin || !isSmart || isSafe) {
            mainActivityContainer.setVisibility(View.VISIBLE);
        } else {
            mainActivityContainer.setVisibility(View.GONE);
            if (!isSafe) {
                try {
                    if (!kernel.runAnotherHomeLauncher()) {
                        mainActivityContainer.setVisibility(View.VISIBLE);
                        Toast.makeText(this, "ERROR: Please set Home Launcher!", Toast.LENGTH_LONG).show();
                    }
                } catch (Exception e) {
                    Toast.makeText(this, e.toString(), Toast.LENGTH_LONG).show();
                    e.printStackTrace();
                }
            }
        }

        if (isAdmin && isSmart) {
            ensureAllRunning();
            kernel.smartLock("HOME");
        }
    }

    private void ensureAllRunning() {
        if (preferences.isSmartLockEnabled() && !preferences.getShouldTimerBeRunning()) {
            preferences.setShouldTimerBeRunning(true);
        }

        if (timer == null) {
            Log.w("Smartlock", "Timer is not running: starting");
            runTimer();
        }

        if (!RepeatSmartlockAlarm.isSomeAlarmSet(this)) {
            Log.w("Smartlock", "Alarm is not running: starting");
            RepeatSmartlockAlarm.setAlarm(this);
        }

        if (!isPeriodicWorkerRunning()) {
            Log.w("Smartlock", "Periodic worker is not running: starting");
            startPeriodicWorker();
        }
    }

    private boolean isPeriodicWorkerRunning() {
        try {
            List<WorkInfo> infos = WorkManager.getInstance().getWorkInfosForUniqueWork(RepeatSmartlockWorker.class.getName()).get();
            for (WorkInfo info : infos) {
                if (info.getState() == WorkInfo.State.ENQUEUED || info.getState() == WorkInfo.State.RUNNING) {
                    return true;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private void startPeriodicWorker() {
        PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(
                RepeatSmartlockWorker.class, 15, TimeUnit.MINUTES
        ).build();

        WorkManager.getInstance().enqueueUniquePeriodicWork(
                RepeatSmartlockWorker.class.getName(), ExistingPeriodicWorkPolicy.KEEP, workRequest);

    }

    private void runTimer() {
        timer = new Timer();
        timer.schedule(new TimerTask() {
            private long id = Calendar.getInstance().getTimeInMillis();

            public void goHome() {
                Intent startMain = new Intent(Intent.ACTION_MAIN);
                startMain.addCategory(Intent.CATEGORY_HOME);
                startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(startMain);
            }

            public void bringToFront() {
                // этот вариант почему-то не работает:
                // видимо он работает только если приложение установлено как DEFAULT HOME LAUNCHER
//                Intent intent = new Intent(Intent.ACTION_MAIN);
//                intent.addCategory(Intent.CATEGORY_LAUNCHER);
//                intent.setClassName(MainActivity.class.getPackage().getName(), MainActivity.class.getName());
//                startActivity(intent);

                // а этот срабатывает в случае, если приложение не является DEFAULT HOME LAUNCHER
                Intent intent = new Intent(getApplicationContext(), MainActivity.class);
                getApplicationContext().startActivity(intent);
            }

            private void stop() {
                cancel();
                timer.cancel();
                timer.purge();
                timer = null;
            }

            @Override
            public void run() {
                if (!preferences.getShouldTimerBeRunning()) {
                    Log.d("Timer", "Timer should not be running: cancelling timer " + Long.toString(id));
                    stop();
                    return;
                }

                long runningTimerId = preferences.getTimerId();

                if (runningTimerId > id) {
                    Log.d("Timer", "Stopping timer with outdated id " + Long.toString(id));
                    stop();
                    return;
                }

                if (runningTimerId < id) {
                    preferences.setTimerId(id);
                }

                Log.d("Timer", "This id=" + Long.toString(id) + " " + "; runningTimerId=" + Long.toString(runningTimerId));

                if (!kernel.isNowSafe() /* Critical and Danger zones */) {
                    UsageStatsManager mUsageStatsManager = (UsageStatsManager) getSystemService(USAGE_STATS_SERVICE);
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
                        if (pkgName != null && !pkgName.equals(getPackageName()) && !StaticProcessList.fromPreferences(kernel, preferences).isPackageAllowed(pkgName)) {
                            Log.w("Timer", "Last is " + pkgName);
                            Log.w("Timer", "BRINGING TO FRONT");
                            bringToFront();
                        } else {
                            Log.d("Timer", "Last is " + pkgName);
                        }
                    }
                }
            }
        }, 500, 500);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (Intent.ACTION_MAIN.equals(intent.getAction())) {
            final boolean alreadyOnHome =
                    ((intent.getFlags() & Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT)
                            != Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT);
            kernel.runAnotherHomeLauncher();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final DevicePolicyManager devicePolicyManager = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
        final ComponentName componentName = new ComponentName(this, DeviceAdmin.class);
        final Context context = this;
        kernel = new Kernel(context);
        preferences = new Preferences(context);

        ((Button) findViewById(R.id.showControlsButton)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(context, ControlActivity.class);
                startActivity(intent);
            }
        });

        ((Button) findViewById(R.id.showDangerZone)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(context, DangerZoneActivity.class);
                context.startActivity(intent);
            }
        });
    }
}