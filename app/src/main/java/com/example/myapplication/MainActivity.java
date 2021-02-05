package com.example.myapplication;

import androidx.annotation.NonNull;
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
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.PersistableBundle;
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
import android.widget.ScrollView;
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
    public MyUtils myUtils = null;

    @Override
    protected void onResume() {
        super.onResume();

        CheckBox smartLockCheck = (CheckBox) findViewById(R.id.smartLockCheck);
        smartLockCheck.setChecked(myUtils.isSmartLockEnabled());

        if (myUtils.getShouldTimerBeRunning() && timer == null) {
            runTimer();
        }

        View mainActivityContainer = (View) findViewById(R.id.mainActivityContainer);

        boolean isAdmin = MyAdmin.isEnabled(this);
        boolean isSmart = myUtils.isSmartLockEnabled();
        boolean isSafe = myUtils.isNowSafe();
        boolean isDanger = myUtils.isNowDanger();


        if (!isAdmin || !isSmart || isSafe) {
            mainActivityContainer.setVisibility(View.VISIBLE);
        } else {
            mainActivityContainer.setVisibility(View.GONE);
            if (isDanger) {
                try {
                    if (!myUtils.runAnotherHomeLauncher()) {
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
            myUtils.smartLock("HOME");
        }
    }

    private void ensureAllRunning() {
        if (myUtils.isSmartLockEnabled() && !myUtils.getShouldTimerBeRunning()) {
            myUtils.setShouldTimerBeRunning(true);
        }

        if (timer == null) {
            Log.w("Smartlock", "Timer is not running: starting");
            runTimer();
        }

        if (!AlarmReceiver.isSomeAlarmSet(this)) {
            Log.w("Smartlock", "Alarm is not running: starting");
            AlarmReceiver.setAlarm(this);
        }

        if (!isPeriodicWorkerRunning()) {
            Log.w("Smartlock", "Periodic worker is not running: starting");
            startPeriodicWorker();
        }
    }

    private boolean isPeriodicWorkerRunning() {
        try {
            List<WorkInfo> infos = WorkManager.getInstance().getWorkInfosForUniqueWork(MyWorker.class.getName()).get();
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
                MyWorker.class, 15, TimeUnit.MINUTES
        ).build();

        WorkManager.getInstance().enqueueUniquePeriodicWork(
                MyWorker.class.getName(), ExistingPeriodicWorkPolicy.KEEP, workRequest);

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
                if (!myUtils.getShouldTimerBeRunning()) {
                    Log.d("Timer", "Timer should not be running: cancelling timer " + Long.toString(id));
                    stop();
                    return;
                }

                long runningTimerId = myUtils.getTimerId();

                if (runningTimerId > id) {
                    Log.d("Timer", "Stopping timer with outdated id " + Long.toString(id));
                    stop();
                    return;
                }

                if (runningTimerId < id) {
                    myUtils.setTimerId(id);
                }

                Log.d("Timer", "This id=" + Long.toString(id) + " " + "; runningTimerId=" + Long.toString(runningTimerId));

                if (!myUtils.isNowSafe() /* Critical and Danger zones */) {
                    Set<String> processSet = myUtils.getDangerProcessesSet();

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
                        Log.d("Timer", "Last is " + pkgName);
                        if (processSet.contains(pkgName)) {
                            bringToFront();
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
            myUtils.runAnotherHomeLauncher();
        }
    }

    private boolean isMyAppLauncherDefault() {
        final IntentFilter filter = new IntentFilter(Intent.ACTION_MAIN);
        filter.addCategory(Intent.CATEGORY_HOME);

        List<IntentFilter> filters = new ArrayList<IntentFilter>();
        filters.add(filter);

        final String myPackageName = getPackageName();
        List<ComponentName> activities = new ArrayList<ComponentName>();
        final PackageManager packageManager = (PackageManager) getPackageManager();

        packageManager.getPreferredActivities(filters, activities, null);

        for (ComponentName activity : activities) {
            if (myPackageName.equals(activity.getPackageName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * method starts an intent that will bring up a prompt for the user
     * to select their default launcher. It comes up each time it is
     * detected that our app is not the default launcher
     */
    private void launchAppChooser() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent = Intent.createChooser(intent, "Please set launcher settings to ALWAYS");
        startActivity(intent);
    }

    public static void resetPreferredLauncherAndOpenChooser(Context context) {
        PackageManager packageManager = context.getPackageManager();
        ComponentName componentName = new ComponentName(context, FakeLauncherActivity.class);
        packageManager.setComponentEnabledSetting(componentName, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);

        Intent selector = new Intent(Intent.ACTION_MAIN);
        selector.addCategory(Intent.CATEGORY_HOME);
        selector.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(selector);

        packageManager.setComponentEnabledSetting(componentName, PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, PackageManager.DONT_KILL_APP);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        View mainActivityContainer = (View) findViewById(R.id.mainActivityContainer);
        mainActivityContainer.setVisibility(View.GONE);

        final DevicePolicyManager devicePolicyManager = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
        final ComponentName componentName = new ComponentName(this, MyAdmin.class);
        final Context context = this;
        myUtils = new MyUtils(context);

        CheckBox smartLockCheck = (CheckBox) findViewById(R.id.smartLockCheck);
        smartLockCheck.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                myUtils.setSmartLockEnabled(b);
                if (b) {
                    ensureAllRunning();
                }
            }
        });

        ((Button) findViewById(R.id.showHomeLaunchersButton)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                RadioGroup homeLaunchersGroup = (RadioGroup) findViewById(R.id.homeLaunchersGroup);
                homeLaunchersGroup.setOrientation(LinearLayout.VERTICAL);
                String selectedHomeLauncher = myUtils.getHomeLauncher();

                int count = homeLaunchersGroup.getChildCount();
                if (count > 0) {
                    for (int i = count - 1; i >= 0; i--) {
                        View o = homeLaunchersGroup.getChildAt(i);
                        if (o instanceof RadioButton) {
                            homeLaunchersGroup.removeViewAt(i);
                        }
                    }
                }

                final PackageManager packageManager = getPackageManager();
                for (final ResolveInfo resolveInfo : packageManager.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)) {
                    String pkg = resolveInfo.activityInfo.packageName;
                    final RadioButton btn = new RadioButton(context);
                    btn.setText(pkg);
                    btn.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                        @Override
                        public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                            if (b) {
                                myUtils.setHomeLauncher(btn.getText().toString());
                            }
                        }
                    });
                    homeLaunchersGroup.addView(btn);
                    if (pkg.equals(selectedHomeLauncher)) {
                        btn.setChecked(true);
                    }
                }
            }
        });

        ((Button) findViewById(R.id.showHomeLaunchersButton)).performClick();

        ((Button) findViewById(R.id.enableAdminButton)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
                intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName);
                startActivityForResult(intent, RESULT_ENABLE);
            }
        });

        ((Button) findViewById(R.id.disableAdminButton)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                devicePolicyManager.removeActiveAdmin(componentName);
            }
        });

        ((Button) findViewById(R.id.showLogFilePathButton)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Toast.makeText(context, myUtils.getLogFile().toString(), Toast.LENGTH_SHORT).show();
            }
        });

        ((Button) findViewById(R.id.usageStatsAccessButton)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent();
                intent.setAction(Settings.ACTION_USAGE_ACCESS_SETTINGS);
                context.startActivity(intent);
            }
        });

        ((Button) findViewById(R.id.homeButton)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent startMain = new Intent(Intent.ACTION_MAIN);
                startMain.addCategory(Intent.CATEGORY_HOME);
                startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(startMain);
            }
        });

        ((Button) findViewById(R.id.setAsHomeLauncherButton)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                launchAppChooser();
            }
        });

        ((Button) findViewById(R.id.resetAppChooserButton)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                resetPreferredLauncherAndOpenChooser(context);
            }
        });

        ((Button) findViewById(R.id.showPkgsButton)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                PackageManager pm = getPackageManager();
                List<ApplicationInfo> pkgs = pm.getInstalledApplications(0);
                StringBuilder sb = new StringBuilder();
                for (ApplicationInfo pkg : pkgs) {
                    sb.append(pkg.packageName).append("\n");
                }
                TextView textView = (TextView) findViewById(R.id.showPkgsView);
                textView.setText(sb.toString());
            }
        });

        ((Button) findViewById(R.id.loadKillProcessList)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                EditText processListView = (EditText) findViewById(R.id.editKillProcessList);
                processListView.setText(myUtils.getDangerProcessesString());

                EditText editWhiteList = (EditText) findViewById(R.id.editWhiteList);
                editWhiteList.setText(myUtils.getCriticalProcessesString());
            }
        });

        ((Button) findViewById(R.id.loadKillProcessList)).performClick();

        ((Button) findViewById(R.id.saveKillProcessListButton)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                EditText processListView = (EditText) findViewById(R.id.editKillProcessList);
                myUtils.setDangerProcessesString(processListView.getText().toString());

                EditText editWhiteList = (EditText) findViewById(R.id.editWhiteList);
                myUtils.setCriticalProcessesString(editWhiteList.getText().toString());
            }
        });

        ((Button) findViewById(R.id.killProcessListButton)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                EditText processListView = (EditText) findViewById(R.id.editKillProcessList);
                Set<String> processSet = new HashSet<>(Arrays.asList(processListView.getText().toString().split("\\s+")));

                PackageManager pm = getPackageManager();
                ActivityManager am = (ActivityManager) context.getSystemService(ACTIVITY_SERVICE);

                List<ApplicationInfo> pkgs = pm.getInstalledApplications(0);
                for (ApplicationInfo pkg : pkgs) {
                    if (processSet.contains(pkg.packageName)) {
                        am.killBackgroundProcesses(pkg.packageName);
                        Log.w("killBackgroundProcesses", pkg.packageName);
                    }
                }
            }
        });

        ((Button) findViewById(R.id.showDangerZone)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(context, DangerZone.class);
                context.startActivity(intent);
            }
        });

        ((Button) findViewById(R.id.startTimerButton)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                myUtils.setShouldTimerBeRunning(true);
                runTimer();
            }
        });

        ((Button) findViewById(R.id.stopTimerButton)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                myUtils.setShouldTimerBeRunning(false);
            }
        });

        ((Button) findViewById(R.id.showRunningProcessesButton)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                TextView textView = (TextView) findViewById(R.id.processListView);

                UsageStatsManager mUsageStatsManager = (UsageStatsManager) getSystemService(USAGE_STATS_SERVICE);
                long time = System.currentTimeMillis();
                long seconds = 120;
                List<UsageStats> stats = mUsageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, time - 1000 * seconds, time);

                stats.sort(new Comparator<UsageStats>() {
                    @Override
                    public int compare(UsageStats a, UsageStats b) {
                        return -Long.compare(a.getLastTimeUsed(), b.getLastTimeUsed());
                    }
                });

                // Sort the stats by the last time used
                StringBuilder sb = new StringBuilder();
                for (UsageStats usageStats : stats) {
                    sb.append(usageStats.getPackageName()).append("\n");
                }
                textView.setText(sb.toString());
            }
        });

        ((Button) findViewById(R.id.lockFromServiceButton)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent i = new Intent(context, MyService.class);
                i.putExtra("LockNow", true);
                startService(i);
            }
        });

        ((Button) findViewById(R.id.startCronjobButton)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startPeriodicWorker();
                Toast.makeText(view.getContext(), "Started daemon", Toast.LENGTH_SHORT).show();
            }
        });

        ((Button) findViewById(R.id.stopCronjobButton)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                WorkManager.getInstance().cancelUniqueWork(MyWorker.class.getName());
                Toast.makeText(view.getContext(), "Stopped daemon", Toast.LENGTH_SHORT).show();
            }
        });

        ((Button) findViewById(R.id.startAlarmButton)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                AlarmReceiver.setAlarm(context);
                Toast.makeText(view.getContext(), "Started alarm job", Toast.LENGTH_SHORT).show();
            }
        });

        ((Button) findViewById(R.id.stopAlarmButton)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                AlarmReceiver.cancelAlarm(context);
                Toast.makeText(view.getContext(), "Stopped alarm job", Toast.LENGTH_SHORT).show();
            }
        });

        ((Button) findViewById(R.id.checkStatusButton)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                TextView textView = (TextView) findViewById(R.id.checkStatusLabel);
                StringBuilder sb = new StringBuilder();

                sb.append("Allow usage stats: ???\n");

                sb.append("Home Launcher: ");
                String hl = myUtils.getHomeLauncher();
                if (hl != null) {
                    sb.append(hl);
                }
                sb.append("\n");

                sb.append("isMyAppLauncherDefault: ");
                sb.append(isMyAppLauncherDefault()).append("\n");

                sb.append("Admin: ");
                if (MyAdmin.isEnabled(context)) {
                    sb.append("ENABLED");
                }
                sb.append("\n");

                sb.append("Periodic daemon: ");
                try {
                    List<WorkInfo> infos = WorkManager.getInstance().getWorkInfosForUniqueWork(MyWorker.class.getName()).get();
                    for (WorkInfo info : infos) {
                        sb.append(info.getState().toString());
                    }
                } catch (Exception e) {
                    sb.append(e.toString());
                    e.printStackTrace();
                }
                sb.append("\n");

                sb.append("Some alarm: ");
                if (AlarmReceiver.isSomeAlarmSet(context)) {
                    sb.append("SET");
                }
                sb.append("\n");

                sb.append("Timer: ");
                if (timer != null) {
                    sb.append("RUNNING");
                }
                sb.append("\n");

                textView.setText(sb.toString());
            }
        });

        ((Button) findViewById(R.id.anotherHomeButton)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                myUtils.runAnotherHomeLauncher();
            }
        });

        ((Button) findViewById(R.id.launchAppButton)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String pkg = ((EditText) findViewById(R.id.launchAppEdit)).getText().toString();
                PackageManager pm = getPackageManager();
                Intent intent = pm.getLaunchIntentForPackage(pkg);

                if (intent == null) {
                    final PackageManager packageManager = getPackageManager();
                    for (final ResolveInfo resolveInfo : packageManager.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)) {
                        if (!getPackageName().equals(resolveInfo.activityInfo.packageName))  //if this activity is not in our activity (in other words, it's another default home screen)
                        {
                            Log.i("Launch App", resolveInfo.activityInfo.packageName + " " + resolveInfo.activityInfo.name);
                            intent = new Intent()
                                    .addCategory(Intent.CATEGORY_HOME)
                                    .setAction(Intent.ACTION_MAIN)
                                    .setClassName(resolveInfo.activityInfo.packageName, resolveInfo.activityInfo.name);
                            break;
                        }
                    }
                }

                if (intent == null) {
                    Toast.makeText(context, "Launch intent wasn't found", Toast.LENGTH_SHORT).show();
                    return;
                }

                try {
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(context, e.toString(), Toast.LENGTH_SHORT).show();
                    e.printStackTrace();
                }

            }
        });

        ((Button) findViewById(R.id.saveSettingsButton)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String safeTime = ((EditText) findViewById(R.id.editSafeTime)).getText().toString();
                if (!myUtils.setSafeTime(safeTime)) {
                    Toast.makeText(view.getContext(), "ERROR setting safe time", Toast.LENGTH_SHORT).show();
                }

                String dangerTime = ((EditText) findViewById(R.id.editDangerTime)).getText().toString();
                if (!myUtils.setDangerTime(dangerTime)) {
                    Toast.makeText(view.getContext(), "ERROR setting danger time", Toast.LENGTH_SHORT).show();
                }

                String criticalTime = ((EditText) findViewById(R.id.editCriticalTime)).getText().toString();
                if (!myUtils.setCriticalTime(criticalTime)) {
                    Toast.makeText(view.getContext(), "ERROR setting critical time", Toast.LENGTH_SHORT).show();
                }
            }
        });

        ((Button) findViewById(R.id.loadSettingsButton)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ((EditText) findViewById(R.id.editSafeTime)).setText(myUtils.getSafeTime());
                ((EditText) findViewById(R.id.editDangerTime)).setText(myUtils.getDangerTime());
                ((EditText) findViewById(R.id.editCriticalTime)).setText(myUtils.getCriticalTime());
            }
        });

        ((Button) findViewById(R.id.loadSettingsButton)).performClick();
    }
}