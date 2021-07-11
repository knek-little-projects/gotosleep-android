package com.example.myapplication;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class SelectMultipleActivities extends AppCompatActivity {
    private EditText editAppLaunchSearch;
    private Button okButton, cancelButton;
    private LinearLayout appLaunchContainer;
    private Context context;

    private HashSet<String> selectedApps;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_select_multiple_activities);

        selectedApps = new HashSet<>(getSelectedAppList());
        context = this;
        editAppLaunchSearch = (EditText) findViewById(R.id.editAppLaunchSearch);
        appLaunchContainer = (LinearLayout) findViewById(R.id.appLaunchContainer);
        okButton = (Button) findViewById(R.id.okButton);
        cancelButton = (Button) findViewById(R.id.cancelButton);
        cancelButton.requestFocus();

        editAppLaunchSearch.setOnEditorActionListener(
                new TextView.OnEditorActionListener() {
                    @Override
                    public boolean onEditorAction(TextView view, int i, KeyEvent keyEvent) {
                        showApps(((TextView) view).getText().toString());
                        return false;
                    }
                }
        );

        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent();
                intent.putStringArrayListExtra("selectedApps", null);
                setResult(0, intent);
                finish();
            }
        });

        okButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent();
                intent.putStringArrayListExtra("selectedApps", new ArrayList<>(selectedApps));
                setResult(1, intent);
                finish();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        showApps(editAppLaunchSearch.getText().toString());
        cancelButton.requestFocus();
    }

    private @NonNull
    ArrayList<String> getTotalAppList() {
        ArrayList<String> showApps = getIntent().getStringArrayListExtra("totalApps");
        if (showApps == null) {
            return new ArrayList<String>();
        } else {
            return showApps;
        }
    }

    private @NonNull ArrayList<String> getReorderedTotalAppList() {
        ArrayList<String> apps = getTotalAppList(), result = new ArrayList<>(), selected = getSelectedAppList();
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

    private @NonNull
    ArrayList<String> getSelectedAppList() {
        ArrayList<String> showApps = getIntent().getStringArrayListExtra("selectedApps");
        if (showApps == null) {
            return new ArrayList<String>();
        } else {
            return showApps;
        }
    }

    private void clearAppListView() {
        int count = appLaunchContainer.getChildCount();
        if (count > 0) {
            for (int i = count - 1; i >= 0; i--) {
                appLaunchContainer.removeViewAt(i);
            }
        }
    }

    private HashMap<String, Drawable> getAppIconMap() {
        HashMap<String, Drawable> map = new HashMap<>();
        final PackageManager pm = getPackageManager();
        final List<ApplicationInfo> pkgs = pm.getInstalledApplications(0);
        for (final ApplicationInfo pkg : pkgs) {
            map.put(pkg.packageName.toLowerCase(), pm.getApplicationIcon(pkg));
        }
        return map;
    }

    private void showApps(@NonNull String search) {
        clearAppListView();

        HashMap<String, Drawable> appMap = getAppIconMap();
        ArrayList<String> appList = getReorderedTotalAppList();

        for (final String app : appList) {
            if (!app.toLowerCase().contains(search.trim().toLowerCase())) {
                continue;
            }

//            final Intent intent = pm.getLaunchIntentForPackage(pkg.packageName);
//            if (intent == null) {
//                continue;
//            }

            final CheckBox checkBox = new CheckBox(context);
            checkBox.setLayoutParams(new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT));
            checkBox.setText("");
            checkBox.setChecked(selectedApps.contains(app));
            checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                    if (b) {
                        selectedApps.add(app);
                    } else {
                        selectedApps.remove(app);
                    }
                }
            });

            ImageView img = new ImageView(context);
            try {
                Drawable icon = appMap.get(app);
                img.setImageDrawable(icon);
                img.setLayoutParams(new TableRow.LayoutParams(75, 75));
            } catch (Exception e) {
                e.printStackTrace();
            }

            final Button btn = new Button(context);
            btn.setLayoutParams(new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT));
            btn.setText(app);
            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    checkBox.setChecked(!checkBox.isChecked());
                }
            });

            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            TableLayout table = new TableLayout(context);
            table.setLayoutParams(layoutParams);
            TableRow tr = new TableRow(context);
            tr.setLayoutParams(layoutParams);

            tr.addView(checkBox);
            tr.addView(img);
            tr.addView(btn);
            table.addView(tr);
            appLaunchContainer.addView(table);
        }
    }
}