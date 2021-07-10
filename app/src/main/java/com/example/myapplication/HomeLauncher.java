package com.example.myapplication;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.util.Log;

import androidx.annotation.NonNull;

public class HomeLauncher {

    private Context context;

    public HomeLauncher(@NonNull  Context context) {
        this.context = context;
    }

    public boolean runHomeLauncher(@NonNull String homeLauncher) {
        final PackageManager packageManager = context.getPackageManager();

        for (final ResolveInfo resolveInfo : packageManager.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)) {
            if (resolveInfo.activityInfo.packageName.equals(homeLauncher)) {
                Log.i("Launch App", resolveInfo.activityInfo.packageName);
                Intent intent = new Intent().addCategory(Intent.CATEGORY_HOME).setAction(Intent.ACTION_MAIN).setClassName(resolveInfo.activityInfo.packageName, resolveInfo.activityInfo.name);
                context.startActivity(intent);
                return true;
            }
        }

        return false;
    }
}
