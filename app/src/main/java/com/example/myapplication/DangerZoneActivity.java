package com.example.myapplication;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class DangerZoneActivity extends AppCompatActivity {
    private static final int FILTER_DEBOUNCE_MS = 220;

    private EditText editAppLaunchSearch;
    private Kernel kernel;
    private Context context;
    private PackageManager pm;

    private DangerZoneAppAdapter adapter;
    private final List<String> filteredPackages = new ArrayList<>();

    private final Handler debounceHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingFilter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_danger_zone);

        context = this;
        pm = getPackageManager();
        editAppLaunchSearch = (EditText) findViewById(R.id.editAppLaunchSearch);
        kernel = new Kernel(context);

        RecyclerView recyclerView = findViewById(R.id.dangerZoneRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new DangerZoneAppAdapter(this, pm, pkg -> {
            kernel.getPreferences().recordDangerZoneLaunch(pkg);
            applyFilter(editAppLaunchSearch.getText().toString());
        });
        recyclerView.setAdapter(adapter);

        editAppLaunchSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                scheduleFilter(s != null ? s.toString() : "");
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        editAppLaunchSearch.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView view, int actionId, KeyEvent keyEvent) {
                cancelPendingFilter();
                applyFilter(view.getText().toString());
                return false;
            }
        });

        ((Button) findViewById(R.id.failsafePasswordButton)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Preferences preferences = new Preferences(context);
                TextView textView = (TextView) findViewById(R.id.failsafePasswordEdit);

                if (preferences.checkFailsafePassword(textView.getText().toString())) {
                    preferences.setSmartLockEnabled(false);
                    Toast.makeText(context, "Smartlock disabled!", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(context, "Wrong password!", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void updateContainerVisibility() {
        LinearLayout disablePasswordContainer = (LinearLayout) findViewById(R.id.disablePasswordContainer);
        if (kernel.isPasswordDisabled()) {
            disablePasswordContainer.setVisibility(View.GONE);
        } else {
            disablePasswordContainer.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateContainerVisibility();
        applyFilter(editAppLaunchSearch.getText().toString());
    }

    @Override
    protected void onDestroy() {
        cancelPendingFilter();
        if (adapter != null) {
            adapter.shutdown();
        }
        super.onDestroy();
    }

    private void scheduleFilter(@NonNull final String query) {
        cancelPendingFilter();
        pendingFilter = () -> applyFilter(query);
        debounceHandler.postDelayed(pendingFilter, FILTER_DEBOUNCE_MS);
    }

    private void cancelPendingFilter() {
        if (pendingFilter != null) {
            debounceHandler.removeCallbacks(pendingFilter);
            pendingFilter = null;
        }
    }

    private void applyFilter(@NonNull String search) {
        filteredPackages.clear();
        StaticProcessList staticProcessList = StaticProcessList.fromPreferences(kernel, kernel.getPreferences());
        String needle = search.trim().toLowerCase();

        for (ApplicationInfo pkg : Kernel.getInstalledApplicationsCompat(this)) {
            if (!needle.isEmpty() && !pkg.packageName.toLowerCase().contains(needle)) {
                continue;
            }
            if (!staticProcessList.isPackageAllowed(pkg.packageName)) {
                continue;
            }
            if (pm.getLaunchIntentForPackage(pkg.packageName) == null) {
                continue;
            }
            filteredPackages.add(pkg.packageName);
        }

        Preferences prefs = kernel.getPreferences();
        Collections.sort(filteredPackages, new Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                int byTime = Long.compare(
                        prefs.getDangerZoneLastLaunchMillis(b),
                        prefs.getDangerZoneLastLaunchMillis(a));
                if (byTime != 0) {
                    return byTime;
                }
                return a.compareTo(b);
            }
        });

        adapter.setItems(filteredPackages);
    }
}
