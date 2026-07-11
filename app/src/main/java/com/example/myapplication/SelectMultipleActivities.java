package com.example.myapplication;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Context;
import android.content.Intent;
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
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class SelectMultipleActivities extends AppCompatActivity {
    private static final int FILTER_DEBOUNCE_MS = 220;

    private EditText editAppLaunchSearch;
    private Button okButton, cancelButton;
    private Context context;
    private PackageManager pm;

    private PackagePickerAdapter adapter;
    private final List<String> filteredItems = new ArrayList<>();

    private final Handler debounceHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingFilter;

    private HashSet<String> selectedApps;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_select_multiple_activities);

        selectedApps = new HashSet<>(getSelectedAppList());
        context = this;
        pm = getPackageManager();
        editAppLaunchSearch = (EditText) findViewById(R.id.editAppLaunchSearch);
        okButton = (Button) findViewById(R.id.okButton);
        cancelButton = (Button) findViewById(R.id.cancelButton);
        cancelButton.requestFocus();

        RecyclerView recyclerView = findViewById(R.id.appRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PackagePickerAdapter(this, pm, selectedApps);
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

        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent();
                intent.putStringArrayListExtra("selectedApps", null);
                setResult(RESULT_CANCELED, intent);
                finish();
            }
        });

        okButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent();
                intent.putStringArrayListExtra("selectedApps", new ArrayList<>(selectedApps));
                setResult(RESULT_OK, intent);
                finish();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyFilter(editAppLaunchSearch.getText().toString());
        cancelButton.requestFocus();
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
        filteredItems.clear();
        String needle = search.trim().toLowerCase();
        for (String app : getReorderedTotalAppList()) {
            if (needle.isEmpty() || app.toLowerCase().contains(needle)) {
                filteredItems.add(app);
            }
        }
        adapter.setItems(filteredItems);
    }

    private @NonNull ArrayList<String> getTotalAppList() {
        ArrayList<String> showApps = getIntent().getStringArrayListExtra("totalApps");
        if (showApps == null) {
            return new ArrayList<>();
        }
        return showApps;
    }

    private @NonNull ArrayList<String> getReorderedTotalAppList() {
        ArrayList<String> apps = getTotalAppList();
        ArrayList<String> result = new ArrayList<>();
        ArrayList<String> selected = getSelectedAppList();
        HashSet<String> added = new HashSet<>();
        for (String app : selected) {
            if (!added.contains(app)) {
                result.add(app);
                added.add(app);
            }
        }
        for (String app : apps) {
            if (!added.contains(app)) {
                result.add(app);
                added.add(app);
            }
        }
        return result;
    }

    private @NonNull ArrayList<String> getSelectedAppList() {
        ArrayList<String> showApps = getIntent().getStringArrayListExtra("selectedApps");
        if (showApps == null) {
            return new ArrayList<>();
        }
        return showApps;
    }
}
