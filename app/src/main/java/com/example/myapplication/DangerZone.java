package com.example.myapplication;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RelativeLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
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

        StaticProcessList staticProcessList = StaticProcessList.fromSettings(myUtils);

        final PackageManager pm = getPackageManager();
        final List<ApplicationInfo> pkgs = pm.getInstalledApplications(0);
        for (final ApplicationInfo pkg : pkgs) {
            if (!pkg.packageName.toLowerCase().contains(search.toLowerCase())) {
                continue;
            }

            if (!staticProcessList.isPackageAllowed(pkg.packageName)) {
                continue;
            }

            final Intent intent = pm.getLaunchIntentForPackage(pkg.packageName);
            if (intent == null) {
                continue;
            }

            Log.d("qwe", "Adding " + pkg.packageName);
//            final LinearLayout.LayoutParams buttonlayout = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);

            ImageView img = new ImageView(context);
            try {
                Drawable icon = pm.getApplicationIcon(pkg);
                img.setImageDrawable(icon);
                img.setLayoutParams(new TableRow.LayoutParams(75, 75));
            } catch (Exception e) {
                e.printStackTrace();
            }

            final Button btn = new Button(context);
            btn.setLayoutParams(new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT));
            btn.setText(pkg.packageName);
            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    context.startActivity(intent);
                }
            });

            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            TableLayout table = new TableLayout(context);
            table.setLayoutParams(layoutParams);
            TableRow tr = new TableRow(context);
            tr.setLayoutParams(layoutParams);

            tr.addView(img);
            tr.addView(btn);
            table.addView(tr);
            appLaunchContainer.addView(table);
        }
    }
}