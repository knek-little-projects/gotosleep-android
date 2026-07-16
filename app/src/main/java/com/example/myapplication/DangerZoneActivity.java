package com.example.myapplication;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.PasswordTransformationMethod;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class DangerZoneActivity extends AppCompatActivity {
    private static final int FILTER_DEBOUNCE_MS = 220;
    private static final int PAGE_ICONS = 0;
    private static final int PAGE_UNLOCK = 1;

    private EditText editAppLaunchSearch;
    private Kernel kernel;
    private Context context;
    private PackageManager pm;

    private ViewPager2 pager;
    private DangerZonePagerAdapter pagerAdapter;

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
        kernel = new Kernel(context);

        pagerAdapter = new DangerZonePagerAdapter();
        pager = findViewById(R.id.dangerZonePager);
        pager.setAdapter(pagerAdapter);
    }

    private void setupIconsPage(@NonNull View view) {
        editAppLaunchSearch = view.findViewById(R.id.editAppLaunchSearch);

        RecyclerView recyclerView = view.findViewById(R.id.dangerZoneRecyclerView);
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

        applyFilter("");
    }

    private void setupUnlockPage(@NonNull View view) {
        EditText failsafePasswordEdit = view.findViewById(R.id.failsafePasswordEdit);
        failsafePasswordEdit.setTransformationMethod(new AsteriskPasswordTransformationMethod());

        ((Button) view.findViewById(R.id.failsafePasswordButton)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Preferences preferences = new Preferences(context);
                String entered = failsafePasswordEdit.getText().toString();

                if (preferences.checkFailsafePassword(entered)) {
                    preferences.setSmartLockEnabled(false);
                    Toast.makeText(context, "Smartlock disabled!", Toast.LENGTH_SHORT).show();
                } else if (preferences.checkBlacklistOnDemandPassword(entered)) {
                    int minutes = preferences.getBlacklistOnDemandTimeoutMinutes();
                    preferences.setBlacklistOnDemandUntilMillis(System.currentTimeMillis() + minutes * 60_000L);
                    Toast.makeText(context, "Blacklist mode for " + minutes + " minutes", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(context, "Wrong password!", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        pagerAdapter.notifyDataSetChanged();
        if (editAppLaunchSearch != null) {
            applyFilter(editAppLaunchSearch.getText().toString());
        }
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
        if (adapter == null) {
            return;
        }
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

    /**
     * Two static pages: app icons, and the unlock-password panel. The unlock
     * page is omitted entirely (itemCount drops to 1) during configured
     * password-disabled windows, matching the old container-visibility gating.
     */
    private class DangerZonePagerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @Override
        public int getItemViewType(int position) {
            return position;
        }

        @Override
        public int getItemCount() {
            return kernel.isPasswordDisabled() ? 1 : 2;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            if (viewType == PAGE_ICONS) {
                View view = inflater.inflate(R.layout.panel_danger_zone_icons, parent, false);
                setupIconsPage(view);
                return new RecyclerView.ViewHolder(view) {
                };
            } else {
                View view = inflater.inflate(R.layout.panel_danger_zone_unlock, parent, false);
                setupUnlockPage(view);
                return new RecyclerView.ViewHolder(view) {
                };
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        }
    }

    /** Masks entered characters with '*' instead of the system's default dot. */
    private static class AsteriskPasswordTransformationMethod extends PasswordTransformationMethod {
        @Override
        public CharSequence getTransformation(CharSequence source, View view) {
            return new AsteriskCharSequence(source);
        }

        private static class AsteriskCharSequence implements CharSequence {
            private final CharSequence source;

            AsteriskCharSequence(CharSequence source) {
                this.source = source;
            }

            @Override
            public int length() {
                return source.length();
            }

            @Override
            public char charAt(int index) {
                return '*';
            }

            @Override
            public CharSequence subSequence(int start, int end) {
                return source.subSequence(start, end);
            }
        }
    }
}
