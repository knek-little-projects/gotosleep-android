package com.example.myapplication;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DangerZone extends AppCompatActivity {
    private EditText editAppLaunchSearch;
    private LinearLayout appLaunchContainer;
    private LinearLayout container;
    private MyUtils myUtils;
    private Context context;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_danger_zone);

        context = this;
        container = (LinearLayout) findViewById(R.id.dangerZoneContainer);
        editAppLaunchSearch = (EditText) findViewById(R.id.editAppLaunchSearch);
        appLaunchContainer = (LinearLayout) findViewById(R.id.appLaunchContainer);
        myUtils = new MyUtils(context);

        updateContainerVisibility();
        editAppLaunchSearch.setOnEditorActionListener(
                new TextView.OnEditorActionListener() {
                    @Override
                    public boolean onEditorAction(TextView view, int i, KeyEvent keyEvent) {
                        showApps(((TextView) view).getText().toString());
                        return false;
                    }
                }
        );
    }

    private void updateContainerVisibility() {
//        if (myUtils.isNowCritical()) {
//            container.setVisibility(View.GONE);
//        } else {
//            container.setVisibility(View.VISIBLE);
//        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateContainerVisibility();
        showApps(editAppLaunchSearch.getText().toString());
    }

    private void showApps(@NonNull String search) {
        Log.d("qwe", search);

        int count = appLaunchContainer.getChildCount();
        if (count > 0) {
            for (int i = count - 1; i >= 0; i--) {
//                View o = appLaunchContainer.getChildAt(i);
                appLaunchContainer.removeViewAt(i);
            }
        }

        Set<String> blacklist = null;
        Set<String> whitelist = null;

        if (myUtils.isNowDanger()) {
            blacklist = myUtils.getDangerProcessesSet();
        }

        if (myUtils.isNowCritical()) {
            whitelist = myUtils.getCriticalProcessesSet();
        }

        final PackageManager pm = getPackageManager();
        final List<ApplicationInfo> pkgs = pm.getInstalledApplications(0);
        for (final ApplicationInfo pkg : pkgs) {
            if (!pkg.packageName.toLowerCase().contains(search.toLowerCase())) {
                continue;
            }

            if (whitelist != null && !whitelist.contains(pkg.packageName)) {
                continue;
            }

            if (blacklist != null && blacklist.contains(pkg.packageName)) {
                continue;
            }

            final Intent intent = pm.getLaunchIntentForPackage(pkg.packageName);
            if (intent == null) {
                continue;
            }

            Log.d("qwe", "Adding " + pkg.packageName);
            final Button btn = new Button(context);
            final LinearLayout.LayoutParams buttonlayout = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            appLaunchContainer.addView(btn, buttonlayout);

            btn.setText(pkg.packageName);
            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    context.startActivity(intent);
                }
            });
        }
    }
}