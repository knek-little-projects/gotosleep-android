package com.example.myapplication;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import android.app.ActivityManager;
import android.app.AppOpsManager;
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
import android.net.Uri;
import android.os.Bundle;
import android.os.Debug;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONObject;

import org.w3c.dom.Text;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ControlActivity extends AppCompatActivity {

    public static final int RESULT_ENABLE = 11;
    public Timer timer = null;
    public Kernel kernel = null;
    public Preferences preferences = null;

    public static int REQUEST_BLACK_LIST = 1000;
    public static int REQUEST_WHITE_LIST = 1001;
    public static int REQUEST_SAVE_SETTINGS_FILE = 1002;
    public static int REQUEST_LOAD_SETTINGS_FILE = 1003;

    @Override
    protected void onResume() {
        super.onResume();

        NightMode.apply(this);

        final CheckBox doesPasswordHasDisablePeriod = (CheckBox) findViewById(R.id.disablePassword);
        doesPasswordHasDisablePeriod.setChecked(preferences.doesPasswordHasDisablePeriod());

        final EditText disablePasswordStart = (EditText) findViewById(R.id.disablePasswordStart);
        disablePasswordStart.setText(preferences.getPasswordDisablePeriodStart());

        final EditText disablePasswordEnd = (EditText) findViewById(R.id.disablePasswordEnd);
        disablePasswordEnd.setText(preferences.getPasswordDisablePeriodEnd());

        TextView failsafePasswordEdit = (TextView) findViewById(R.id.failsafePasswordEdit);
        failsafePasswordEdit.setText(preferences.getFailsafePassword());

        TextView blacklistOnDemandPasswordEdit = (TextView) findViewById(R.id.blacklistOnDemandPasswordEdit);
        blacklistOnDemandPasswordEdit.setText(preferences.getBlacklistOnDemandPassword());

        TextView blacklistOnDemandTimeoutMinutesEdit = (TextView) findViewById(R.id.blacklistOnDemandTimeoutMinutesEdit);
        blacklistOnDemandTimeoutMinutesEdit.setText(String.valueOf(preferences.getBlacklistOnDemandTimeoutMinutes()));

        TextView pdfFolderPathEdit = (TextView) findViewById(R.id.pdfFolderPathEdit);
        pdfFolderPathEdit.setText(preferences.getPdfFolderPath());

        wirePasswordVisibilityToggle(R.id.failsafePasswordEdit, R.id.failsafePasswordVisibilityToggle);
        wirePasswordVisibilityToggle(R.id.blacklistOnDemandPasswordEdit, R.id.blacklistOnDemandPasswordVisibilityToggle);

        TextView tercActivityURL = (TextView) findViewById(R.id.editTercActivityURL);
        tercActivityURL.setText(preferences.getTERCActivityURL());

        CheckBox smartLockCheck = (CheckBox) findViewById(R.id.smartLockCheck);
        smartLockCheck.setChecked(preferences.isSmartLockEnabled());

        CheckBox tercCheck = (CheckBox) findViewById(R.id.tercCheck);
        tercCheck.setChecked(preferences.isTercUse());

        if (preferences.getShouldTimerBeRunning() && timer == null) {
            runTimer();
        }

        View mainActivityContainer = (View) findViewById(R.id.mainActivityContainer);

        boolean isAdmin = DeviceAdmin.isEnabled(this);
        boolean isSmart = preferences.isSmartLockEnabled();
        boolean isSafe = kernel.isNowSafe();
        boolean isDanger = kernel.isNowDanger();


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

        // ExistingPeriodicWorkPolicy.KEEP makes this idempotent - no need to query state first.
        // The previous .get() on the UI thread was an ANR source (same bug as the check-status button).
        startPeriodicWorker();
    }

    private void startPeriodicWorker() {
        PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(
                RepeatSmartlockWorker.class, 15, TimeUnit.MINUTES
        ).build();

        WorkManager.getInstance(getApplicationContext()).enqueueUniquePeriodicWork(
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
                kernel.smartLock("ControlActivityTimer");
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

    private void wirePasswordVisibilityToggle(int editId, int toggleButtonId) {
        final EditText edit = (EditText) findViewById(editId);
        final ImageButton toggle = (ImageButton) findViewById(toggleButtonId);
        final boolean[] visible = {false};

        toggle.setOnClickListener(v -> {
            visible[0] = !visible[0];
            int selection = edit.getSelectionStart();
            if (visible[0]) {
                edit.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
                toggle.setImageResource(R.drawable.ic_visibility_off);
                toggle.setContentDescription("Hide password");
            } else {
                edit.setTransformationMethod(PasswordTransformationMethod.getInstance());
                toggle.setImageResource(R.drawable.ic_visibility);
                toggle.setContentDescription("Show password");
            }
            if (selection >= 0) {
                edit.setSelection(selection);
            }
        });
    }

    private boolean hasUsageStatsPermission() {
        try {
            AppOpsManager appOps = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
            if (appOps == null) {
                return false;
            }
            int mode;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                mode = appOps.unsafeCheckOpNoThrow(
                        AppOpsManager.OPSTR_GET_USAGE_STATS,
                        android.os.Process.myUid(),
                        getPackageName());
            } else {
                mode = appOps.checkOpNoThrow(
                        AppOpsManager.OPSTR_GET_USAGE_STATS,
                        android.os.Process.myUid(),
                        getPackageName());
            }
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) {
            return false;
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
        setContentView(R.layout.activity_control);

        try {
            String versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            ((TextView) findViewById(R.id.versionLabel)).setText("Version " + versionName);
        } catch (PackageManager.NameNotFoundException ignored) {
        }

        final Context context = this;
        kernel = new Kernel(context);
        preferences = new Preferences(context);

        final CheckBox doesPasswordHasDisablePeriod = (CheckBox) findViewById(R.id.disablePassword);
        final EditText disablePasswordStart = (EditText) findViewById(R.id.disablePasswordStart);
        final EditText disablePasswordEnd = (EditText) findViewById(R.id.disablePasswordEnd);
        Button disablePasswordSaveButton = (Button) findViewById(R.id.disablePasswordSaveButton);
        disablePasswordSaveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (preferences.setPasswordDisablePeriodStart(disablePasswordStart.getText().toString()) && preferences.setPasswordDisablePeriodEnd(disablePasswordEnd.getText().toString())) {
                    preferences.setPasswordHasDisablePeriod(doesPasswordHasDisablePeriod.isChecked());
                } else {
                    preferences.setSmartLockEnabled(false);
                    Toast.makeText(context, "ERROR", Toast.LENGTH_SHORT).show();
                }
            }
        });

        EditText passwordDisablePeriodStart = (EditText) findViewById(R.id.disablePasswordStart);
        passwordDisablePeriodStart.setText(preferences.getPasswordDisablePeriodStart());

        EditText passwordDisablePeriodEnd = (EditText) findViewById(R.id.disablePasswordEnd);
        passwordDisablePeriodEnd.setText(preferences.getPasswordDisablePeriodEnd());

        View mainActivityContainer = (View) findViewById(R.id.mainActivityContainer);
        mainActivityContainer.setVisibility(View.GONE);

        final DevicePolicyManager devicePolicyManager = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
        final ComponentName componentName = new ComponentName(this, DeviceAdmin.class);

        final CheckBox smartLockCheck = (CheckBox) findViewById(R.id.smartLockCheck);
        smartLockCheck.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                preferences.setSmartLockEnabled(b);
                if (b) {
                    ensureAllRunning();
                }
            }
        });

        ((Button) findViewById(R.id.forceCriticalButton)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Debug helper: simulate whitelist (critical) zone for exactly 2 minutes regardless of the
                // configured safe/danger/critical times. Kernel.getPeriod() reads the deadline
                // from prefs, so all three enforcement mechanisms (Timer/Worker/Alarm) will
                // treat "now" as critical (whitelist) until the deadline passes. At that point Kernel's
                // lazy check does the teardown; we ALSO schedule a UI-side teardown below so
                // the user sees the checkbox flip back to off while this screen is open.
                final long durationMs = 2 * 60 * 1000L;
                final long expireAt = System.currentTimeMillis() + durationMs;

                preferences.setForceDangerUntilMillis(0L);
                preferences.setForceCriticalUntilMillis(expireAt);
                preferences.setSmartLockEnabled(true);
                smartLockCheck.setChecked(true);
                ensureAllRunning();
                Toast.makeText(context, "Whitelist zone forced for 2 minutes", Toast.LENGTH_SHORT).show();

                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        // Belt: clear the override / smartlock here so UI reflects the state
                        // immediately. Suspenders: Kernel.getPeriod() would also clear these
                        // lazily, so the state stays consistent even if this Activity was
                        // destroyed before the deadline.
                        preferences.setForceCriticalUntilMillis(0L);
                        preferences.setSmartLockEnabled(false);
                        preferences.setShouldTimerBeRunning(false);
                        if (!isFinishing() && !isDestroyed()) {
                            smartLockCheck.setChecked(false);
                            Toast.makeText(context, "Force whitelist expired", Toast.LENGTH_SHORT).show();
                        }
                    }
                }, durationMs);
            }
        });

        ((Button) findViewById(R.id.forceBlacklistButton)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final long durationMs = 2 * 60 * 1000L;
                final long expireAt = System.currentTimeMillis() + durationMs;

                preferences.setForceCriticalUntilMillis(0L);
                preferences.setForceDangerUntilMillis(expireAt);
                preferences.setSmartLockEnabled(true);
                smartLockCheck.setChecked(true);
                ensureAllRunning();
                Toast.makeText(context, "Blacklist zone forced for 2 minutes", Toast.LENGTH_SHORT).show();

                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        preferences.setForceDangerUntilMillis(0L);
                        preferences.setSmartLockEnabled(false);
                        preferences.setShouldTimerBeRunning(false);
                        if (!isFinishing() && !isDestroyed()) {
                            smartLockCheck.setChecked(false);
                            Toast.makeText(context, "Force blacklist expired", Toast.LENGTH_SHORT).show();
                        }
                    }
                }, durationMs);
            }
        });

        ((Button) findViewById(R.id.testAlarmButton)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                BlockNotificationHelper.showTestNotification(context);
                Toast.makeText(context, R.string.test_notification_sent, Toast.LENGTH_SHORT).show();
            }
        });

        CheckBox tercCheck = (CheckBox) findViewById(R.id.tercCheck);
        tercCheck.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                preferences.setTercUse(b);
            }
        });

        final EditText tercActivityURL = (EditText) findViewById(R.id.editTercActivityURL);
        tercActivityURL.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                preferences.setTERCACtivityURL(tercActivityURL.getText().toString());
            }
        });

        ((Button) findViewById(R.id.debugRequestActivityURL)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String url = preferences.getTERCActivityURL();
                Log.d("qwe", "Request " + url);
                RequestQueue requestQueue = Volley.newRequestQueue(getApplicationContext());
                StringRequest stringRequest = new StringRequest(
                        Request.Method.GET,
                        url,
                        new Response.Listener<String>() {
                            @Override
                            public void onResponse(String response) {
                                Log.d("qwe", response);
                                Toast.makeText(context, "Got response: " + response, Toast.LENGTH_SHORT).show();
                            }
                        },
                        new Response.ErrorListener() {
                            @Override
                            public void onErrorResponse(VolleyError error) {
                                Log.e("qwe", error.toString());
                                Toast.makeText(context, "ERROR", Toast.LENGTH_SHORT).show();

                            }
                        }
                );

                requestQueue.add(stringRequest);
            }
        });

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
                Toast.makeText(context, preferences.getLogFile().toString(), Toast.LENGTH_SHORT).show();
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

        ((Button) findViewById(R.id.overlayPermissionButton)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // ACTION_MANAGE_OVERLAY_PERMISSION is the only way to grant SYSTEM_ALERT_WINDOW
                // on M+; there is no runtime-permission dialog for it. On pre-M the permission
                // is granted at install time and this toggle is a no-op.
                if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M) {
                    Toast.makeText(context, "Already granted on this Android version", Toast.LENGTH_SHORT).show();
                    return;
                }
                try {
                    Intent intent = new Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            android.net.Uri.parse("package:" + context.getPackageName()));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                } catch (Exception e) {
                    // Some OEM ROMs don't honour the per-package URI. Fall back to the global
                    // overlay settings screen which always resolves.
                    try {
                        Intent fallback = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                        fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(fallback);
                    } catch (Exception e2) {
                        Toast.makeText(context, "Cannot open overlay settings: " + e2, Toast.LENGTH_LONG).show();
                    }
                }
            }
        });

        ((Button) findViewById(R.id.accessibilityServiceButton)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                    Toast.makeText(context,
                            "Find \"" + getString(R.string.app_name) + "\" in the list and enable it",
                            Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    Toast.makeText(context, "Cannot open accessibility settings: " + e, Toast.LENGTH_LONG).show();
                }
            }
        });

        ((Button) findViewById(R.id.allFilesAccessButton)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) {
                    Toast.makeText(context, "Not needed on this Android version", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (PdfFileFinder.hasAllFilesAccess()) {
                    Toast.makeText(context, "Already granted", Toast.LENGTH_SHORT).show();
                    return;
                }
                try {
                    Intent intent = new Intent(
                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            Uri.parse("package:" + context.getPackageName()));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                } catch (Exception e) {
                    try {
                        Intent fallback = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                        fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(fallback);
                    } catch (Exception e2) {
                        Toast.makeText(context, "Cannot open all-files-access settings: " + e2, Toast.LENGTH_LONG).show();
                    }
                }
            }
        });

        ((Button) findViewById(R.id.pdfFolderPathButton)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                TextView pathEdit = (TextView) findViewById(R.id.pdfFolderPathEdit);
                String path = pathEdit.getText().toString().trim();
                if (!PdfFileFinder.isPathAllowed(path)) {
                    Toast.makeText(context, "Path must be inside external storage", Toast.LENGTH_LONG).show();
                    return;
                }
                preferences.setPdfFolderPath(path);
                Toast.makeText(context, "PDF folder saved", Toast.LENGTH_SHORT).show();
            }
        });

        ((Button) findViewById(R.id.failsafePasswordButton)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                TextView textView = (TextView) findViewById(R.id.failsafePasswordEdit);
                preferences.setFailsafePassword(textView.getText().toString());
            }
        });

        ((Button) findViewById(R.id.blacklistOnDemandPasswordButton)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                TextView passwordEdit = (TextView) findViewById(R.id.blacklistOnDemandPasswordEdit);
                preferences.setBlacklistOnDemandPassword(passwordEdit.getText().toString());

                TextView timeoutEdit = (TextView) findViewById(R.id.blacklistOnDemandTimeoutMinutesEdit);
                int minutes;
                try {
                    minutes = Integer.parseInt(timeoutEdit.getText().toString().trim());
                } catch (NumberFormatException e) {
                    minutes = Preferences.defaultBlacklistOnDemandTimeoutMinutes;
                }
                preferences.setBlacklistOnDemandTimeoutMinutes(minutes);
                timeoutEdit.setText(String.valueOf(minutes));
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
                processListView.setText(preferences.getDangerProcessesString());

                EditText editWhiteList = (EditText) findViewById(R.id.editWhiteList);
                editWhiteList.setText(preferences.getCriticalProcessesString());
            }
        });

        ((Button) findViewById(R.id.loadKillProcessList)).performClick();

        ((Button) findViewById(R.id.saveKillProcessListButton)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                EditText processListView = (EditText) findViewById(R.id.editKillProcessList);
                preferences.setDangerProcesses(processListView.getText().toString());

                EditText editWhiteList = (EditText) findViewById(R.id.editWhiteList);
                preferences.setCriticalProcesses(editWhiteList.getText().toString());
            }
        });

        ((Button) findViewById(R.id.editCriticalTimeWhitelist)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(context, SelectMultipleActivities.class);
                intent.putExtra("selectedApps", new ArrayList<>(preferences.getCriticalProcessesSet()));
                intent.putExtra("totalApps", kernel.getPackageNameList());
                startActivityForResult(intent, REQUEST_WHITE_LIST);
            }
        });

        ((Button) findViewById(R.id.editDangerzoneBlacklist)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(context, SelectMultipleActivities.class);
                intent.putExtra("selectedApps", new ArrayList<>(preferences.getDangerProcessesSet()));
                intent.putExtra("totalApps", kernel.getPackageNameList());
                startActivityForResult(intent, REQUEST_BLACK_LIST);
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

        ((Button) findViewById(R.id.startTimerButton)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                preferences.setShouldTimerBeRunning(true);
                runTimer();
            }
        });

        ((Button) findViewById(R.id.stopTimerButton)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                preferences.setShouldTimerBeRunning(false);
            }
        });

        ((Button) findViewById(R.id.showRunningProcessesButton)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                TextView textView = (TextView) findViewById(R.id.processListView);

                if (!hasUsageStatsPermission()) {
                    textView.setText("No usage access permission. Tap to grant it in Settings.");
                    Toast.makeText(ControlActivity.this,
                            "Grant Usage access to see running processes",
                            Toast.LENGTH_LONG).show();
                    try {
                        startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
                    } catch (Exception e) {
                        Log.w("showRunningProcesses", "cannot open usage access settings", e);
                    }
                    return;
                }

                UsageStatsManager mUsageStatsManager = (UsageStatsManager) getSystemService(USAGE_STATS_SERVICE);
                if (mUsageStatsManager == null) {
                    textView.setText("UsageStatsManager is not available on this device");
                    return;
                }

                long time = System.currentTimeMillis();
                long windowMs = TimeUnit.HOURS.toMillis(24);
                List<UsageStats> stats = mUsageStatsManager.queryUsageStats(
                        UsageStatsManager.INTERVAL_DAILY, time - windowMs, time);

                if (stats == null || stats.isEmpty()) {
                    textView.setText("No usage data in the last 24h");
                    return;
                }

                stats.sort(new Comparator<UsageStats>() {
                    @Override
                    public int compare(UsageStats a, UsageStats b) {
                        return -Long.compare(a.getLastTimeUsed(), b.getLastTimeUsed());
                    }
                });

                StringBuilder sb = new StringBuilder();
                sb.append(stats.size()).append(" packages (last 24h):\n\n");
                for (UsageStats usageStats : stats) {
                    long ageSec = Math.max(0, (time - usageStats.getLastTimeUsed()) / 1000);
                    sb.append(usageStats.getPackageName())
                            .append("  (")
                            .append(ageSec)
                            .append("s ago)\n");
                }
                textView.setText(sb.toString());
            }
        });

        ((Button) findViewById(R.id.lockFromServiceButton)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent i = new Intent(context, RepeatSmartlockService.class);
                i.putExtra("LockNow", true);
                RepeatSmartlockService.enqueue(context, i);
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
                WorkManager.getInstance(getApplicationContext()).cancelUniqueWork(RepeatSmartlockWorker.class.getName());
                Toast.makeText(view.getContext(), "Stopped daemon", Toast.LENGTH_SHORT).show();
            }
        });

        ((Button) findViewById(R.id.startAlarmButton)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                RepeatSmartlockAlarm.setAlarm(context);
                Toast.makeText(view.getContext(), "Started alarm job", Toast.LENGTH_SHORT).show();
            }
        });

        ((Button) findViewById(R.id.stopAlarmButton)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                RepeatSmartlockAlarm.cancelAlarm(context);
                Toast.makeText(view.getContext(), "Stopped alarm job", Toast.LENGTH_SHORT).show();
            }
        });

        ((Button) findViewById(R.id.checkStatusButton)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final TextView textView = (TextView) findViewById(R.id.checkStatusLabel);
                textView.setText("…");

                final Context appCtx = getApplicationContext();
                final ExecutorService ex = Executors.newSingleThreadExecutor();
                ex.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            String daemonLine;
                            try {
                                List<WorkInfo> infos = WorkManager.getInstance(appCtx)
                                        .getWorkInfosForUniqueWork(RepeatSmartlockWorker.class.getName())
                                        .get();
                                StringBuilder w = new StringBuilder();
                                for (WorkInfo info : infos) {
                                    if (w.length() > 0) {
                                        w.append(", ");
                                    }
                                    w.append(info.getState().toString());
                                }
                                daemonLine = w.length() > 0 ? w.toString() : "(none)";
                            } catch (Exception e) {
                                daemonLine = "error: " + e.getMessage();
                                Log.w("checkStatus", "WorkManager query failed", e);
                            }

                            final String line = daemonLine;
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        StringBuilder sb = new StringBuilder();
                                        sb.append("Allow usage stats: ");
                                        sb.append(hasUsageStatsPermission() ? "yes" : "no").append("\n");

                                        // Reflects two things that together decide whether
                                        // bringToFront() can actually work from a background
                                        // context (Timer / Worker / AlarmReceiver).
                                        sb.append("Overlay (SYSTEM_ALERT_WINDOW): ");
                                        sb.append(kernel.canDrawOverlays() ? "yes" : "no").append("\n");

                                        sb.append("Accessibility minimize service: ");
                                        if (MinimizeAccessibilityService.isRunning()) {
                                            sb.append("RUNNING");
                                        } else if (MinimizeAccessibilityService.isEnabledInSettings(context)) {
                                            sb.append("enabled in settings, not yet bound");
                                        } else {
                                            sb.append("no");
                                        }
                                        sb.append("\n");

                                        sb.append("Redirect target: Danger Zone\n");

                                        sb.append("isMyAppLauncherDefault: ");
                                        sb.append(isMyAppLauncherDefault()).append("\n");

                                        sb.append("Admin: ");
                                        if (DeviceAdmin.isEnabled(context)) {
                                            sb.append("ENABLED");
                                        } else {
                                            sb.append("no");
                                        }
                                        sb.append("\n");

                                        sb.append("Periodic daemon: ");
                                        sb.append(line).append("\n");

                                        sb.append("Some alarm: ");
                                        if (RepeatSmartlockAlarm.isSomeAlarmSet(context)) {
                                            sb.append("SET");
                                        } else {
                                            sb.append("no");
                                        }
                                        sb.append("\n");

                                        sb.append("Timer: ");
                                        if (timer != null) {
                                            sb.append("RUNNING");
                                        } else {
                                            sb.append("stopped");
                                        }
                                        sb.append("\n");

                                        textView.setText(sb.toString());
                                    } catch (Exception e) {
                                        Toast.makeText(ControlActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
                                        Log.e("checkStatus", "building status", e);
                                    }
                                }
                            });
                        } finally {
                            ex.shutdown();
                        }
                    }
                });
            }
        });

        ((Button) findViewById(R.id.anotherHomeButton)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                kernel.runAnotherHomeLauncher();
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
                if (!preferences.setSafeTime(safeTime)) {
                    Toast.makeText(view.getContext(), "ERROR setting safe time", Toast.LENGTH_SHORT).show();
                }

                String dangerTime = ((EditText) findViewById(R.id.editDangerTime)).getText().toString();
                if (!preferences.setDangerTime(dangerTime)) {
                    Toast.makeText(view.getContext(), "ERROR setting danger time", Toast.LENGTH_SHORT).show();
                }

                String criticalTime = ((EditText) findViewById(R.id.editCriticalTime)).getText().toString();
                if (!preferences.setCriticalTime(criticalTime)) {
                    Toast.makeText(view.getContext(), "ERROR setting critical time", Toast.LENGTH_SHORT).show();
                }
            }
        });

        ((Button) findViewById(R.id.loadSettingsButton)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ((EditText) findViewById(R.id.editSafeTime)).setText(preferences.getSafeTime());
                ((EditText) findViewById(R.id.editDangerTime)).setText(preferences.getDangerTime());
                ((EditText) findViewById(R.id.editCriticalTime)).setText(preferences.getCriticalTime());
            }
        });

        ((Button) findViewById(R.id.loadSettingsButton)).performClick();

        ((Button) findViewById(R.id.saveSettingsToFileButton)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                saveAllSettingsFromUi();
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/json");
                intent.putExtra(Intent.EXTRA_TITLE, "gotosleep-settings.json");
                startActivityForResult(intent, REQUEST_SAVE_SETTINGS_FILE);
            }
        });

        ((Button) findViewById(R.id.loadSettingsFromFileButton)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/json");
                startActivityForResult(intent, REQUEST_LOAD_SETTINGS_FILE);
            }
        });
    }

    private void saveAllSettingsFromUi() {
        ((Button) findViewById(R.id.saveSettingsButton)).performClick();
        ((Button) findViewById(R.id.saveKillProcessListButton)).performClick();
        ((Button) findViewById(R.id.disablePasswordSaveButton)).performClick();
        ((Button) findViewById(R.id.failsafePasswordButton)).performClick();
        ((Button) findViewById(R.id.blacklistOnDemandPasswordButton)).performClick();
    }

    private void refreshUiFromPreferences() {
        ((Button) findViewById(R.id.loadSettingsButton)).performClick();
        ((Button) findViewById(R.id.loadKillProcessList)).performClick();

        ((CheckBox) findViewById(R.id.smartLockCheck)).setChecked(preferences.isSmartLockEnabled());
        ((CheckBox) findViewById(R.id.tercCheck)).setChecked(preferences.isTercUse());
        ((EditText) findViewById(R.id.editTercActivityURL)).setText(preferences.getTERCActivityURL());
        ((CheckBox) findViewById(R.id.disablePassword)).setChecked(preferences.doesPasswordHasDisablePeriod());
        ((EditText) findViewById(R.id.disablePasswordStart)).setText(preferences.getPasswordDisablePeriodStart());
        ((EditText) findViewById(R.id.disablePasswordEnd)).setText(preferences.getPasswordDisablePeriodEnd());
        ((TextView) findViewById(R.id.failsafePasswordEdit)).setText(preferences.getFailsafePassword());
        ((TextView) findViewById(R.id.blacklistOnDemandPasswordEdit)).setText(preferences.getBlacklistOnDemandPassword());
        ((TextView) findViewById(R.id.blacklistOnDemandTimeoutMinutesEdit)).setText(String.valueOf(preferences.getBlacklistOnDemandTimeoutMinutes()));
    }

    private void writeSettingsToUri(Uri uri) {
        try {
            JSONObject json = preferences.exportSettingsToJson();
            byte[] bytes = json.toString(2).getBytes(StandardCharsets.UTF_8);
            OutputStream outputStream = getContentResolver().openOutputStream(uri);
            if (outputStream == null) {
                Toast.makeText(this, "Cannot write file", Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                outputStream.write(bytes);
            } finally {
                outputStream.close();
            }
            Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Save failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            Log.e("ControlActivity", "writeSettingsToUri", e);
        }
    }

    private void readSettingsFromUri(Uri uri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            if (inputStream == null) {
                Toast.makeText(this, "Cannot read file", Toast.LENGTH_SHORT).show();
                return;
            }
            byte[] buffer = new byte[8192];
            StringBuilder sb = new StringBuilder();
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                sb.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
            }
            inputStream.close();

            JSONObject json = new JSONObject(sb.toString());
            String error = preferences.importSettingsFromJson(json);
            if (error != null) {
                Toast.makeText(this, error, Toast.LENGTH_LONG).show();
                return;
            }
            refreshUiFromPreferences();
            Toast.makeText(this, "Settings loaded", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Load failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            Log.e("ControlActivity", "readSettingsFromUri", e);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode != RESULT_OK || data == null) {
            return;
        }

        Uri uri = data.getData();
        if (requestCode == REQUEST_SAVE_SETTINGS_FILE) {
            if (uri != null) {
                writeSettingsToUri(uri);
            }
            return;
        }

        if (requestCode == REQUEST_LOAD_SETTINGS_FILE) {
            if (uri != null) {
                readSettingsFromUri(uri);
            }
            return;
        }

        if (requestCode == REQUEST_BLACK_LIST) {
            ArrayList<String> selectedApps = data.getStringArrayListExtra("selectedApps");

            if (selectedApps == null) {
                return;
            }
            preferences.setDangerProcesses(selectedApps);

            EditText processListView = (EditText) findViewById(R.id.editKillProcessList);
            processListView.setText(preferences.getDangerProcessesString());
        }


        if (requestCode == REQUEST_WHITE_LIST) {
            ArrayList<String> selectedApps = data.getStringArrayListExtra("selectedApps");

            if (selectedApps == null) {
                return;
            }

            preferences.setCriticalProcesses(selectedApps);

            EditText editWhiteList = (EditText) findViewById(R.id.editWhiteList);
            editWhiteList.setText(preferences.getCriticalProcessesString());
        }
    }
}