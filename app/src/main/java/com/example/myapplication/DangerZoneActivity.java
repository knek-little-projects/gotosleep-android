package com.example.myapplication;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
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
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DangerZoneActivity extends AppCompatActivity {
    private static final int FILTER_DEBOUNCE_MS = 220;

    private static final int PAGE_ICONS = 0;
    private static final int PAGE_PDF = 1;
    private static final int PAGE_UNLOCK = 2;
    private static final int REAL_PAGE_COUNT = 3;
    private static final int VIRTUAL_ITEM_COUNT = REAL_PAGE_COUNT * 100_000;
    private static final int START_POSITION = (VIRTUAL_ITEM_COUNT / 2 / REAL_PAGE_COUNT) * REAL_PAGE_COUNT;

    private Kernel kernel;
    private Context context;
    private PackageManager pm;

    private ViewPager2 pager;

    // Icons page
    private EditText editAppLaunchSearch;
    private DangerZoneAppAdapter adapter;
    private final List<String> filteredPackages = new ArrayList<>();
    private final Handler debounceHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingIconFilter;

    // PDF page
    private EditText editPdfSearch;
    private PdfListAdapter pdfAdapter;
    private TextView pdfScanStatus;
    private Button pdfRefreshButton;
    private PdfCache pdfCache;
    private ExecutorService pdfExecutor;
    private volatile boolean pdfScanInProgress;
    private Runnable pendingPdfFilter;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Unlock page
    private View unlockInputContainer;
    private TextView unlockDisabledMessage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_danger_zone);

        context = this;
        pm = getPackageManager();
        kernel = new Kernel(context);
        pdfCache = new PdfCache(context);
        pdfExecutor = Executors.newSingleThreadExecutor();

        pager = findViewById(R.id.dangerZonePager);
        pager.setAdapter(new DangerZonePagerAdapter());
        pager.setCurrentItem(START_POSITION, false);
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
                scheduleIconFilter(s != null ? s.toString() : "");
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        editAppLaunchSearch.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView view, int actionId, KeyEvent keyEvent) {
                cancelPendingIconFilter();
                applyFilter(view.getText().toString());
                return false;
            }
        });

        applyFilter("");
    }

    private void setupPdfPage(@NonNull View view) {
        editPdfSearch = view.findViewById(R.id.editPdfSearch);
        pdfScanStatus = view.findViewById(R.id.pdfScanStatus);
        pdfRefreshButton = view.findViewById(R.id.pdfRefreshButton);

        RecyclerView recyclerView = view.findViewById(R.id.pdfRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        pdfAdapter = new PdfListAdapter(this, this::onPdfClicked);
        recyclerView.setAdapter(pdfAdapter);

        editPdfSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                schedulePdfFilter(s != null ? s.toString() : "");
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        editPdfSearch.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView view, int actionId, KeyEvent keyEvent) {
                cancelPendingPdfFilter();
                applyPdfFilter(view.getText().toString());
                return false;
            }
        });

        pdfRefreshButton.setOnClickListener(v -> triggerPdfScan());

        // Loading the cached JSON is just an in-memory parse (no filesystem walk), so it's
        // done synchronously here instead of round-tripping through pdfExecutor - that removes
        // the async gap where the page would render empty for a moment before a background
        // load finished, which looked like "the cache isn't being used at all".
        boolean firstRun = !pdfCache.cacheFileExists();
        pdfCache.load();
        pdfAdapter.setItems(pdfCache.getSortedPaths());
        applyPdfFilter(editPdfSearch.getText().toString());
        if (firstRun) {
            runPdfScan();
        }
    }

    private void setupUnlockPage(@NonNull View view) {
        unlockInputContainer = view.findViewById(R.id.unlockInputContainer);
        unlockDisabledMessage = view.findViewById(R.id.unlockDisabledMessage);

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

        Preferences preferences = kernel.getPreferences();

        Switch darkModeSwitch = view.findViewById(R.id.darkModeSwitch);
        darkModeSwitch.setChecked(preferences.isDarkModeEnabled());
        darkModeSwitch.setEnabled(!preferences.isDarkModeScheduleEnabled());
        darkModeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferences.setDarkModeEnabled(isChecked);
            NightMode.apply(context);
        });

        CheckBox darkModeScheduleCheckbox = view.findViewById(R.id.darkModeScheduleCheckbox);
        EditText darkModeScheduleStartEdit = view.findViewById(R.id.darkModeScheduleStartEdit);
        EditText darkModeScheduleEndEdit = view.findViewById(R.id.darkModeScheduleEndEdit);
        Button darkModeScheduleSaveButton = view.findViewById(R.id.darkModeScheduleSaveButton);

        darkModeScheduleCheckbox.setChecked(preferences.isDarkModeScheduleEnabled());
        darkModeScheduleStartEdit.setText(preferences.getDarkModeScheduleStart());
        darkModeScheduleEndEdit.setText(preferences.getDarkModeScheduleEnd());

        darkModeScheduleCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferences.setDarkModeScheduleEnabled(isChecked);
            darkModeSwitch.setEnabled(!isChecked);
            NightMode.apply(context);
        });

        darkModeScheduleSaveButton.setOnClickListener(v -> {
            String start = darkModeScheduleStartEdit.getText().toString().trim();
            String end = darkModeScheduleEndEdit.getText().toString().trim();
            if (!preferences.setDarkModeScheduleStart(start) || !preferences.setDarkModeScheduleEnd(end)) {
                Toast.makeText(context, "Invalid time format, use HH:MM", Toast.LENGTH_SHORT).show();
                return;
            }
            Toast.makeText(context, "Night schedule saved", Toast.LENGTH_SHORT).show();
            NightMode.apply(context);
        });

        refreshUnlockGating();
    }

    private void refreshUnlockGating() {
        if (unlockInputContainer == null) {
            return;
        }
        if (kernel.isPasswordDisabled()) {
            unlockInputContainer.setVisibility(View.GONE);
            unlockDisabledMessage.setText("Unlock disabled until " + kernel.getPreferences().getPasswordDisablePeriodEnd());
            unlockDisabledMessage.setVisibility(View.VISIBLE);
        } else {
            unlockInputContainer.setVisibility(View.VISIBLE);
            unlockDisabledMessage.setVisibility(View.GONE);
        }
    }

    private void onPdfClicked(@NonNull String path) {
        // In-memory only (cheap map mutation) so the re-sort below immediately reflects it;
        // the JSON file is flushed to disk lazily in onPause(), not on every click.
        pdfCache.recordClick(path);
        applyPdfFilter(editPdfSearch.getText().toString());
        openPdf(path);
    }

    private void openPdf(@NonNull String path) {
        File file = new File(path);
        Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", file);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/pdf");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(context, "No app found to open PDF files", Toast.LENGTH_SHORT).show();
        }
    }

    private void triggerPdfScan() {
        if (pdfScanInProgress) {
            return;
        }
        if (!PdfFileFinder.hasAllFilesAccess()) {
            Toast.makeText(context, "Grant \"all files access\" in Controls first", Toast.LENGTH_LONG).show();
            return;
        }
        runPdfScan();
    }

    private void runPdfScan() {
        pdfScanInProgress = true;
        mainHandler.post(() -> {
            pdfScanStatus.setVisibility(View.VISIBLE);
            pdfRefreshButton.setEnabled(false);
        });

        pdfExecutor.execute(() -> {
            String folder = kernel.getPreferences().getPdfFolderPath();
            if (PdfFileFinder.isPathAllowed(folder)) {
                List<PdfFileFinder.Entry> scanned = PdfFileFinder.scan(folder);
                pdfCache.mergeScanResults(scanned);
                pdfCache.flushToDisk();
            }
            List<String> sorted = pdfCache.getSortedPaths();

            mainHandler.post(() -> {
                pdfAdapter.setItems(sorted);
                applyPdfFilter(editPdfSearch != null ? editPdfSearch.getText().toString() : "");
                pdfScanStatus.setVisibility(View.GONE);
                pdfRefreshButton.setEnabled(true);
                pdfScanInProgress = false;
            });
        });
    }

    private void applyPdfFilter(@NonNull String search) {
        if (pdfAdapter == null) {
            return;
        }
        String needle = search.trim().toLowerCase();
        List<String> all = pdfCache.getSortedPaths();
        if (needle.isEmpty()) {
            pdfAdapter.setItems(all);
            return;
        }
        List<String> filtered = new ArrayList<>();
        for (String path : all) {
            if (new File(path).getName().toLowerCase().contains(needle)) {
                filtered.add(path);
            }
        }
        pdfAdapter.setItems(filtered);
    }

    @Override
    protected void onResume() {
        super.onResume();
        NightMode.apply(context);
        refreshUnlockGating();
        if (editAppLaunchSearch != null) {
            applyFilter(editAppLaunchSearch.getText().toString());
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (pdfExecutor != null) {
            pdfExecutor.execute(() -> pdfCache.flushToDisk());
        }
    }

    @Override
    protected void onDestroy() {
        cancelPendingIconFilter();
        cancelPendingPdfFilter();
        if (adapter != null) {
            adapter.shutdown();
        }
        if (pdfExecutor != null) {
            pdfExecutor.shutdown();
        }
        super.onDestroy();
    }

    private void scheduleIconFilter(@NonNull final String query) {
        cancelPendingIconFilter();
        pendingIconFilter = () -> applyFilter(query);
        debounceHandler.postDelayed(pendingIconFilter, FILTER_DEBOUNCE_MS);
    }

    private void cancelPendingIconFilter() {
        if (pendingIconFilter != null) {
            debounceHandler.removeCallbacks(pendingIconFilter);
            pendingIconFilter = null;
        }
    }

    private void schedulePdfFilter(@NonNull final String query) {
        cancelPendingPdfFilter();
        pendingPdfFilter = () -> applyPdfFilter(query);
        debounceHandler.postDelayed(pendingPdfFilter, FILTER_DEBOUNCE_MS);
    }

    private void cancelPendingPdfFilter() {
        if (pendingPdfFilter != null) {
            debounceHandler.removeCallbacks(pendingPdfFilter);
            pendingPdfFilter = null;
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
     * Circular 3-page carousel (icons / pdf / unlock): itemCount is a large constant so the
     * pager can be swiped in either direction effectively forever, with the real page always
     * {@code position % REAL_PAGE_COUNT}. Keeping itemCount constant means it never needs
     * notifyDataSetChanged - the unlock page's password-disabled window is gated by hiding its
     * content (see refreshUnlockGating), not by removing it from the cycle.
     */
    private class DangerZonePagerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @Override
        public int getItemViewType(int position) {
            return position % REAL_PAGE_COUNT;
        }

        @Override
        public int getItemCount() {
            return VIRTUAL_ITEM_COUNT;
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
            } else if (viewType == PAGE_PDF) {
                View view = inflater.inflate(R.layout.panel_danger_zone_pdf, parent, false);
                setupPdfPage(view);
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
